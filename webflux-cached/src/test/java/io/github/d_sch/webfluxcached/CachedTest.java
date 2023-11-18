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


package io.github.d_sch.webfluxcached;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map.Entry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorResourceFactory;
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
    public void test() {

        Cached<Integer, String> cached  
            = Cached.build(
                reactorResourceFactory, 
                String::valueOf, 
                Integer::parseInt, 
                keyFlux -> keyFlux.transform(
                    flux -> flux.concatMap(
                        key -> Mono.defer(
                            () -> Mono.just(KeyValueHolder.of(key, "Test"))
                        )
                    )
                )
        );


        Flux<Entry<Integer, String>> x = cached.getAll(Flux.defer(() -> Flux.just(1)));   
        Entry<Integer, String> a = x.blockFirst();
        assertNotNull(a);
        assertEquals(1, a.getKey());
        assertEquals("Test", a.getValue());
    }

}
