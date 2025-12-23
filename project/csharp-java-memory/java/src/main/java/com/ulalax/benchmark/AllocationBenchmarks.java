package com.ulalax.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.TimeUnit;

/**
 * Arena-based Memory Allocation Benchmarks
 *
 * Compares different Arena allocation strategies:
 * - Arena.ofConfined(): Single-threaded, automatic cleanup
 * - Arena.ofShared(): Multi-threaded shared access
 * - Arena.ofAuto(): GC-managed lifecycle
 * - Arena reuse patterns
 *
 * Tests allocation performance across various buffer sizes from 64 bytes to 1 MB.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1)
public class AllocationBenchmarks {

    @Param({"64", "512", "1024", "4096", "65536", "1048576"})
    private int bufferSize;

    private Arena reusableArena;

    @Setup
    public void setup() {
        // Pre-create arena for reuse benchmarks
        reusableArena = Arena.ofConfined();
    }

    @TearDown
    public void teardown() {
        if (reusableArena != null && reusableArena.scope().isAlive()) {
            reusableArena.close();
        }
    }

    /**
     * Benchmark: Arena.ofConfined() allocation
     *
     * Confined arenas are owned by a single thread and automatically cleaned up.
     * Provides the best performance for single-threaded scenarios.
     */
    @Benchmark
    public void confinedArenaAllocation(Blackhole bh) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(bufferSize, 8);
            bh.consume(segment);
        }
    }

    /**
     * Benchmark: Arena.ofShared() allocation
     *
     * Shared arenas can be accessed from multiple threads.
     * Requires synchronization, resulting in higher overhead.
     */
    @Benchmark
    public void sharedArenaAllocation(Blackhole bh) {
        try (Arena arena = Arena.ofShared()) {
            MemorySegment segment = arena.allocate(bufferSize, 8);
            bh.consume(segment);
        }
    }

    /**
     * Benchmark: Arena.ofAuto() allocation
     *
     * Auto arenas are managed by the garbage collector.
     * Memory is freed when the arena becomes unreachable.
     * Adds GC pressure but provides automatic cleanup.
     */
    @Benchmark
    public void autoArenaAllocation(Blackhole bh) {
        Arena arena = Arena.ofAuto();
        MemorySegment segment = arena.allocate(bufferSize, 8);
        bh.consume(segment);
        // No explicit close - GC will handle cleanup
    }

    /**
     * Benchmark: Arena reuse pattern
     *
     * Allocates from a pre-created arena without closing it.
     * This simulates the best-case scenario where an arena is kept alive
     * and reused for multiple allocations within a single scope.
     */
    @Benchmark
    public void arenaReuse(Blackhole bh) {
        MemorySegment segment = reusableArena.allocate(bufferSize, 8);
        bh.consume(segment);
    }

    /**
     * Benchmark: Global arena allocation
     *
     * Uses the global arena which never closes.
     * Fastest allocation but memory is never freed until JVM shutdown.
     * Suitable only for long-lived data.
     */
    @Benchmark
    public void globalArenaAllocation(Blackhole bh) {
        MemorySegment segment = Arena.global().allocate(bufferSize, 8);
        bh.consume(segment);
    }

    /**
     * Benchmark: Allocation with initialization
     *
     * Allocates memory and writes a pattern to ensure memory is actually touched.
     * More realistic than pure allocation as most use cases require initialization.
     */
    @Benchmark
    public void confinedArenaAllocationWithInit(Blackhole bh) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(bufferSize, 8);
            // Initialize memory with a pattern
            for (long i = 0; i < bufferSize; i++) {
                segment.set(ValueLayout.JAVA_BYTE, i, (byte) (i & 0xFF));
            }
            bh.consume(segment);
        }
    }

    /**
     * Benchmark: Multiple small allocations in single arena
     *
     * Tests arena allocation efficiency when performing multiple allocations
     * within the same arena scope. Simulates typical usage patterns.
     */
    @Benchmark
    public void multipleAllocationsInSameArena(Blackhole bh) {
        try (Arena arena = Arena.ofConfined()) {
            // Allocate 10 segments of the specified size
            for (int i = 0; i < 10; i++) {
                MemorySegment segment = arena.allocate(bufferSize / 10, 8);
                bh.consume(segment);
            }
        }
    }
}
