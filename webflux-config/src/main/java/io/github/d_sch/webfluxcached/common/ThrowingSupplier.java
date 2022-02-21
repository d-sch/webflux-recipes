/*
 * Copyright 2021 - 2021 d-sch
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

package io.github.d_sch.webfluxcached.common;
import java.util.function.Supplier;

public interface ThrowingSupplier<T> {
    T get() throws Exception;

    static <T> Supplier<T> wrap(ThrowingSupplier<T> consumer) {
        return () -> {
            try {
                return consumer.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}
