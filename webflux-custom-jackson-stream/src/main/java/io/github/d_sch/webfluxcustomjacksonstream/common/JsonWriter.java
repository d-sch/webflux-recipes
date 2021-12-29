/*
 * Copyright 2021 - 2021 d-sch
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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;

import io.netty.util.internal.shaded.org.jctools.queues.MessagePassingQueue.Consumer;
import reactor.core.publisher.Flux;

public class JsonWriter<T> {
    DataBufferOutputStream dataBufferOutputStream;
    JsonGenerator jsonGenerator;

    public JsonWriter(DataBufferFactory dataBufferFactory, JsonFactory jsonFactory, Consumer<DataBuffer> consumer) {
        this.dataBufferOutputStream = new DataBufferOutputStream(consumer);
        this.jsonGenerator = ThrowingSupplier.wrap(() -> jsonFactory.createGenerator(dataBufferOutputStream)).get();
    }

    private void generate(ThrowingConsumer<JsonGenerator> consumer) {
        ThrowingConsumer.wrap(consumer).accept(jsonGenerator);
    }

    public void writeObject(T pojo) {
        generate(jsonGenerator -> jsonGenerator.writeObject(pojo));
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
            inFlux.subscribe(
                x -> {
                    if (isEmpty.get() && isEmpty.compareAndSet(true, false)) {
                        generator.startArray();
                    }
                    generator.writeObject(x);
                }, throwable -> {
                    fluxSink.error(throwable);
                }, () -> {
                    if (isEmpty.get()) {
                        generator.writeEmptyArray();
                    } else {
                        generator.endArray();
                    }
                    fluxSink.complete();
                });
        });
    }
}  

