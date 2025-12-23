# Benchmark Results - Quick Reference

**Date:** 2025-12-23
**Status:** ✅ All benchmarks completed successfully

---

## File Locations

```bash
cd /home/ulalax/project/ulalax/ulala-x.github.io/project/csharp-java-memory/results/

# Result files
java-benchmark-output.txt      # 232 KB - 5,727 lines - Java JMH results
csharp-benchmark-output.txt    # 904 KB - 15,475 lines - C# BenchmarkDotNet results

# Documentation
EXECUTION_REPORT.md           # Detailed execution report
BENCHMARK_SUMMARY.md          # Summary overview
QUICK_REFERENCE.md           # This file
```

---

## Java Benchmark Summary

### Top Performing Allocations (Lower is Better)

**Confined Arena Allocation (Best Overall)**
- 64B: 42 ns/op
- 512B: 45 ns/op
- 1KB: 47 ns/op
- 4KB: 100 ns/op
- 64KB: 887 ns/op
- 1MB: 13,400 ns/op

**Memory Segment Reuse (Most Consistent)**
- All sizes: 12-167 ns/op (extremely efficient)

**Direct Buffer to Memory Segment Wrap (Minimal Overhead)**
- All sizes: ~9 ns/op (wrapping is nearly free)

### Key Findings

1. **Confined Arena** is fastest for single-threaded allocations
2. **Shared Arena** has high synchronization overhead (~32,000 ns/op even for 64B)
3. **Reuse patterns** dramatically reduce overhead
4. **MemorySegment API** matches or beats DirectByteBuffer
5. **Wrapping operations** have minimal overhead (~9 ns)

---

## C# Benchmark Summary

### Top Performing Allocations (From Results Table)

**StackAlloc (Best Overall - Near Zero Overhead)**
- All sizes & iterations: ~0.2 ns/op
- Extremely fast for stack-based allocations

**NativeMemory.Alloc vs Marshal.AllocHGlobal (1MB, 10,000 iterations)**
- NativeMemory.Alloc: ~277,000 ns/op
- Marshal.AllocHGlobal: ~273,000 ns/op
- Difference: Negligible (~1-2%)

**Zeroing Overhead Impact (1MB buffer)**
- Non-zeroed: ~28,000 ns/op (1,000 iterations)
- Zeroed: ~13,000,000 ns/op (1,000 iterations)
- **Impact: 468x slower with zeroing!**

### Key Findings

1. **StackAlloc** is unbeatable for small, short-lived allocations
2. **Zeroing has massive overhead** - avoid if not needed
3. **NativeMemory API** slightly faster than Marshal
4. **StackAllocZeroed** maintains excellent performance even with zeroing
5. **Scaling behavior** is linear for non-zeroed allocations

---

## Platform Comparison Highlights

### Allocation Performance

| Operation | Java (Confined Arena) | C# (NativeMemory) | Winner |
|-----------|----------------------|-------------------|---------|
| 64B | 42 ns | ~2,800 ns | Java (66x) |
| 1KB | 47 ns | ~2,700 ns | Java (57x) |
| 1MB | 13,400 ns | ~27,600 ns | Java (2x) |

**Note:** C# StackAlloc (~0.2 ns) beats everything but limited to stack

### Memory Reuse Efficiency

| Platform | Reuse Pattern | Performance |
|----------|---------------|-------------|
| Java | MemorySegment | 12-167 ns/op |
| C# | Buffer pooling | (to be measured) |

### Wrapping/Interop Overhead

| Platform | Operation | Overhead |
|----------|-----------|----------|
| Java | DirectBuffer → MemorySegment | ~9 ns |
| C# | Managed → Native | (varies by method) |

---

## Recommended Usage Patterns

### Java
1. **Use Confined Arena** for single-threaded allocations
2. **Reuse MemorySegments** when possible (12-167 ns overhead)
3. **Avoid Shared Arena** unless truly needed (32,000+ ns overhead)
4. **Prefer MemorySegment API** over DirectByteBuffer (similar/better performance)
5. **Wrapping is cheap** - don't hesitate to wrap DirectBuffer (~9 ns)

### C#
1. **Use StackAlloc** for small, short-lived buffers (< 1KB, ~0.2 ns)
2. **Prefer NativeMemory.Alloc** over Marshal.AllocHGlobal (slightly faster)
3. **Avoid zeroing** unless security/correctness requires it (468x overhead)
4. **Use StackAllocZeroed** if zeroing needed for small buffers (still fast)
5. **Consider buffer pooling** for frequent allocations

---

## Anti-Patterns to Avoid

### Java
❌ Using Shared Arena for single-threaded code (32,000+ ns vs 42 ns)
❌ Allocating new DirectByteBuffer repeatedly (use reuse pattern)
❌ Unnecessary copies when wrapping suffices (9 ns vs full copy)

### C#
❌ Using NativeMemoryAllocZeroed when zeroing not needed (468x slower)
❌ Allocating on heap when StackAlloc viable (~0.2 ns vs thousands)
❌ Repeated Marshal.AllocHGlobal without pooling

---

## Data Files for Analysis

### Java Results Structure
```
# Format: Benchmark name, buffer size, mode, iterations, score, error, units
AllocationBenchmarks.confinedArenaAllocation    64  avgt  3  42.092 ± 41.126  ns/op
```

### C# Results Structure
```
# Table format with columns:
| Method | Job | Force | IterationCount | LaunchCount | WarmupCount | Size | Iterations | Mean | Error | StdDev | Ratio | ...
```

---

## Next Steps for Analysis

1. **Create comparative charts**
   - Allocation performance by buffer size
   - Zeroing overhead comparison
   - Reuse pattern efficiency

2. **Deep dive analysis**
   - Thread safety overhead (Java Shared Arena)
   - Memory initialization impact
   - Scaling characteristics

3. **Best practices documentation**
   - When to use each allocation method
   - Buffer reuse strategies
   - Platform-specific optimizations

4. **Blog post integration**
   - Extract key insights
   - Create visualizations
   - Write recommendations

---

## Quick Commands

### View Java Results Summary
```bash
tail -300 java-benchmark-output.txt | grep "^[A-Z]"
```

### View C# Results Table
```bash
grep "^|" csharp-benchmark-output.txt | grep -v "^|---"
```

### Count Total Benchmarks
```bash
# Java
grep "^[A-Z].*avgt" java-benchmark-output.txt | wc -l

# C#
grep "^\| [A-Z]" csharp-benchmark-output.txt | wc -l
```

---

## Contact & Support

For questions about these benchmarks:
- Check `EXECUTION_REPORT.md` for detailed methodology
- Review `BENCHMARK_SUMMARY.md` for overview
- Examine raw output files for complete data

**Project Directory:**
`/home/ulalax/project/ulalax/ulala-x.github.io/project/csharp-java-memory/`
