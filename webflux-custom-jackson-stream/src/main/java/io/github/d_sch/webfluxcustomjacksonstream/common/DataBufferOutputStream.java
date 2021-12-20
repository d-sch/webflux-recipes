package io.github.d_sch.webfluxcustomjacksonstream.common;

import java.io.IOException;
import java.io.OutputStream;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;

import io.netty.util.internal.shaded.org.jctools.queues.MessagePassingQueue.Consumer;

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

