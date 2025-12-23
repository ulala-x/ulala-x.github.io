package com.ulalax.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks for actual native function calls with ZMQ-style operations.
 * This provides fair comparison with C# P/Invoke benchmarks.
 *
 * Key difference from C#:
 * - C# can use 'fixed' keyword to pin managed arrays → Zero-copy
 * - Java cannot pin heap arrays → Must copy from Heap to Native memory
 *
 * Test scenarios:
 * 1. Send: Read-only operation (Heap → Native copy required)
 * 2. Recv: Write operation (Native → Heap copy required)
 * 3. Transform: Read-write operation (Heap → Native → Heap, 2 copies required)
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {
    "--enable-native-access=ALL-UNNAMED",
    "-Djava.library.path=/home/ulalax/project/ulalax/ulala-x.github.io/project/csharp-java-memory/native/build"
})
public class NativeInteropBenchmarks {

    /**
     * Test data sizes: 64B, 1KB, 64KB, 1MB
     * These represent typical scenarios from small messages to large data transfers
     */
    @Param({"64", "1024", "65536", "1048576"})
    private int bufferSize;

    private byte[] heapArray;
    private Arena arena;
    private MemorySegment reusableSegment;

    // Native function handles
    private MethodHandle mockSend;
    private MethodHandle mockRecv;
    private MethodHandle mockTransform;

    @Setup
    public void setup() throws Throwable {
        // Initialize heap array with test data
        heapArray = new byte[bufferSize];
        for (int i = 0; i < bufferSize; i++) {
            heapArray[i] = (byte) (i & 0xFF);
        }

        // Create shared arena for native memory management
        arena = Arena.ofShared();
        reusableSegment = arena.allocate(bufferSize, 8);

        // Load native library and bind functions using FFM API
        Linker linker = Linker.nativeLinker();
        String libPath = "/home/ulalax/project/ulalax/ulala-x.github.io/project/csharp-java-memory/native/build/libmockzmq.so";
        SymbolLookup lookup = SymbolLookup.libraryLookup(libPath, arena);

        // int64_t mock_send(const uint8_t* data, size_t len)
        mockSend = linker.downcallHandle(
            lookup.find("mock_send").orElseThrow(),
            FunctionDescriptor.of(
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG
            )
        );

        // int64_t mock_recv(uint8_t* buf, size_t len)
        mockRecv = linker.downcallHandle(
            lookup.find("mock_recv").orElseThrow(),
            FunctionDescriptor.of(
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG
            )
        );

        // void mock_transform(uint8_t* data, size_t len)
        mockTransform = linker.downcallHandle(
            lookup.find("mock_transform").orElseThrow(),
            FunctionDescriptor.ofVoid(
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG
            )
        );
    }

    @TearDown
    public void tearDown() {
        if (arena != null) {
            arena.close();
        }
    }

    /**
     * Scenario 1: Send operation
     * Java requires: Heap → Native copy before calling native function
     * C# advantage: Can use 'fixed' keyword to avoid copy
     */
    @Benchmark
    public long send_HeapCopyToNative(Blackhole bh) throws Throwable {
        // Copy heap array to native memory
        MemorySegment.copy(heapArray, 0, reusableSegment, ValueLayout.JAVA_BYTE, 0, bufferSize);

        // Call native function
        long result = (long) mockSend.invokeExact(reusableSegment, (long) bufferSize);
        bh.consume(result);
        return result;
    }

    /**
     * Scenario 1b: Send with allocation (worst case)
     * This includes both allocation and copy overhead
     */
    @Benchmark
    public long send_AllocateAndCopy(Blackhole bh) throws Throwable {
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment tempSegment = tempArena.allocate(bufferSize, 8);
            MemorySegment.copy(heapArray, 0, tempSegment, ValueLayout.JAVA_BYTE, 0, bufferSize);

            long result = (long) mockSend.invokeExact(tempSegment, (long) bufferSize);
            bh.consume(result);
            return result;
        }
    }

    /**
     * Scenario 2: Receive operation
     * Java requires: Call native function, then Native → Heap copy
     * C# advantage: Native function can write directly to pinned array
     */
    @Benchmark
    public long recv_NativeCopyToHeap(Blackhole bh) throws Throwable {
        // Call native function (writes to native memory)
        long result = (long) mockRecv.invokeExact(reusableSegment, (long) bufferSize);

        // Copy native memory back to heap array
        MemorySegment.copy(reusableSegment, ValueLayout.JAVA_BYTE, 0, heapArray, 0, bufferSize);

        bh.consume(result);
        bh.consume(heapArray);
        return result;
    }

    /**
     * Scenario 2b: Receive with allocation (worst case)
     */
    @Benchmark
    public long recv_AllocateAndCopy(Blackhole bh) throws Throwable {
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment tempSegment = tempArena.allocate(bufferSize, 8);

            long result = (long) mockRecv.invokeExact(tempSegment, (long) bufferSize);
            MemorySegment.copy(tempSegment, ValueLayout.JAVA_BYTE, 0, heapArray, 0, bufferSize);

            bh.consume(result);
            bh.consume(heapArray);
            return result;
        }
    }

    /**
     * Scenario 3: Transform operation (read-write)
     * Java requires: Heap → Native copy, call function, Native → Heap copy
     * This is the worst case scenario with 2 copies required
     *
     * C# advantage: Native function can read/write directly to pinned array (0 copies)
     */
    @Benchmark
    public void transform_RoundTripCopy(Blackhole bh) throws Throwable {
        // Copy heap to native
        MemorySegment.copy(heapArray, 0, reusableSegment, ValueLayout.JAVA_BYTE, 0, bufferSize);

        // Call native function (modifies data in-place)
        mockTransform.invokeExact(reusableSegment, (long) bufferSize);

        // Copy native back to heap
        MemorySegment.copy(reusableSegment, ValueLayout.JAVA_BYTE, 0, heapArray, 0, bufferSize);

        bh.consume(heapArray);
    }

    /**
     * Scenario 3b: Transform with allocation (worst case)
     */
    @Benchmark
    public void transform_AllocateAndRoundTrip(Blackhole bh) throws Throwable {
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment tempSegment = tempArena.allocate(bufferSize, 8);

            // Copy heap to native
            MemorySegment.copy(heapArray, 0, tempSegment, ValueLayout.JAVA_BYTE, 0, bufferSize);

            // Call native function
            mockTransform.invokeExact(tempSegment, (long) bufferSize);

            // Copy native back to heap
            MemorySegment.copy(tempSegment, ValueLayout.JAVA_BYTE, 0, heapArray, 0, bufferSize);

            bh.consume(heapArray);
        }
    }

    /**
     * Baseline: Just the copy overhead (no native call)
     * This helps understand how much overhead comes from copying vs native call
     */
    @Benchmark
    public void baseline_CopyOnly(Blackhole bh) {
        MemorySegment.copy(heapArray, 0, reusableSegment, ValueLayout.JAVA_BYTE, 0, bufferSize);
        bh.consume(reusableSegment);
    }
}
