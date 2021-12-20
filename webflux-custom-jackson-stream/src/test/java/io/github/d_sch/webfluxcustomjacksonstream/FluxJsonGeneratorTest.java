package io.github.d_sch.webfluxcustomjacksonstream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;

import io.github.d_sch.webfluxcustomjacksonstream.common.JsonWriter;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class FluxJsonGeneratorTest {


    @Test
    public void test() {
        Flux<DataBuffer> source = Flux.defer(() -> Flux.range(1, 5))
            .as(inFlux -> JsonWriter.asDataBufferFlux(inFlux));
        
        StepVerifier.create(source).assertNext(
            actual -> assertEquals("[1,2,3,4,5]", actual.toString(StandardCharsets.UTF_8))
        ).verifyComplete();
    }
}
