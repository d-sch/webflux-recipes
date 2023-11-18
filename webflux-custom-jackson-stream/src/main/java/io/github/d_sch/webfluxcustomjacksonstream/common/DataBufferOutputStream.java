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

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;


public class DataBufferOutputStream extends OutputStream {

    private Consumer<DataBuffer> consumer;        
    private DataBufferFactory dataBufferFactory;
    private DataBuffer dataBuffer;
    private int defaultCapacity = 8192;

    public DataBufferOutputStream(Consumer<DataBuffer> consumer) {
        this.consumer = consumer;
        this.dataBufferFactory = DefaultDataBufferFactory.sharedInstance;
        setDataBuffer(defaultCapacity);
    }

    private void setDataBuffer(int capacity) {
        if (dataBuffer != null)
            return;
        this.dataBuffer = dataBufferFactory.allocateBuffer(capacity > defaultCapacity ? capacity : defaultCapacity);
    }

    private void flushBuffer(boolean isClose, int capacity) {
        if (dataBuffer.writePosition() > 0) {
            consumer.accept(dataBuffer);
            dataBuffer = null;
            if (!isClose) {
                setDataBuffer(capacity);
            }
        }
    }

    private void flushBuffer(boolean isClose) {
        flushBuffer(isClose, defaultCapacity);
    }

    @Override
    public void write(int b) throws IOException {
        if (dataBuffer.writePosition() == dataBuffer.capacity()) {
            flushBuffer(false);
        } 
        dataBuffer.write((byte) b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {     
        if (dataBuffer.capacity() - dataBuffer.writePosition() < len) {
            flushBuffer(false, len);
        }
        dataBuffer.write(b, off, len);
    }
    
    @Override
    public void close() throws IOException {
        flushBuffer(true);
    }

}

