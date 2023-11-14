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


package io.github.d_sch.webfluxcustomjacksonstream.common;

import org.springframework.core.io.buffer.DataBuffer;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.d_sch.webfluxcustomjacksonstream.common.impl.NonPrefetchingJsonNodeSinkProducer;
import io.github.d_sch.webfluxcustomjacksonstream.common.impl.PrefetchingJsonNodeSinkProducer;
import reactor.core.publisher.Flux;

public class JsonReader {

    /**
     * Reactive Json parser returning root Object/Array nodes as stream of
     * JsonNodes.
     * 
     * @param inFlux
     * @return
     */
    public static Flux<JsonNode> toJsonNodeFlux(Flux<DataBuffer> inFlux) {
        return toJsonNodeFlux(inFlux, 0, 0);
    }

    /**
     * Reactive Json parser returning root Object/Array nodes as stream of
     * JsonNodes.
     * 
     * @param inFlux
     * @parem prefetch
     * @return
     */
    public static Flux<JsonNode> toJsonNodeFlux(Flux<DataBuffer> inFlux, int prefetch) {
        return toJsonNodeFlux(inFlux, prefetch, 0);
    }

    /**
     * Reactive Json parser returning root Object/Array nodes as stream of
     * JsonNodes.
     * 
     * @param inFlux
     * @param prefetch
     * @param ignoreLevel
     * @return
     */
    public static Flux<JsonNode> toJsonNodeFlux(Flux<DataBuffer> inFlux, int prefetch, int ignoreLevel) {
        if (prefetch == 0) {
            return Flux.create(
                jsonNodeSink -> {
                    inFlux.subscribe(new NonPrefetchingJsonNodeSinkProducer(jsonNodeSink, ignoreLevel));
                });
        } else {
            return Flux.create(
                jsonNodeSink -> {
                    inFlux.subscribe(new PrefetchingJsonNodeSinkProducer(jsonNodeSink, prefetch));
                });
        }
    }
}
