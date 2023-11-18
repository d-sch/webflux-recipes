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

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;

import io.github.d_sch.webfluxcustomjacksonstream.common.JsonReader;
import io.github.d_sch.webfluxcustomjacksonstream.common.impl.JsonTokenParser;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class FluxJsonParserTest {
    
    DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();

    @Test
    void test() throws JsonParseException, IOException {
        var source = "{\"string\":[\"test\"],\"array\":[1,2,3],\"object\":{\"number\":100}}";
        var parser = JsonFactory.builder().build().createParser(
            source
        );

        var a = new JsonTokenParser(
            parser
        );
        
        JsonNode result = null;
        int n = 0;
        for (JsonToken nextToken = parser.nextToken(); nextToken != null ; nextToken = parser.nextToken()) {
            result = a.parseToken(nextToken);
            n++;
            if (result != null) {
                Assertions.assertEquals(
                    source, result.toString()
                );
            }
            System.out.println(result == null ? "null" : result.toPrettyString());
        }
        Assertions.assertEquals(17, n);
        Assertions.assertNotNull(result);
    }

    @Test
    void test3() throws JsonParseException, IOException {
        var source = "[{\"string\":[\"test\"],\"array\":[1,2,3],\"object\":{\"number\":100}}]";
        var expected = "{\"string\":[\"test\"],\"array\":[1,2,3],\"object\":{\"number\":100}}";
        var parser = JsonFactory.builder().build().createParser(
            source
        );

        var a = new JsonTokenParser(
            parser, 1
        );
        
        JsonNode result = null;
        int n = 0;
        for (JsonToken nextToken = parser.nextToken(); nextToken != null ; nextToken = parser.nextToken()) {
            result = a.parseToken(nextToken);
            n++;
            if (result != null) {
                Assertions.assertEquals(18, n);
                Assertions.assertEquals(
                    expected, result.toString()
                );
            }
            System.out.println(result == null ? "null" : result.toPrettyString());
        }
        Assertions.assertEquals(19, n);
    }

    @Test
    void test3a() throws JsonParseException, IOException {
        var source1 = "{\"string\":\"test\",\"array\":[1,2,3],\"object\":{\"number\":100}}";
        var source2 = "{\"string\":\"test1\",\"array\":[5,6,7],\"object\":{\"number\":101}}";
        var expected = source1;

        var parser = JsonFactory.builder().build().createParser(
            "[" + source1 + "," + source2 + "]"
        );

        var a = new JsonTokenParser(
            parser, 1
        );
        
        
        for (JsonToken nextToken = parser.nextToken(); nextToken != null ; nextToken = parser.nextToken()) {
            var result = a.parseToken(nextToken);
            if (result != null) {
                Assertions.assertEquals(
                    expected, result.toString()
                );
                expected = source2;
            }
            System.out.println(result == null ? "null" : result.toPrettyString());
        }
    }

    @Test
    void test4() throws JsonParseException, IOException {
        var parser = JsonFactory.builder().build().createParser(
            "[{\"string\":[\"test\"],\"array\":[1,2,3],\"object\":{\"number\":100}}]"
        );

        var a = new JsonTokenParser(
            parser, 2
        );
        
        Assertions.assertThrows(
            IllegalStateException.class,
            () -> {
                for (JsonToken nextToken = parser.nextToken(); nextToken != null ; nextToken = parser.nextToken()) {
                    var result = a.parseToken(nextToken);
                    System.out.println(result == null ? "null" : result.toPrettyString());
                }
            });
    }

    @Test
    void test1() {
        var source1 = "{\"string\":\"test\",\"array\":[1,2,3],\"object\":{\"number\":100}}";
        var source2 = "{\"string\":\"test1\",\"array\":[5,6,7],\"object\":{\"number\":101}}";
        var fluxSource = Flux.fromArray(
            new DataBuffer[] {
                dataBufferFactory.wrap(source1.getBytes()),
                dataBufferFactory.wrap(source2.getBytes())
            }
        );

        StepVerifier.create(
            JsonReader.toJsonNodeFlux(
                Flux.defer(() -> fluxSource)
            )
        ).assertNext(
            jsonNode -> Assertions.assertEquals(source1, jsonNode.toString())
        ).assertNext(
            jsonNode -> Assertions.assertEquals(source2, jsonNode.toString())
        ).verifyComplete();
    }

    @Test
    void test2() {
        var source1 = "{\"string\":\"test\",\"array\":[1,2,3],\"object\":{\"number\":100}}";
        var source2 = "{\"string\":\"test1\",\"array\":[5,6,7],\"object\":{\"number\":101}}";
        var expected = "[" + source1 + "," + source2 + "]";
        var fluxSource = Flux.fromArray(
            new DataBuffer[] {
                dataBufferFactory.wrap(("[" + source1 + ",").getBytes()),
                dataBufferFactory.wrap((source2 + "]").getBytes())
            }
        );

        StepVerifier.create(
            JsonReader.toJsonNodeFlux(
                Flux.defer(() -> fluxSource)
            )
        ).assertNext(
            jsonNode -> Assertions.assertEquals(expected, jsonNode.toString())
        ).verifyComplete();
    }

    @Test
    void test5() {
        var source1 = "{\"string\":\"test\",\"array\":[1,2,3],\"object\":{\"number\":100}}";
        var source2 = "{\"string\":\"test1\",\"array\":[5,6,7],\"object\":{\"number\":101}}";
        
        var fluxSource = Flux.fromArray(
            new DataBuffer[] {
                dataBufferFactory.wrap(("[" + source1 + ",").getBytes()),
                dataBufferFactory.wrap((source2 + "]").getBytes())
            }
        );

        StepVerifier.create(
            JsonReader.toJsonNodeFlux(
                Flux.defer(() -> fluxSource),
                0,
                1
            )
        ).assertNext(
            jsonNode -> Assertions.assertEquals(source1, jsonNode.toString())
        ).assertNext(
            jsonNode -> Assertions.assertEquals(source2, jsonNode.toString())
        ).verifyComplete();
    }
}
