package com.ulalax.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Data Transfer Benchmarks - Real-World Scenarios
 *
 * Simulates realistic data transfer patterns to compare with C# benchmarks:
 * - Heap Array → Native (direct copy)
 * - DirectBuffer → Native (zero-copy wrap)
 * - Full pipeline: Heap → Direct → Native (staged transfer)
 * - Pre-allocated DirectBuffer usage patterns
 *
 * These benchmarks mirror C# scenarios to enable direct performance comparison
 * and highlight architectural differences between the two platforms.
 *
 * Key comparisons:
 * - Java byte[] vs C# byte[] (both require copy/pin)
 * - Java DirectByteBuffer vs C# stackalloc/NativeMemory
 * - Java MemorySegment vs C# Span<byte>/Memory<byte>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1)
public class DataTransferBenchmarks {

    @Param({"64", "512", "1024", "4096", "65536", "1048576"})
    private int bufferSize;

    private byte[] sourceHeapArray;
    private ByteBuffer preallocatedDirectBuffer;
    private MemorySegment preallocatedNativeSegment;
    private Arena reusableArena;

    @Setup
    public void setup() {
        // Initialize source heap array with test data
        sourceHeapArray = new byte[bufferSize];
        for (int i = 0; i < bufferSize; i++) {
            sourceHeapArray[i] = (byte) (i & 0xFF);
        }

        // Pre-allocate reusable off-heap buffers
        preallocatedDirectBuffer = ByteBuffer.allocateDirect(bufferSize);
        reusableArena = Arena.ofConfined();
        preallocatedNativeSegment = reusableArena.allocate(bufferSize, 8);
    }

    @TearDown
    public void teardown() {
        if (reusableArena != null && reusableArena.scope().isAlive()) {
            reusableArena.close();
        }
    }

    /**
     * Scenario 1: Heap Array → Native (Direct Transfer)
     *
     * Simulates: Receiving data in heap array, copying to native for processing.
     *
     * Equivalent to C#:
     *   byte[] data = GetData();
     *   fixed (byte* ptr = data) { ProcessNative(ptr); }
     *
     * Java requires explicit copy; C# can pin but often copies too for safety.
     */
    @Benchmark
    public void heapArrayToNativeDirect(Blackhole bh) {
        try (Arena arena = Arena.ofConfined()) {
            // Allocate native segment
            MemorySegment nativeSegment = arena.allocate(bufferSize, 8);

            // MANDATORY COPY: heap → native
            MemorySegment.copy(sourceHeapArray, 0, nativeSegment, ValueLayout.JAVA_BYTE, 0, bufferSize);

            // Simulate native processing
            long checksum = 0;
            for (long i = 0; i < Math.min(bufferSize, 100); i += 10) {
                checksum += nativeSegment.get(ValueLayout.JAVA_BYTE, i);
            }

            bh.consume(checksum);
        }
    }

    /**
     * Scenario 2: DirectBuffer → Native (Zero-Copy Wrap)
     *
     * Simulates: Using pre-allocated DirectBuffer, wrapping for FFM API usage.
     *
     * Equivalent to C#:
     *   NativeMemory.Alloc(...);
     *   Span<byte> span = new Span<byte>(ptr, size);
     *
     * Best case for Java: zero-copy interop between DirectBuffer and MemorySegment.
     */
    @Benchmark
    public void directBufferToNativeWrap(Blackhole bh) {
        // Clear DirectBuffer for reuse
        preallocatedDirectBuffer.clear();

        // Populate DirectBuffer (simulates receiving data directly in off-heap buffer)
        for (int i = 0; i < Math.min(bufferSize, 1024); i++) {
            preallocatedDirectBuffer.put((byte) (i & 0xFF));
        }
        preallocatedDirectBuffer.flip();

        // ZERO-COPY WRAP: DirectBuffer → MemorySegment
        MemorySegment segment = MemorySegment.ofBuffer(preallocatedDirectBuffer);

        // Simulate native processing
        long checksum = 0;
        for (long i = 0; i < Math.min(bufferSize, 100); i += 10) {
            checksum += segment.get(ValueLayout.JAVA_BYTE, i);
        }

        bh.consume(checksum);
    }

    /**
     * Scenario 3: Full Pipeline (Heap → Direct → Native)
     *
     * Simulates: Multi-stage data transfer through different memory regions.
     * Common in network/IO scenarios where data arrives in heap buffers.
     *
     * Pipeline:
     * 1. Heap array (data source)
     * 2. DirectByteBuffer (intermediate buffer)
     * 3. MemorySegment (native API consumption)
     */
    @Benchmark
    public void fullPipelineHeapToDirectToNative(Blackhole bh) {
        // Stage 1: Heap array → DirectByteBuffer
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(bufferSize);
        directBuffer.put(sourceHeapArray);
        directBuffer.flip();

        // Stage 2: DirectByteBuffer → MemorySegment (zero-copy wrap)
        MemorySegment segment = MemorySegment.ofBuffer(directBuffer);

        // Stage 3: Process via MemorySegment
        long checksum = 0;
        for (long i = 0; i < Math.min(bufferSize, 100); i += 10) {
            checksum += segment.get(ValueLayout.JAVA_BYTE, i);
        }

        bh.consume(checksum);
    }

    /**
     * Scenario 4: Pre-allocated DirectBuffer Reuse
     *
     * Simulates: Long-lived DirectBuffer pool, amortizing allocation costs.
     * Realistic pattern for high-throughput applications.
     *
     * Equivalent to C# buffer pooling with ArrayPool<byte> or MemoryPool<byte>.
     */
    @Benchmark
    public void preallocatedDirectBufferReuse(Blackhole bh) {
        // Reuse pre-allocated buffer
        preallocatedDirectBuffer.clear();

        // Copy heap data to DirectBuffer
        preallocatedDirectBuffer.put(sourceHeapArray);
        preallocatedDirectBuffer.flip();

        // Wrap as MemorySegment for native processing
        MemorySegment segment = MemorySegment.ofBuffer(preallocatedDirectBuffer);

        // Process
        long checksum = 0;
        for (long i = 0; i < Math.min(bufferSize, 100); i += 10) {
            checksum += segment.get(ValueLayout.JAVA_BYTE, i);
        }

        bh.consume(checksum);
    }

    /**
     * Scenario 5: Direct Native Allocation and Processing
     *
     * Simulates: Pure native workflow with no heap involvement.
     * Best-case baseline for performance comparison.
     *
     * Equivalent to C#:
     *   byte* ptr = (byte*)NativeMemory.Alloc(size);
     *   // Work directly with ptr
     */
    @Benchmark
    public void directNativeAllocationAndProcessing(Blackhole bh) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(bufferSize, 8);

            // Initialize native memory directly
            for (long i = 0; i < Math.min(bufferSize, 1024); i++) {
                segment.set(ValueLayout.JAVA_BYTE, i, (byte) (i & 0xFF));
            }

            // Process
            long checksum = 0;
            for (long i = 0; i < Math.min(bufferSize, 100); i += 10) {
                checksum += segment.get(ValueLayout.JAVA_BYTE, i);
            }

            bh.consume(checksum);
        }
    }

    /**
     * Scenario 6: Bidirectional Transfer (Native → Heap)
     *
     * Simulates: Processing in native memory, copying results back to heap.
     * Common in JNI/FFM scenarios where results must be returned to Java.
     */
    @Benchmark
    public void nativeToHeapTransfer(Blackhole bh) {
        try (Arena arena = Arena.ofConfined()) {
            // Allocate and populate native memory
            MemorySegment segment = arena.allocate(bufferSize, 8);
            for (long i = 0; i < bufferSize; i++) {
                segment.set(ValueLayout.JAVA_BYTE, i, (byte) (i & 0xFF));
            }

            // Copy native → heap (return results to Java)
            byte[] result = new byte[bufferSize];
            MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, 0, result, 0, bufferSize);

            bh.consume(result);
        }
    }

    /**
     * Scenario 7: Streaming Transfer (Multiple Small Chunks)
     *
     * Simulates: Processing data in chunks, typical for network/file I/O.
     * Tests overhead of repeated small transfers vs single large transfer.
     */
    @Benchmark
    public void streamingChunkedTransfer(Blackhole bh) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(bufferSize, 8);

            // Transfer in 1KB chunks
            int chunkSize = 1024;
            int numChunks = bufferSize / chunkSize;

            for (int chunk = 0; chunk < numChunks; chunk++) {
                int offset = chunk * chunkSize;
                int size = Math.min(chunkSize, bufferSize - offset);

                MemorySegment.copy(
                    sourceHeapArray, offset,
                    segment, ValueLayout.JAVA_BYTE, offset,
                    size
                );
            }

            bh.consume(segment);
        }
    }

    /**
     * Scenario 8: Write-Heavy Workload
     *
     * Simulates: Frequent writes to native memory.
     * Tests MemorySegment write performance vs DirectByteBuffer.
     */
    @Benchmark
    public void writeHeavyNativeWorkload(Blackhole bh) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(bufferSize, 8);

            // Write pattern: simulates encoding/serialization
            long writeCount = Math.min(bufferSize / 4, 1000);
            for (long i = 0; i < writeCount; i++) {
                long offset = (i * 4) % bufferSize;
                segment.set(ValueLayout.JAVA_INT, offset, (int) i);
            }

            bh.consume(segment);
        }
    }

    /**
     * Scenario 9: Read-Heavy Workload
     *
     * Simulates: Frequent reads from native memory.
     * Tests MemorySegment read performance after initial copy.
     */
    @Benchmark
    public void readHeavyNativeWorkload(Blackhole bh) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(bufferSize, 8);

            // Initial copy
            MemorySegment.copy(sourceHeapArray, 0, segment, ValueLayout.JAVA_BYTE, 0, bufferSize);

            // Read pattern: simulates decoding/deserialization
            long sum = 0;
            long readCount = Math.min(bufferSize, 1000);
            for (long i = 0; i < readCount; i++) {
                sum += segment.get(ValueLayout.JAVA_BYTE, i);
            }

            bh.consume(sum);
        }
    }
}
