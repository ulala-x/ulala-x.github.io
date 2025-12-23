# Benchmark Execution Summary

**Date:** 2025-12-23
**Environment:** WSL2 (Linux 6.6.87.2)
**Java Version:** Java 22.0.2 (Oracle HotSpot)
**C# Version:** .NET 8.0.122

---

## Benchmark Execution Status

### Java JMH Benchmarks ✅ COMPLETED
- **Framework:** JMH 1.37
- **Configuration:**
  - Warmup: 1 iteration, 1 second each
  - Measurement: 3 iterations, 1 second each
  - Forks: 1
- **Output File:** `java-benchmark-output.txt` (232 KB)
- **Total Benchmarks Executed:** 204 benchmark methods
- **Status:** Successfully completed

### C# BenchmarkDotNet Benchmarks ⏳ IN PROGRESS
- **Framework:** BenchmarkDotNet 0.14.0
- **Configuration:** `--job short`
- **Output File:** `csharp-benchmark-output.txt` (364+ KB and growing)
- **Status:** Currently running (started at 12:47 UTC)

---

## Java Benchmark Results Overview

The Java benchmarks tested the following categories:

### 1. Allocation Benchmarks
- **Arena-based allocations:** Testing different arena types (confined, global, shared, auto)
- **Buffer sizes tested:** 64B, 512B, 1KB, 4KB, 64KB, 1MB
- **Key findings:** Confined arenas show the best performance for allocation

### 2. Copy Overhead Benchmarks
- **Heap-to-native copy operations**
- **Direct buffer wrapping**
- **Memory segment operations**
- **Key findings:** Reusable buffers significantly reduce overhead

### 3. Direct Buffer Benchmarks
- **DirectByteBuffer vs MemorySegment performance**
- **Allocation with and without initialization**
- **Bulk copy operations**
- **Key findings:** MemorySegment API shows competitive or better performance

### 4. Data Transfer Benchmarks
- **Native allocation and processing**
- **Buffer wrapping overhead**
- **Read/write operations**

---

## C# Benchmark Categories (Pending Completion)

The C# benchmarks are testing:

### 1. Allocation Benchmarks
- `NativeMemoryAlloc` - Native memory allocation
- `NativeMemoryAllocZeroed` - Zero-initialized allocation
- `MarshalAllocHGlobal` - Marshal-based allocation
- `StackAlloc` - Stack allocation
- `StackAllocZeroed` - Zero-initialized stack allocation

### 2. Data Transfer Benchmarks
- Memory copy operations
- Data marshalling performance

### 3. Pinning Benchmarks
- GC handle pinning
- Fixed statement performance
- Memory pinning overhead

**Parameters tested:**
- Buffer sizes: 64B, 512B, 1KB, 4KB, 64KB
- Iterations: 1,000 and 10,000

---

## Output Files Location

All benchmark results are stored in:
```
/home/ulalax/project/ulalax/ulala-x.github.io/project/csharp-java-memory/results/
```

- `java-benchmark-output.txt` - Complete Java JMH results ✅
- `csharp-benchmark-output.txt` - C# BenchmarkDotNet results (in progress) ⏳
- `BENCHMARK_SUMMARY.md` - This summary document

---

## Next Steps

1. Wait for C# benchmarks to complete
2. Extract and analyze final results from both platforms
3. Create comparative analysis charts
4. Document key performance insights
5. Update blog post with findings

---

## Notes

- Java benchmarks completed successfully in approximately 14 minutes
- C# benchmarks are using the `--job short` configuration for faster execution
- Both benchmarks use Release/optimized builds
- No background processes were running during benchmark execution
- Results may vary based on system load and WSL2 performance characteristics
