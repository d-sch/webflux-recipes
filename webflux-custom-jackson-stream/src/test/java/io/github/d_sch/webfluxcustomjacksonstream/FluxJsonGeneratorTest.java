package io.github.d_sch.webfluxcustomjacksonstream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;

import io.github.d_sch.webfluxcustomjacksonstream.common.JsonWriter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Slf4j
public class FluxJsonGeneratorTest {

    @Test
    public void test() {
        Flux<DataBuffer> source = Flux.defer(() -> Flux.range(1, 5))
            .transform(inFlux -> JsonWriter.asDataBufferFlux(inFlux));
        
        StepVerifier.create(source).assertNext(
            actual -> assertEquals("[1,2,3,4,5]", actual.toString(StandardCharsets.UTF_8))
        ).verifyComplete();
    }

    @Test
    public void test2() {
        Flux<DataBuffer> source = Flux.defer(() -> Flux.never())
            .transform(inFlux -> JsonWriter.asDataBufferFlux(inFlux))
            .timeout(Duration.of(1, ChronoUnit.SECONDS));        

        StepVerifier
            .create(source)
            .expectError(TimeoutException.class)
            .verify();
    }

}
