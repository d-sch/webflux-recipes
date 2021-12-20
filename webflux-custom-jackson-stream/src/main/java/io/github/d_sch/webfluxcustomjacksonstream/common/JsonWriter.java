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

