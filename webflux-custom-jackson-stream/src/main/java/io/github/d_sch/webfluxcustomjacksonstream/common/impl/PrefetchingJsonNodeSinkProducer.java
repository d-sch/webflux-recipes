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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.Subscription;
import org.springframework.core.io.buffer.DataBuffer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.json.async.NonBlockingByteBufferJsonParser;
import com.fasterxml.jackson.databind.JsonNode;

import io.github.d_sch.webfluxcommon.common.ThrowingLongConsumer;
import io.github.d_sch.webfluxcommon.common.ThrowingRunnable;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.FluxSink;

@Slf4j 
public class PrefetchingJsonNodeSinkProducer extends BaseSubscriber<DataBuffer> {
    private FluxSink<JsonNode> sink;
    private NonBlockingByteBufferJsonParser jsonParser;
    private JsonTokenParser jsonTokenParser;
    private boolean completed;
    private AtomicLong requested = new AtomicLong();
    private AtomicBoolean WIP = new AtomicBoolean();
    Deque<DataBuffer> buffer = new ArrayDeque<>();
    private static final int PREFETCH = 32;
    int prefetch;
    int consumed;

    public PrefetchingJsonNodeSinkProducer(FluxSink<JsonNode> sink, int prefetch) {
        this.sink = sink;
        this.prefetch = prefetch > 0 ? prefetch : PREFETCH;
        var jsonFactory = JsonFactory.builder().build();
        try {
            jsonParser = (NonBlockingByteBufferJsonParser) jsonFactory.createNonBlockingByteBufferParser();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        jsonTokenParser = new JsonTokenParser(jsonParser);
        sink.onCancel(this);
        sink.onDispose(this);
        sink.onRequest(ThrowingLongConsumer.wrap(this::onSinkRequest));
    }

    private void onSinkRequest(long n) throws IOException {
        requested.addAndGet(n);
        doRequest();
    }

    @Override
    protected void hookOnSubscribe(Subscription subscription) {
        request(this.prefetch);    
    }

    @Override
    protected void hookOnError(Throwable throwable) {
        sink.error(throwable);
    }

    @Override
    protected void hookOnNext(DataBuffer value) {
        buffer.offer(value);
        if (buffer.size() > this.prefetch) {
            log.warn("Prefetch limit exceeded.");
        }
        ThrowingRunnable.wrap(this::doRequest).run();
    }

    @Override
    protected void hookOnComplete() {
        this.completed = true;
        ThrowingRunnable.wrap(this::doRequest).run();
    }

    private void doRequest() throws IOException {
        if (requested.get() > 0 && WIP.compareAndSet(false, true)) {
            var done = false;
            do {
                var nextToken = jsonParser.nextToken();
                switch (nextToken) {
                    case NOT_AVAILABLE -> {
                        var nextDataBuffer = buffer.poll();
                        if (nextDataBuffer != null) {
                            jsonParser.feedInput(
                                nextDataBuffer.toByteBuffer()
                            );
                            var consumed = this.consumed + 1;
                            if (consumed == prefetch) {
                                log.debug("Request prefetch.");
                                request(prefetch);
                                this.consumed = 0;
                            } else {
                                this.consumed = consumed;
                            }
                        } else {
                            done = true;
                        }
                        if (done && completed) {
                            log.debug("Completed.");
                            sink.complete();
                        }
                    }
                    default -> {
                        var nextNode = jsonTokenParser.parseToken(nextToken);
                        if (nextNode != null) {
                            sink.next(nextNode);
                            done = requested.getAndDecrement() > 0;
                        }
                    }
                }
            } while (!done);
            WIP.set(false);
        }
    }
}