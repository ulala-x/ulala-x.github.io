package com.ulalax.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Copy Overhead Benchmarks - The Core Java Limitation
 *
 * This benchmark demonstrates Java's fundamental constraint:
 * **Java cannot pin heap arrays (byte[]) for native access.**
 *
 * Unlike C# which can use fixed/pinning, Java MUST copy data from heap to native memory
 * before passing to native code. This mandatory copy overhead is a key architectural difference.
 *
 * Comparison points:
 * - byte[] → MemorySegment copy (MemorySegment.copy)
 * - byte[] → DirectByteBuffer copy (ByteBuffer.put)
 * - DirectBuffer → MemorySegment wrapping (zero-copy possible)
 * - Native-only operations (baseline with no copy overhead)
 *
 * Key Insight: DirectByteBuffer and MemorySegment enable zero-copy native interop,
 * but require upfront allocation. Heap arrays are convenient but always incur copy costs.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1)
public class CopyOverheadBenchmarks {

    @Param({"64", "512", "1024", "4096", "65536", "1048576"})
    private int bufferSize;

    private byte[] heapArray;
    private ByteBuffer directBuffer;
    private MemorySegment nativeSegment;
    private Arena arena;

    @Setup
    public void setup() {
        // Prepare heap array with test data
        heapArray = new byte[bufferSize];
        for (int i = 0; i < bufferSize; i++) {
            heapArray[i] = (byte) (i & 0xFF);
        }

        // Pre-allocate off-heap buffers for reuse
        directBuffer = ByteBuffer.allocateDirect(bufferSize);
        arena = Arena.ofConfined();
        nativeSegment = arena.allocate(bufferSize, 8);
    }

    @TearDown
    public void teardown() {
        if (arena != null && arena.scope().isAlive()) {
            arena.close();
        }
    }

    /**
     * Benchmark: Heap byte[] → MemorySegment copy
     *
     * MANDATORY COPY: Java cannot pin heap arrays for native access.
     * Data must be copied from heap (byte[]) to native memory (MemorySegment).
     *
     * This is the fundamental cost that C# can sometimes avoid with pinning.
     */
    @Benchmark
    public void heapToMemorySegmentCopy(Blackhole bh) {
        try (Arena localArena = Arena.ofConfined()) {
            MemorySegment segment = localArena.allocate(bufferSize, 8);
            // COPY OPERATION - unavoidable overhead
            MemorySegment.copy(heapArray, 0, segment, ValueLayout.JAVA_BYTE, 0, bufferSize);
            bh.consume(segment);
        }
    }

    /**
     * Benchmark: Heap byte[] → DirectByteBuffer copy
     *
     * Legacy approach to copying heap data to off-heap.
     * Similar mandatory copy overhead as MemorySegment approach.
     */
    @Benchmark
    public void heapToDirectBufferCopy(Blackhole bh) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        // COPY OPERATION - unavoidable overhead
        buffer.put(heapArray);
        buffer.flip();
        bh.consume(buffer);
    }

    /**
     * Benchmark: Reusable DirectByteBuffer copy
     *
     * Copies heap data to pre-allocated DirectByteBuffer.
     * Amortizes allocation cost but copy overhead remains.
     */
    @Benchmark
    public void heapToReusableDirectBufferCopy(Blackhole bh) {
        directBuffer.clear();
        // COPY OPERATION - still required even with reuse
        directBuffer.put(heapArray);
        directBuffer.flip();
        bh.consume(directBuffer);
    }

    /**
     * Benchmark: Reusable MemorySegment copy
     *
     * Copies heap data to pre-allocated MemorySegment.
     * Best-case for heap→native transfer, but copy still required.
     */
    @Benchmark
    public void heapToReusableMemorySegmentCopy(Blackhole bh) {
        // COPY OPERATION - mandatory even with pre-allocated segment
        MemorySegment.copy(heapArray, 0, nativeSegment, ValueLayout.JAVA_BYTE, 0, bufferSize);
        bh.consume(nativeSegment);
    }

    /**
     * Benchmark: DirectByteBuffer → MemorySegment wrap (ZERO-COPY)
     *
     * Zero-copy wrapping of DirectByteBuffer as MemorySegment.
     * No data copy - just creates a view over the same memory.
     *
     * This demonstrates that once data is off-heap, it can be used efficiently.
     * The challenge is getting data off-heap in the first place.
     */
    @Benchmark
    public void directBufferToMemorySegmentWrap(Blackhole bh) {
        // ZERO-COPY WRAP - no data movement
        MemorySegment segment = MemorySegment.ofBuffer(directBuffer);
        bh.consume(segment);
    }

    /**
     * Benchmark: Native-only operation (baseline)
     *
     * Pure native memory operation with no heap involvement.
     * This represents the theoretical best case - no copy overhead.
     *
     * Compare this with heap→native benchmarks to quantify copy cost.
     */
    @Benchmark
    public void nativeOnlyBaseline(Blackhole bh) {
        try (Arena localArena = Arena.ofConfined()) {
            MemorySegment segment = localArena.allocate(bufferSize, 8);
            // Write directly to native memory - no heap copy
            for (long i = 0; i < Math.min(bufferSize, 1024); i++) {
                segment.set(ValueLayout.JAVA_BYTE, i, (byte) (i & 0xFF));
            }
            bh.consume(segment);
        }
    }

    /**
     * Benchmark: Copy + immediate native read
     *
     * Simulates a common pattern: copy heap data to native, then process it.
     * Measures total cost of copy + native access.
     */
    @Benchmark
    public void heapToNativeCopyAndRead(Blackhole bh) {
        try (Arena localArena = Arena.ofConfined()) {
            MemorySegment segment = localArena.allocate(bufferSize, 8);
            // COPY PHASE
            MemorySegment.copy(heapArray, 0, segment, ValueLayout.JAVA_BYTE, 0, bufferSize);

            // PROCESS PHASE - read some values from native memory
            long sum = 0;
            for (long i = 0; i < Math.min(bufferSize, 100); i += 10) {
                sum += segment.get(ValueLayout.JAVA_BYTE, i);
            }
            bh.consume(sum);
        }
    }

    /**
     * Benchmark: Partial copy from heap to native
     *
     * Tests copy overhead for partial buffer copies.
     * Useful for understanding copy cost scaling.
     */
    @Benchmark
    public void partialHeapToNativeCopy(Blackhole bh) {
        try (Arena localArena = Arena.ofConfined()) {
            MemorySegment segment = localArena.allocate(bufferSize, 8);
            int copySize = Math.min(bufferSize / 2, bufferSize);
            // PARTIAL COPY - still has overhead
            MemorySegment.copy(heapArray, 0, segment, ValueLayout.JAVA_BYTE, 0, copySize);
            bh.consume(segment);
        }
    }

    /**
     * Benchmark: MemorySegment.copy() for native-to-native
     *
     * Tests copy performance between two native segments.
     * This is zero-copy from Java perspective but still requires memory copy.
     * Much faster than memcpy-like operations in older APIs.
     */
    @Benchmark
    public void nativeToNativeCopy(Blackhole bh) {
        try (Arena localArena = Arena.ofConfined()) {
            MemorySegment source = localArena.allocate(bufferSize, 8);
            MemorySegment dest = localArena.allocate(bufferSize, 8);

            // Initialize source
            for (long i = 0; i < Math.min(bufferSize, 100); i++) {
                source.set(ValueLayout.JAVA_BYTE, i, (byte) (i & 0xFF));
            }

            // Native-to-native copy
            MemorySegment.copy(source, 0, dest, 0, bufferSize);
            bh.consume(dest);
        }
    }
}
