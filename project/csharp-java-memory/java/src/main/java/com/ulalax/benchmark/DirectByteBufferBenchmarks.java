package com.ulalax.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * DirectByteBuffer vs MemorySegment Benchmarks
 *
 * Compares the legacy DirectByteBuffer API with the modern FFM MemorySegment API:
 * - ByteBuffer.allocateDirect() allocation and reuse
 * - MemorySegment allocation and reuse
 * - DirectByteBuffer → MemorySegment wrapping (zero-copy interop)
 *
 * DirectByteBuffer is the legacy approach to off-heap memory in Java.
 * MemorySegment (FFM API) provides better safety, performance, and ergonomics.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1)
public class DirectByteBufferBenchmarks {

    @Param({"64", "512", "1024", "4096", "65536", "1048576"})
    private int bufferSize;

    private ByteBuffer reusableDirectBuffer;
    private Arena reusableArena;
    private MemorySegment reusableSegment;

    @Setup
    public void setup() {
        // Pre-allocate buffers for reuse benchmarks
        reusableDirectBuffer = ByteBuffer.allocateDirect(bufferSize);
        reusableArena = Arena.ofConfined();
        reusableSegment = reusableArena.allocate(bufferSize, 8);
    }

    @TearDown
    public void teardown() {
        if (reusableArena != null && reusableArena.scope().isAlive()) {
            reusableArena.close();
        }
    }

    /**
     * Benchmark: DirectByteBuffer allocation
     *
     * Traditional off-heap allocation using ByteBuffer.allocateDirect().
     * Memory is managed outside the Java heap, but cleanup relies on finalization.
     */
    @Benchmark
    public void directByteBufferAllocation(Blackhole bh) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        bh.consume(buffer);
        // Note: No explicit cleanup - relies on finalization/Cleaner
    }

    /**
     * Benchmark: MemorySegment allocation (FFM API)
     *
     * Modern off-heap allocation using Arena and MemorySegment.
     * Provides deterministic cleanup and better performance.
     */
    @Benchmark
    public void memorySegmentAllocation(Blackhole bh) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(bufferSize, 8);
            bh.consume(segment);
        }
    }

    /**
     * Benchmark: Reusable DirectByteBuffer write
     *
     * Writes to a pre-allocated DirectByteBuffer, simulating buffer reuse.
     * Buffer is cleared and reused across invocations.
     */
    @Benchmark
    public void directByteBufferReuse(Blackhole bh) {
        reusableDirectBuffer.clear();
        for (int i = 0; i < Math.min(bufferSize, 1024); i++) {
            reusableDirectBuffer.put((byte) (i & 0xFF));
        }
        reusableDirectBuffer.flip();
        bh.consume(reusableDirectBuffer);
    }

    /**
     * Benchmark: Reusable MemorySegment write
     *
     * Writes to a pre-allocated MemorySegment, simulating buffer reuse.
     * More efficient than DirectByteBuffer due to better JIT optimization.
     */
    @Benchmark
    public void memorySegmentReuse(Blackhole bh) {
        for (long i = 0; i < Math.min(bufferSize, 1024); i++) {
            reusableSegment.set(ValueLayout.JAVA_BYTE, i, (byte) (i & 0xFF));
        }
        bh.consume(reusableSegment);
    }

    /**
     * Benchmark: DirectByteBuffer → MemorySegment wrapping
     *
     * Demonstrates zero-copy interop between DirectByteBuffer and MemorySegment.
     * This allows gradual migration from legacy ByteBuffer code to FFM API.
     */
    @Benchmark
    public void directBufferToMemorySegmentWrap(Blackhole bh) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        MemorySegment segment = MemorySegment.ofBuffer(buffer);
        bh.consume(segment);
    }

    /**
     * Benchmark: DirectByteBuffer with data initialization
     *
     * Allocates DirectByteBuffer and initializes with data.
     * Tests realistic allocation + initialization performance.
     */
    @Benchmark
    public void directByteBufferAllocationWithInit(Blackhole bh) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        for (int i = 0; i < bufferSize; i++) {
            buffer.put((byte) (i & 0xFF));
        }
        buffer.flip();
        bh.consume(buffer);
    }

    /**
     * Benchmark: MemorySegment with data initialization
     *
     * Allocates MemorySegment and initializes with data.
     * Generally faster than DirectByteBuffer due to better intrinsics.
     */
    @Benchmark
    public void memorySegmentAllocationWithInit(Blackhole bh) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(bufferSize, 8);
            for (long i = 0; i < bufferSize; i++) {
                segment.set(ValueLayout.JAVA_BYTE, i, (byte) (i & 0xFF));
            }
            bh.consume(segment);
        }
    }

    /**
     * Benchmark: DirectByteBuffer bulk copy
     *
     * Tests bulk data transfer using DirectByteBuffer.put(byte[]).
     * Measures the cost of copying heap data to off-heap.
     */
    @Benchmark
    public void directByteBufferBulkCopy(Blackhole bh) {
        byte[] heapData = new byte[bufferSize];
        // Initialize heap data
        for (int i = 0; i < bufferSize; i++) {
            heapData[i] = (byte) (i & 0xFF);
        }

        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        buffer.put(heapData);
        buffer.flip();
        bh.consume(buffer);
    }

    /**
     * Benchmark: MemorySegment bulk copy
     *
     * Tests bulk data transfer using MemorySegment.copy().
     * Modern alternative to DirectByteBuffer with better performance.
     */
    @Benchmark
    public void memorySegmentBulkCopy(Blackhole bh) {
        byte[] heapData = new byte[bufferSize];
        // Initialize heap data
        for (int i = 0; i < bufferSize; i++) {
            heapData[i] = (byte) (i & 0xFF);
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(bufferSize, 8);
            MemorySegment.copy(heapData, 0, segment, ValueLayout.JAVA_BYTE, 0, bufferSize);
            bh.consume(segment);
        }
    }
}
