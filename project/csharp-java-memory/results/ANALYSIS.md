# C# Native Interop Benchmark Results Analysis

## Test Environment
- **CPU**: Intel Core Ultra 7 265K (20 cores)
- **OS**: Ubuntu 24.04.3 LTS
- **Runtime**: .NET 8.0.22
- **BenchmarkDotNet**: v0.14.0
- **Native Library**: libmockzmq.so (compiled with gcc 13.3.0, -O3)

## Key Findings

### 1. Zero-Copy vs Copy Performance Comparison

#### 64B (Small Data)

| Operation | Zero-Copy (fixed) | With Copy | Difference |
|-----------|-------------------|-----------|------------|
| **Send** (read) | 7.26 ns | 16.46 ns | **2.27x slower** |
| **Recv** (write) | 7.94 ns | 16.38 ns | **2.06x slower** |
| **Transform** (r/w) | 5.74 ns | 16.27 ns | **2.83x slower** |

**Analysis**: Even with tiny 64-byte data, copy overhead is significant (9-11 ns). Zero-copy is consistently 2-3x faster.

#### 1KB (Medium Small Data)

| Operation | Zero-Copy (fixed) | With Copy | Difference |
|-----------|-------------------|-----------|------------|
| **Send** (read) | 105.87 ns | 232.78 ns | **2.20x slower** |
| **Recv** (write) | 105.16 ns | 238.08 ns | **2.26x slower** |
| **Transform** (r/w) | 21.88 ns | 262.79 ns | **12.01x slower** |

**Analysis**: At 1KB, copy overhead becomes more pronounced (~127-240 ns). Transform operation shows dramatic difference because it requires round-trip copy (heap→native→heap).

#### 64KB (Large Data)

| Operation | Zero-Copy (fixed) | With Copy | Difference |
|-----------|-------------------|-----------|------------|
| **Send** (read) | 6,927 ns | 7,814 ns | **1.13x slower** |
| **Recv** (write) | 6,572 ns | 7,593 ns | **1.16x slower** |
| **Transform** (r/w) | 673 ns | 2,680 ns | **3.98x slower** |

**Analysis**: At 64KB, send/recv operations show smaller differences (~13-16%) because native function processing time dominates. However, transform still shows 4x difference due to double copy.

#### 1MB (Very Large Data)

| Operation | Zero-Copy (fixed) | With Copy | Difference |
|-----------|-------------------|-----------|------------|
| **Send** (read) | 111,797 ns | 125,954 ns | **1.13x slower** |
| **Recv** (write) | 105,208 ns | 120,584 ns | **1.15x slower** |
| **Transform** (r/w) | 19,855 ns | 45,583 ns | **2.30x slower** |

**Analysis**: At 1MB, copy overhead is ~14-15 μs for single copy, ~25 μs for round-trip. Zero-copy advantage is still clear but less dramatic than smaller sizes.

### 2. Operation Type Analysis

#### Send Operation (Read-Only)
```
C# Zero-copy: Pin managed array → Pass pointer to native → Native reads data
C# With Copy:  Allocate native → Copy managed to native → Pass pointer → Free native

Cost breakdown (1MB):
- Zero-copy: ~112 μs (pin + native processing)
- With copy: ~126 μs (allocate + 14μs copy + native processing + free)
```

#### Recv Operation (Write-Only)
```
C# Zero-copy: Pin managed array → Pass pointer → Native writes data → Unpin
C# With Copy:  Allocate native → Pass pointer → Native writes → Copy to managed → Free

Cost breakdown (1MB):
- Zero-copy: ~105 μs (pin + native processing)
- With copy: ~121 μs (allocate + native processing + 15μs copy + free)
```

#### Transform Operation (Read-Write)
```
C# Zero-copy: Pin managed array → Pass pointer → Native reads/writes in-place → Unpin
C# With Copy:  Allocate → Copy to native → Native transforms → Copy back → Free

Cost breakdown (1MB):
- Zero-copy: ~20 μs (pin + in-place transformation)
- With copy: ~46 μs (allocate + 14μs copy + transform + 14μs copy back + free)
```

**Key Insight**: Transform operation benefits the most from zero-copy because it eliminates **two** copy operations.

### 3. Copy Cost Analysis

| Data Size | Single Copy Cost | Round-Trip Cost |
|-----------|------------------|-----------------|
| 64B | ~9 ns | ~11 ns |
| 1KB | ~127 ns | ~241 ns |
| 64KB | ~1,021 ns | ~2,007 ns |
| 1MB | ~14,157 ns | ~25,728 ns |

**Memory Copy Bandwidth**:
- 1KB: 1024 / 127ns ≈ **8.06 GB/s**
- 64KB: 65536 / 1021ns ≈ **64.2 GB/s**
- 1MB: 1048576 / 14157ns ≈ **74.1 GB/s**

The bandwidth increases with size, suggesting better cache/memory efficiency for larger contiguous copies.

### 4. Pin Overhead Analysis

Pin overhead can be estimated by comparing zero-copy times:

| Data Size | Pin Overhead (estimated) |
|-----------|--------------------------|
| 64B | ~7-8 ns |
| 1KB | ~22-106 ns |
| 64KB | ~673-6,927 ns |
| 1MB | ~19,855-111,797 ns |

**Note**: These include both pin overhead and native function processing time. Pure pin overhead is likely much smaller (~1-10 ns).

## Comparison with Java (Expected)

Based on these C# results, we can predict Java FFM API performance:

### Expected Java Performance (with required copy)

| Operation | Size | C# Zero-copy | Java (expected) | Basis |
|-----------|------|--------------|-----------------|-------|
| Send | 1KB | 106 ns | ~233 ns | Match C# with-copy |
| Send | 1MB | 112 μs | ~126 μs | Match C# with-copy |
| Transform | 1KB | 22 ns | ~263 ns | Match C# with-copy |
| Transform | 1MB | 20 μs | ~46 μs | Match C# with-copy |

### Key Differences

1. **C# Advantage**: Can use `fixed` keyword to pin managed arrays → Zero-copy
2. **Java Constraint**: Cannot pin heap arrays → Must copy heap to native memory
3. **Performance Impact**:
   - Small data (< 1KB): 2-12x difference
   - Large data (> 1MB): 1.1-2.3x difference

### When Copy Overhead Matters Most

1. **High-frequency small messages** (< 1KB): Copy overhead is 2-3x
2. **Read-write operations** (transform): Copy overhead is 2-12x due to round-trip
3. **Real-time applications**: Even microseconds matter

### When Copy Overhead Is Acceptable

1. **Large data with infrequent calls**: 10-15 μs overhead per MB is often acceptable
2. **Write-once, read-many**: If data stays in native memory, copy is one-time cost
3. **Type safety priority**: Java's approach avoids unsafe operations

## Recommendations

### Choose C# when:
- **Zero-copy is critical** for performance (real-time, high-frequency)
- **Large data processing** with tight latency requirements
- **Read-write operations** on managed data
- Comfortable with `unsafe` code

### Choose Java when:
- **Type safety** is priority over raw performance
- **Cross-platform consistency** is important
- Copy overhead (< 15 μs/MB) is acceptable for your use case
- Want to avoid `unsafe`/pinning complexities

## Practical Example: ZMQ-style Message Queue

Sending 1000 messages of 1KB each:

| Language | Approach | Time per message | Total Time |
|----------|----------|------------------|------------|
| C# | Zero-copy (fixed) | 106 ns | **106 μs** |
| C# | With copy | 233 ns | 233 μs |
| Java | FFM API (copy required) | ~233 ns | ~233 μs |

**Conclusion**: For high-frequency messaging, C#'s zero-copy can provide **2.2x throughput improvement** over Java's copy-based approach.

## Next Steps

1. Run Java benchmarks to confirm predictions
2. Test with actual ZMQ or similar real-world library
3. Measure under different GC pressure scenarios
4. Compare with JNI (Java's older native interface)
