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

package io.github.d_sch.webfluxcustomjacksonstream.common;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;

import io.github.d_sch.webfluxcommon.common.ThrowingConsumer;
import io.netty.util.internal.shaded.org.jctools.queues.MessagePassingQueue.Consumer;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import tools.jackson.core.JsonEncoding;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.ObjectMapper;

@Slf4j
public class JsonWriter<T> {
    DataBufferOutputStream dataBufferOutputStream;
    JsonGenerator jsonGenerator;

    public JsonWriter(DataBufferFactory dataBufferFactory, JsonFactory jsonFactory, Consumer<DataBuffer> consumer) {
        this.dataBufferOutputStream = new DataBufferOutputStream(consumer);
        final var objectMapper = new ObjectMapper(jsonFactory);
        this.jsonGenerator = objectMapper.createGenerator(dataBufferOutputStream, JsonEncoding.UTF8);
    }

    private void generate(ThrowingConsumer<JsonGenerator> consumer) {
        ThrowingConsumer.wrap(consumer).accept(jsonGenerator);
    }

    public void writeObject(T pojo) {
        generate(jsonGenerator -> jsonGenerator.writePOJO(pojo));
    }

    public void startArray() {
        generate(jsonGenerator -> jsonGenerator.writeStartArray());
    }

    public void endArray() {
        generate(jsonGenerator -> {
            jsonGenerator.writeEndArray();
            jsonGenerator.close();
        });
    }

    public void writeEmptyArray() {
        generate(jsonGenerator -> {            
            jsonGenerator.writeStartArray();
            jsonGenerator.writeEndArray();
            jsonGenerator.close();
        });
    }

    public static <T> Flux<DataBuffer> asDataBufferFlux(Flux<T> inFlux) {
        return Flux.<DataBuffer>create(fluxSink -> {
            var generator = new JsonWriter<>(
                    DefaultDataBufferFactory.sharedInstance, 
                    JsonFactory.builder().build(), 
                    x -> fluxSink.next(x)
                );

            AtomicBoolean isEmpty = new AtomicBoolean(true);
            AtomicBoolean isDone = new AtomicBoolean(false);

            var subscription = inFlux.subscribe(
                x -> {
                    if (!isDone.get() && isEmpty.get() && isEmpty.compareAndSet(true, false)) {
                        generator.startArray();
                    }
                    generator.writeObject(x);
                }, throwable -> {
                    if (!isDone.get()) {
                        isDone.set(true);
                        fluxSink.error(throwable);
                    }
                }, () -> {
                    if (!isDone.get()) {
                        isDone.set(true);
                        if (isEmpty.get()) {
                            generator.writeEmptyArray();
                        } else {
                            generator.endArray();
                        }
                        fluxSink.complete();
                    }
                }
            );
            fluxSink.onCancel(
                () -> {
                    if (!isDone.get()) {
                        log.debug("Cancelled.");
                        isDone.set(true);                  
                        subscription.dispose();                        
                    }
                }
            );
        });
    }
}  

