package io.github.d_sch.webfluxcached;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;

import java.util.Map.Entry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import io.github.d_sch.webfluxcached.common.KeyValueHolder;
import io.github.d_sch.webfluxcached.common.cached.Cached;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(SpringExtension.class)
@SpringBootTest()
@ContextConfiguration(classes=CachedTest.TestConfig.class)
public class CachedTest {   

    @TestConfiguration
    public static class TestConfig {

        @Component
        public static class Response {
            public Mono<ServerResponse> hello(ServerRequest request) {
                return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                  .body(BodyInserters.fromValue("Hello"));
            }            
        }

        @Bean
        public RouterFunction<ServerResponse> route(Response greetingHandler) {
      
          return RouterFunctions
            .route(RequestPredicates.GET("/hello").and(RequestPredicates.accept(MediaType.APPLICATION_JSON)), greetingHandler::hello);
        }

    }

    @Autowired
    ReactorResourceFactory reactorResourceFactory;

    @Test
    @DisplayName("getAll with a single key returns the loaded mapping")
    public void getAll_withSingleKey_returnsLoadedMapping() {

        // Arrange: Build a cache with a loader that maps keys to a single "Test" value
        Cached<Integer, String> cached  
            = Cached.build(reactorResourceFactory, String::valueOf, Integer::parseInt, keyFlux -> 
            keyFlux.transform(flux -> flux.<Entry<Integer, String>>concatMap(key -> Mono.defer(() -> Mono.just(KeyValueHolder.of(key, "Test")))) )
        );


        // Act: Request the key
        Flux<Entry<Integer, String>> x = cached.getAll(Flux.defer(() -> Flux.just(1)));   
        StepVerifier.create(x)
            .assertNext(entry -> {
                assertNotNull(entry);
                assertEquals(1, entry.getKey());
                assertEquals("Test", entry.getValue());
            }).verifyComplete();
    }

    @Test
    @DisplayName("getAll with multiple keys returns all loaded mappings")
    public void getAll_withMultipleKeys_returnsAllLoadedMappings() {
        var loopResources = reactorResourceFactory.getLoopResources();
        // Build loader returning entry for each key with "V" + key
        Cached<Integer, String> cached = Cached.build(reactorResourceFactory, String::valueOf, Integer::parseInt, keyFlux ->
            keyFlux.concatMap(k -> Mono.just(KeyValueHolder.of(k, "V" + k)))
        );

        StepVerifier.create(cached.getAll(Flux.just(1, 2, 3)))
            .expectNextMatches(e -> e.getKey() == 1 && "V1".equals(e.getValue()))
            .expectNextMatches(e -> e.getKey() == 2 && "V2".equals(e.getValue()))
            .expectNextMatches(e -> e.getKey() == 3 && "V3".equals(e.getValue()))
            .verifyComplete();
    }

    @Test
    @DisplayName("getAll with a loader returning no entries produces empty result")
    public void getAll_withEmptyLookup_returnsNoEntries() {
        // Arrange: build a cached with a lookup that returns empty for any key
        Cached<Integer, String> cached = Cached.build(reactorResourceFactory, String::valueOf, Integer::parseInt, keyFlux -> Flux.empty());

        // Act & Assert: getAll yields nothing
        StepVerifier.create(cached.getAll(Flux.just(1)))
            .expectNextCount(0).verifyComplete();
    }

    @Test
    @DisplayName("getAll propagates exception from loader")
    public void getAll_whenLookupThrows_propagatesException() {
        // Arrange: loader throws runtime exception
        Cached<Integer, String> cached = Cached.build(reactorResourceFactory, String::valueOf, Integer::parseInt, keyFlux -> keyFlux.flatMap(k -> Mono.error(new IllegalStateException("load fail"))));

        // Act & Assert: the resulting flux should propagate the error
        StepVerifier.create(cached.getAll(Flux.just(1)))
            .expectError(IllegalStateException.class).verify();
    }

    @Test
    @DisplayName("Building Cached with a null reactorResourceFactory throws NullPointerException")
    public void build_withNullReactorResourceFactory_throws() {
        assertThrows(NullPointerException.class, () -> Cached.build(null, String::valueOf, Integer::parseInt, k -> Flux.empty()));
    }

    @Test
    @DisplayName("Building Cached with a null toCacheKey function throws NullPointerException")
    public void build_withNullToCacheKey_throws() {
        // Assert: building doesn't throw immediately - but usage via getAll does
        Cached<Integer, String> c = Cached.build(reactorResourceFactory, null, Integer::parseInt, k -> Flux.empty());
        assertThrows(NullPointerException.class, () -> c.getAll(Flux.just(1)));
    }

    @Test
    @DisplayName("Building Cached with a null fromCacheKey function throws NullPointerException")
    public void build_withNullFromCacheKey_throws() {
        // Assert: a usage scenario that produces entries will call fromCacheKey and thus throw NPE
        Cached<Integer, String> c = Cached.build(reactorResourceFactory, String::valueOf, null, k -> k.map(i -> KeyValueHolder.of(i, "V"+i)));
        StepVerifier.create(c.getAll(Flux.just(1)))
            .expectError(NullPointerException.class).verify();
    }

}
