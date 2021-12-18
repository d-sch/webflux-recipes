import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonGenerator;

import org.springframework.core.io.buffer.DataBufferFactory;

import io.github.d_sch.webfluxcustomjacksonstream.common.OutputStreamFlux;
import reactor.core.publisher.Flux;

public class FluxJsonGenerator {
    
    OutputStreamFlux outputStreamFlux;
    JsonGenerator jsonGenerator;

    public FluxJsonGenerator(DataBufferFactory dataBufferFactory) {
        this(dataBufferFactory, JsonFactory.builder().build());
    }

    public FluxJsonGenerator(DataBufferFactory dataBufferFactory, JsonFactory jsonFactory) {
        this.outputStreamFlux = new OutputStreamFlux(dataBufferFactory);
        this.jsonGenerator = new JsonFactory(jsonFactory).createGenerator(out);
    }

    public void generate(ThrowableConsumer consumer) {
        ThrowableConsumer.wrap(consumer).accept(jsonGenerator);
    }

    public <T> Flux<DataBuffer> streamArray(Flux<T> stream) {
        return stream.switchOnFirst(signal, flux -> {
            switch (signal) {
                case value:
                    
                    break;
            
                default:
                    break;
            }
        });
    }

    private streamArray(Flux<T> stream) {
        stream  
    }

    private emptyArray() throws Exception {
        jsonGenerator.writeStartArray();
        jsonGenerator.writeEndArray();
        jsonGenerator.close();
    }

    private error(Throwable throwable) {
        outputStreamFlux.error(throwable);
    }
}
