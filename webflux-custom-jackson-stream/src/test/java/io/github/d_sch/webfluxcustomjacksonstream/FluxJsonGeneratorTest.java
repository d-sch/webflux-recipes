package io.github.d_sch.webfluxcustomjacksonstream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;

import io.github.d_sch.webfluxcustomjacksonstream.common.JsonWriter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@Slf4j
public class FluxJsonGeneratorTest {

    // A tiny POJO used to produce larger JSON payloads when needed in tests
    private static class LargePojo {
        public final String data;
        public LargePojo(String data) { this.data = data; }
    }

    // A small test POJO used when serializing object arrays
    private static class Person {
        public final String name;
        public final int age;
        public Person(String name, int age) { this.name = name; this.age = age; }
    }

    @Test
    @DisplayName("Should convert a sequence of simple values into a single JSON array")
    public void shouldReturnJsonArrayForElements() {
        // Arrange: create a simple Flux of integers
        Flux<DataBuffer> source = Flux.defer(() -> Flux.range(1, 5))
            .transform(inFlux -> JsonWriter.asDataBufferFlux(inFlux));

        // Assert: the emitted DataBuffer contains the full JSON array
        StepVerifier.create(source)
            .assertNext(actual -> assertEquals("[1,2,3,4,5]", actual.toString(StandardCharsets.UTF_8)))
            .verifyComplete();
    }

    @Test
    @DisplayName("Should signal error when upstream never completes and a timeout is applied")
    public void shouldTimeoutOnNeverFlux() {
        // Arrange & Act: convert a never-completing flux to JSON and apply a short timeout
        Flux<DataBuffer> source = Flux.defer(() -> Flux.never())
            .transform(inFlux -> JsonWriter.asDataBufferFlux(inFlux))
            .timeout(Duration.of(1, ChronoUnit.SECONDS));

        // Assert: the resulting stream times out
        StepVerifier.create(source)
            .expectError(TimeoutException.class)
            .verify();
    }

    @Test
    @DisplayName("Should return JSON empty array for an empty source flux")
    public void shouldReturnEmptyArrayForNoElements() {
        // Arrange: empty source
        Flux<DataBuffer> source = Flux.<Integer>empty()
            .transform(inFlux -> JsonWriter.asDataBufferFlux(inFlux));

        // Assert: expect a single DataBuffer with value []
        StepVerifier.create(source)
            .assertNext(actual -> assertEquals("[]", actual.toString(StandardCharsets.UTF_8)))
            .verifyComplete();
    }

    @Test
    @DisplayName("Should propagate upstream errors")
    public void shouldPropagateErrorFromSource() {
        // Arrange: an upstream that immediately errors
        RuntimeException error = new RuntimeException("Failed!");
        Flux<DataBuffer> source = Flux.<Integer>error(error)
            .transform(inFlux -> JsonWriter.asDataBufferFlux(inFlux));

        // Assert: error should be forwarded by the JsonWriter
        StepVerifier.create(source)
            .expectErrorMatches(t -> t instanceof RuntimeException && t.getMessage().equals("Failed!"))
            .verify();
    }

    @Test
    @DisplayName("Should split large JSON into multiple DataBuffers")
    public void shouldEmitMultipleDataBuffersWhenLargePayload() {
        // Arrange: craft a large payload exceeding DataBuffer default capacity (8192)
        var largeString = "a".repeat(9000);
        var largePojo = new LargePojo(largeString);
        Flux<DataBuffer> source = Flux.just(largePojo).
            transform(inFlux -> JsonWriter.asDataBufferFlux(inFlux));

        // Act & Assert: collect all DataBuffers and validate we received more than one chunk
        StepVerifier.create(source.collectList())
            .assertNext(list -> {
                // We expect the stream to be split across multiple buffers
                assertTrue(list.size() > 1, "Large payload should produce multiple DataBuffers");

                // Validate concatenated bytes from all buffers match the expected JSON array
                StringBuilder content = new StringBuilder();
                list.forEach(buf -> content.append(buf.toString(StandardCharsets.UTF_8)));
                assertEquals("[{\"data\":\"" + largeString + "\"}]", content.toString());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should serialize multiple objects into JSON array")
    public void shouldSerializeMultipleObjects() {
        // Arrange: create multiple small POJOs
        var p1 = new Person("Alice", 30);
        var p2 = new Person("Bob", 25);
        var p3 = new Person("Charlie", 40);

        Flux<DataBuffer> source = Flux.just(p1, p2, p3)
            .transform(inFlux -> JsonWriter.asDataBufferFlux(inFlux));

        // Assert: the output JSON should contain all objects as an array
        StepVerifier.create(source)
            .assertNext(actual -> assertEquals("[{\"name\":\"Alice\",\"age\":30},{\"name\":\"Bob\",\"age\":25},{\"name\":\"Charlie\",\"age\":40}]", actual.toString(StandardCharsets.UTF_8)))
            .verifyComplete();
    }

    @Test
    @DisplayName("Should stop emitting when downstream cancels; closing bracket must not be emitted")
    public void shouldNotEmitClosingBracketOnCancel() {
        // Arrange: a source that emits two objects and then never completes
        var largeString = "a".repeat(9000);
        var largePojo = new LargePojo(largeString);
        Flux<DataBuffer> source = Flux.defer(() -> Flux.concat(Flux.just(largePojo, Flux.never())))
            .limitRate(1)
            .transform(inFlux -> JsonWriter.asDataBufferFlux(inFlux)).take(Duration.of(1, ChronoUnit.SECONDS));

        // Act & Assert: request both events then cancel; ensure no closing bracket was emitted
        StepVerifier.create(source)
            .assertNext(actual -> assertFalse(actual.toString(StandardCharsets.UTF_8).endsWith("]")))
            .thenCancel()
            .verify();
    }

}
