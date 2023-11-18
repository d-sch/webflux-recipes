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


package io.github.d_sch.webfluxcustomjacksonstream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;

import io.github.d_sch.webfluxcustomjacksonstream.common.JsonWriter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@Slf4j
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
