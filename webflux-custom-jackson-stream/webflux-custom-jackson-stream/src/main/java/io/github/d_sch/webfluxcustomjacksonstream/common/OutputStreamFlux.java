package io.github.d_sch.webfluxcustomjacksonstream.common;

import java.io.IOException;
import java.io.OutputStream;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;

import lombok.Getter;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitFailureHandler;


public class OutputStreamFlux extends OutputStream {

    public static final int DEFAULT_BUFFER_SIZE = 8192;

    Sinks.Many<DataBuffer> streamSink = Sinks.many().multicast().onBackpressureBuffer(); 

    @Getter
    private int bufferSize = 8192;
    private DataBuffer dataBuffer;
    private DataBufferFactory dataBufferFactory;

    public OutputStreamFlux(DataBufferFactory dataBufferFactory) {
        this(dataBufferFactory, DEFAULT_BUFFER_SIZE);
    }

    public OutputStreamFlux(DataBufferFactory dataBufferFactory, int bufferSize) {
        this.dataBufferFactory = dataBufferFactory;
        this.bufferSize = bufferSize;
    }   

    private void setDataBuffer() {
        this.dataBuffer = dataBufferFactory.allocateBuffer(bufferSize);
    }

    private void flushBuffer() {
        if (dataBuffer.writePosition() > 0) {
            setDataBuffer();
        }
        streamSink.emitNext(dataBuffer, EmitFailureHandler.FAIL_FAST);
    }

    @Override
    public void write(int b) throws IOException {
        if (dataBuffer.writePosition() >= dataBuffer.capacity()) {
            flushBuffer();
        }
        dataBuffer.write((byte) b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (dataBuffer.capacity() - dataBuffer.writePosition() >= len) {
            dataBuffer.write(b, off, len);
        } else {
            
        }
    }
    
    @Override
    public void close() throws IOException {
        flushBuffer();
        streamSink.emitComplete(EmitFailureHandler.FAIL_FAST);
    }

    public void error(Throwable throwable) {
        streamSink.emitError(throwable, EmitFailureHandler.FAIL_FAST);
    }
}
