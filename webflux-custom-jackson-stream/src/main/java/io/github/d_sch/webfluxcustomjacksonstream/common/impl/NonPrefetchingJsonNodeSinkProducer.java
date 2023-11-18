/*
 * Copyright 2021 - 2023 d-sch
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and,
 * limitations under the License.
 */


package io.github.d_sch.webfluxcustomjacksonstream.common.impl;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.Subscription;
import org.springframework.core.io.buffer.DataBuffer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.json.async.NonBlockingByteBufferJsonParser;
import com.fasterxml.jackson.databind.JsonNode;

import io.github.d_sch.webfluxcustomjacksonstream.common.ThrowingLongConsumer;
import io.github.d_sch.webfluxcustomjacksonstream.common.ThrowingRunnable;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.FluxSink;

@Slf4j
public class NonPrefetchingJsonNodeSinkProducer extends BaseSubscriber<DataBuffer> {
    private FluxSink<JsonNode> sink;
    private NonBlockingByteBufferJsonParser jsonParser;
    private JsonTokenParser jsonTokenParser;
    private AtomicLong requested = new AtomicLong();
    private AtomicInteger WIP = new AtomicInteger();
    boolean completed;

    public NonPrefetchingJsonNodeSinkProducer(
            FluxSink<JsonNode> inFlux, int ignoreLevel) {
        this.sink = inFlux;

        var jsonFactory = JsonFactory.builder().build();
        try {
            jsonParser = (NonBlockingByteBufferJsonParser) jsonFactory.createNonBlockingByteBufferParser();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        jsonTokenParser = new JsonTokenParser(jsonParser, ignoreLevel);
        inFlux.onCancel(this);
        inFlux.onDispose(this);
        inFlux.onRequest(ThrowingLongConsumer.wrap(this::onSinkRequest));
    }

    @Override
    protected void hookOnSubscribe(Subscription subscription) {
        subscription.request(1);
    }

    @Override
    protected void hookOnNext(DataBuffer value) {

        ThrowingRunnable.wrap(
                () -> {
                    WIP.getAndIncrement();
                    jsonParser.feedInput(value.toByteBuffer());
                    this.doRequest();
                }).run();
    }

    @Override
    protected void hookOnComplete() {
        this.completed = true;
        ThrowingRunnable.wrap(
                () -> {
                    WIP.getAndIncrement();
                    this.doRequest();
                }).run();
    }

    @Override
    protected void hookOnError(Throwable throwable) {
        sink.error(throwable);
    }

    private void onSinkRequest(long n) throws IOException {
        requested.addAndGet(n);
        doRequest();
    }

    private void doRequest() throws IOException {
        if (this.upstream() != null && WIP.get() == 1) {
            var done = completed; // done processing
            var request = false;
            do {
                if (!done) {
                    var nextToken = jsonParser.nextToken();
                    switch (nextToken) {
                        case NOT_AVAILABLE -> {
                            if (!this.completed) {
                                log.debug("Request next.");
                                if (WIP.decrementAndGet() == 0) {
                                    request(1);
                                    request = true;
                                }
                            } else {
                                done = true; // Publisher completed. No more data available.
                            }
                        }
                        default -> {
                            var nextNode = jsonTokenParser.parseToken(nextToken);
                            if (nextNode != null) {
                                sink.next(nextNode);
                                done = requested.getAndDecrement() > 0; // Published requested elements
                            }
                        }
                    }
                }
                if (done && this.completed) {
                    // Published all requested elements or no more data available.
                    log.debug("Completed.");
                    sink.complete();
                    done = WIP.decrementAndGet() == 0;
                }
                done = WIP.get() == 0;
            } while (!request && !done); // No outstanding request and not all outstanding requests fullfilled
        }
    }

}