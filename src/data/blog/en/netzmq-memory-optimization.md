---
author: Ulala-X
pubDatetime: 2025-12-20T00:00:00+09:00
title: Why Net.ZMQ Doesn't Use Message Pooling or ZeroCopy
slug: en/netzmq-memory-optimization
featured: true
draft: false
lang: en
tags:
  - zeromq
  - netzmq
  - performance
  - optimization
  - csharp
description: Performance Optimization Verification - Benchmark Data-Driven Analysis
---

# Why Net.ZMQ Doesn't Use Message Pooling or ZeroCopy

> Performance Optimization Verification: Benchmark Data-Driven Analysis

**December 20, 2025** / Commits: [32b4ee2](https://github.com/ulalax/netzmq/commit/32b4ee2), [d122e62](https://github.com/ulalax/netzmq/commit/d122e62)

---

## Overview

ZeroMQ is a high-performance asynchronous messaging library capable of processing millions of messages per second. When using such a high-performance library in .NET, developers naturally consider two optimizations:

> "Wouldn't memory pooling reduce allocation overhead?"
> "Wouldn't ZeroCopy eliminate copy costs?"

These optimizations seem theoretically obvious. However, Net.ZMQ does not provide these features.

This article shares the results of actually implementing and measuring these two optimization techniques. It explains why "seemingly obvious" optimizations were not adopted, backed by benchmark data.

### Verified Optimization Techniques

1. **MessagePool**: Reducing allocation overhead through native memory pooling
2. **ZeroCopy**: Performance improvement through memory copy elimination

### Measurement Results

```
MessagePool: 27% faster for small messages (≤512B), 11% slower for large messages, performance crash under burst load
ZeroCopy: 2.43x slower for small messages, effective from 64KB onwards
```

### Conclusion

Despite theoretical benefits, both techniques were decided not to be adopted in actual environments. This article explains why, along with benchmark data.

---

## Chapter 1: MessagePool - Native Memory Pooling Verification

### Background: Why MessagePool Was Needed

Net.ZMQ must handle ZeroMQ's native messages (`zmq_msg_t`). Each message send/receive operation involves native memory allocation/deallocation, which can be a source of performance overhead.

**Hypothesis**: Pre-allocating and reusing native memory can reduce allocation overhead.

**Note**: .NET's ArrayPool pools managed memory. MessagePool required a separate mechanism to pool native memory (`zmq_msg_t`).

### Implementation: Native Memory Pooling Mechanism

```csharp
public sealed class MessagePool
{
    // 19 buckets: 16B ~ 4MB (powers of 2)
    private static readonly int[] BucketSizes =
    [
        16, 32, 64, 128, 256, 512,
        1024, 2048, 4096, 8192, 16384, 32768, 65536,
        131072, 262144, 524288,
        1048576, 2097152, 4194304
    ];

    // ConcurrentStack pool per bucket
    private readonly ConcurrentStack<Message>[] _pooledMessages;

    // Per-bucket counters (Interlocked-based thread-safe)
    private long[] _pooledMessageCounts;

    // Statistics tracking
    private long _totalRents;
    private long _totalReturns;
    private long _poolHits;
    private long _poolMisses;
}
```

**Core Mechanism**:

1. **Bucket allocation**: Round up requested size to nearest power of 2
2. **Reusable Message**: Initialize `zmq_msg_t` once with bucket size
3. **Automatic return**: Auto-return to pool via ZeroMQ's free callback
4. **Thread-safe**: ConcurrentStack + Interlocked counters

**Usage Example**:

```csharp
// Receive with MessagePool
using var msg = socket.ReceiveWithPool();  // Rent from pool
// Process data
// Automatically returns to pool on Dispose
```

### Implementation Details

MessagePool was developed through several iterations:

- **f195d1c**: Initial implementation (+200 lines)
- **78816d9**: Double-free bug fix (+500 lines)
- **45ac578**: Message object reuse (+100 lines)
- **cb81713**: ActualSize tracking (+150 lines)
- **e8527f8**: Thread-safety with Interlocked counters (+80 lines)
- **d122e62**: Comprehensive tests (+1,753 lines)

**Final Result**:
- MessagePool.cs: **697 lines**
- MessagePoolTests.cs: **1,753 lines**
- All tests passed, no memory leaks

### Benchmark Results

**Test Environment**:
- CPU: Intel Core Ultra 7 265K (20 cores)
- OS: Ubuntu 24.04.3 LTS
- Runtime: .NET 8.0.22
- BenchmarkDotNet v0.14.0

**Measurement Results**:

| Message Size | Baseline (new Message) | ReceiveWithPool | Ratio |
|--------------|------------------------|-----------------|-------|
| **64B** | 3.33 ms (3.00M msg/sec) | 2.41 ms (4.16M msg/sec) | **0.72x (27% faster)** |
| **1KB** | 7.25 ms (1.38M msg/sec) | 7.69 ms (1.30M msg/sec) | **1.06x (nearly same)** |
| **64KB** | 134.3 ms (74.5K msg/sec) | 149.4 ms (66.9K msg/sec) | **1.11x (11% slower)** |

**Observations**:
- ≤512B: Allocation overhead reduction exceeds copy cost
- ≥1KB: Copy cost becomes comparable to allocation savings
- 64KB: Copy cost exceeds allocation savings

**Additional Test - Burst Load**:
When testing send/receive in burst patterns, Pool version's performance degraded significantly. ConcurrentStack synchronization cost and cross-thread cache misses became much more severe under load.

### Root Cause Analysis

Performance characteristics were analyzed through profiler and additional benchmarks.

#### Key Question: Why does performance vary with message size?

**Actual implementation**:
```csharp
// Allocate 4MB fixed buffer on Socket creation
private nint _recvBufferPtr;
private const int MaxRecvBufferSize = 4 * 1024 * 1024;  // 4 MB

public Message? ReceiveWithPool(RecvFlags flags)
{
    // 1. Receive into 4MB buffer
    int actualSize = Recv(_recvBufferPtr, MaxRecvBufferSize, flags);

    // 2. Rent from pool with actual size
    var msg = MessagePool.Shared.Rent(actualSize);

    // 3. Copy! (This is the problem)
    msg.CopyFromNative(_recvBufferPtr, actualSize);

    return msg;
}
```

**Cost Analysis**:
- **≤512B**: Allocation savings >> Copy cost → **Pool clearly faster** (27% at 64B) ✓
- **1KB**: Allocation savings ≈ Copy cost → **About the same** (Pool or not, no difference)
- **64KB**: Allocation savings << Copy cost → **Pool 11% slower** ✗

Copy cost scales proportionally with message size. Beyond 512B, copy cost starts catching up with allocation savings.

Copy-only overhead measured in benchmarks:

| Message Size | SpanCopy Overhead | % of Total |
|--------------|------------------|------------|
| **64B** | 10.7 μs | 0.3% |
| **1KB** | 105.3 μs | 1.4% |
| **64KB** | 9,506 μs | **6.4%** |

Copy is negligible for small messages, but **takes 6.4% of total time at 64KB**.

Why is copying necessary?

ZeroMQ's `zmq_recv()` internally allocates memory when receiving messages. This is not under our control. Therefore:
1. Pre-allocate a large buffer (4MB)
2. Receive into it
3. Rent from pool with actual size
4. Copy

Advantage: Can bypass ZeroMQ allocation. Disadvantage: Copy is mandatory.

For small messages, "ZeroMQ allocation savings > copy cost" is beneficial, but as messages grow, copy cost becomes dominant.

#### 2. **LIFO Inefficiency** (Cross-thread Scenarios)

```csharp
// ConcurrentStack is LIFO structure
private readonly ConcurrentStack<Message>[] _pooledMessages;

// Thread A: Rent message
var msg = pool.Rent(64);  // Pop from pool

// Thread B: ZeroMQ callback invoked
pool.Return(msg);  // Return to different CPU core's cache

// Thread A: Rent again
var msg2 = pool.Rent(64);  // Get recently returned msg
                            // -> CPU cache miss!
```

- **Problem**: LIFO is good for single-thread but harms cache locality in cross-thread scenarios
- **Impact**: Partially offsets gains for small messages

#### 3. **Pool Management Overhead**

```csharp
public Message Rent(int size)
{
    Interlocked.Increment(ref _totalRents);  // Statistics

    int bucketIndex = GetBucketIndex(size);   // Find bucket

    if (_pooledMessages[bucketIndex].TryPop(out var msg))  // Pop from pool
    {
        Interlocked.Decrement(ref _pooledMessageCounts[bucketIndex]);
        Interlocked.Increment(ref _poolHits);
        return msg;
    }

    Interlocked.Increment(ref _poolMisses);
    return CreatePooledMessage(bucketSize, bucketIndex);  // Create new
}
```

- ConcurrentStack synchronization cost
- Interlocked counter update cost
- Bucket index calculation cost

### Decision: Why MessagePool Was Removed

**Performance Characteristics Summary**:
- **≤512B**: Pool clearly faster (27% improvement)
- **≥1KB**: Pool or baseline nearly identical
- **64KB**: Pool 11% slower (copy cost increase)
- **Burst load**: Pool performance crash (synchronization overhead)

**Decision Criteria**:
1. Real-world environments use various message sizes
2. Burst loads occur frequently in actual environments
3. 697 lines of implementation + 1,753 lines of tests = high maintenance cost

**Final Decision**:
Decided to remove MessagePool as its benefits did not justify the complexity.

```bash
$ git commit -m "Remove MessagePool and simplify memory strategies"
# 14 files changed, 271 insertions(+), 4185 deletions(-)
```

**Code Removed**: 4,185 lines

---

## Chapter 2: ZeroCopy - Memory Copy Elimination Verification

### Background: Theoretical Benefits of ZeroCopy

During MessagePool analysis, memory copying was identified as one of the major overheads. Accordingly, the ZeroCopy approach that eliminates copying was verified.

**Hypothesis**: Eliminating memory copy will improve performance, especially for large messages.

### ZeroCopy Concept

```csharp
// Normal way: Data copying occurs
var msg = new Message(data);  // Copy data into Message
socket.Send(msg);

// ZeroCopy way: Pass native memory directly
nint ptr = Marshal.AllocHGlobal(size);
unsafe
{
    var span = new Span<byte>((void*)ptr, size);
    data.CopyTo(span);  // Copy to native memory (once)
}

// Transfer ownership to ZeroMQ
using var msg = new Message(ptr, size, p => Marshal.FreeHGlobal(p));
socket.Send(msg);  // Pass pointer only, no copy!
```

**Expected Benefits**:
- Eliminate managed → native memory copy
- Reduce GC pressure
- Significant performance gains for large messages

### Benchmark Results

#### 64B Messages

| Strategy | Throughput | Allocated |
|----------|------------|-----------|
| **ArrayPool** | **4.12M msg/sec** | 1.85 KB |
| ByteArray | 4.10M msg/sec | 9860 KB |
| Message | 2.34M msg/sec | 168 KB |
| **MessageZeroCopy** | **1.69M msg/sec** | 168 KB |

**Observation**: ZeroCopy is **2.43x slower** than ArrayPool

#### 512B Messages

| Strategy | Processing Time | Throughput | Ratio |
|----------|----------------|------------|-------|
| **ArrayPool** | **6.38 ms** | **1.57M msg/sec** | **0.95x** |
| ByteArray | 6.71 ms | 1.49M msg/sec | 1.00x |
| Message | 8.19 ms | 1.22M msg/sec | 1.22x |
| MessageZeroCopy | 13.37 ms | 748K msg/sec | **1.99x** |

**Observation**: ZeroCopy is **2.1x slower** than ArrayPool

#### 1KB Messages

| Strategy | Processing Time | Throughput | Ratio |
|----------|----------------|------------|-------|
| ArrayPool | 9.02 ms | 1.11M msg/sec | 1.01x |
| ByteArray | 8.97 ms | 1.11M msg/sec | 1.00x |
| Message | 9.74 ms | 1.03M msg/sec | 1.09x |
| MessageZeroCopy | 14.61 ms | 684K msg/sec | **1.63x** |

**Observation**: ZeroCopy is still slowest (**1.63x**)

#### 64KB Messages - Performance Reversal

| Strategy | Throughput | Allocated |
|----------|------------|-----------|
| **Message** | **83.9K msg/sec** | 171 KB |
| MessageZeroCopy | 80.2K msg/sec | 171 KB |
| ArrayPool | 70.0K msg/sec | 4.78 KB |
| ByteArray | 70.6K msg/sec | 4GB |

**Observation**: At 64KB, native memory (Message/MessageZeroCopy) is **16% faster** than ArrayPool

### Root Cause Analysis: ZeroCopy Overhead

Analysis was conducted on why performance degraded for small messages despite eliminating copying.

#### 1. P/Invoke Is Expensive

```csharp
// MessageZeroCopy: Multiple P/Invoke calls
nint ptr = Marshal.AllocHGlobal(size);        // P/Invoke #1
// ... copy data ...
var handle = GCHandle.Alloc(callback);        // GCHandle allocation
var result = LibZmq.MsgInitDataPtr(           // P/Invoke #2
    msgPtr, ptr, size, ffnPtr, hintPtr);
socket.Send(msg);                             // P/Invoke #3
// ZeroMQ callback execution: managed -> unmanaged transition
```

**Cost Analysis** (64B message):
- P/Invoke transitions: ~50ns × 3 = 150ns
- GCHandle alloc/free: ~100ns
- Callback marshalling: ~100ns
- Native memory allocation: ~100ns
- **Total overhead: ~450ns**

#### 2. **ArrayPool is Really Fast**

```csharp
// ArrayPool: Pure managed code
var buffer = ArrayPool<byte>.Shared.Rent(size);  // O(1) array rent
socket.Send(buffer.AsSpan(0, size));             // Single P/Invoke
ArrayPool<byte>.Shared.Return(buffer);           // O(1) return
```

**Cost Analysis**:
- Array rent: ~20ns (just array indexing)
- P/Invoke: ~50ns (only once)
- Array return: ~20ns
- **Total overhead: ~90ns**

#### 3. Cost Calculation

Time to copy 64 bytes:
- Approximately **1ns** (benchmark: 10.71μs / 10,000 messages = 1.07ns)

Time saved with ZeroCopy:
- No copy, so **1ns**

Time spent to enable ZeroCopy:
- P/Invoke, GCHandle, Callback, etc... **450ns**

**Net loss: 449ns**

64-byte copy is not performed, saving 1ns, but 450ns is spent to enable it.

This is the zero-copy paradox.

When is the break-even point? Calculations suggest around 2-3KB, but actual benchmarks show **64KB**. Why?

#### 4. Additional Factors

- **CPU cache effect**: Small data stays in L1/L2 cache, making copy very fast
- **.NET JIT optimization**: Managed memory copy is optimized with SIMD instructions
- **GCHandle cost**: GCHandle is more expensive than expected (~100ns)
- **Callback overhead**: Cost of crossing managed-unmanaged boundary

### Cost Comparison Analysis

**ArrayPool (Managed Memory)**:
- Lock-free operation (in most cases)
- Thread-local caching
- SIMD-optimized copying
- CPU cache friendly

**ZeroCopy (Native Memory)**:
- P/Invoke transition overhead
- GCHandle management
- Callback marshalling
- Native memory allocation/deallocation

**Measurement Results**:
- Small messages (≤512B): Managed memory (ArrayPool) is superior
- Large messages (≥64KB): Native memory is superior

---

## Final Architecture: Simplicity Wins

Conclusions from MessagePool removal and ZeroCopy benchmarks:

### Simple 2-Tier Strategy

```
Message size ≤ 512B:  ArrayPool<byte>.Shared  (managed memory pooling)
Message size > 512B:  Message/MessageZeroCopy  (native memory)
```

**Implementation Example**:

```csharp
// Send: Use ArrayPool (≤512B)
var buffer = ArrayPool<byte>.Shared.Rent(size);
try
{
    // Write data to buffer
    socket.Send(buffer.AsSpan(0, size));
}
finally
{
    ArrayPool<byte>.Shared.Return(buffer);
}

// Send: Use MessageZeroCopy (>512B)
nint ptr = Marshal.AllocHGlobal(size);
unsafe
{
    var span = new Span<byte>((void*)ptr, size);
    sourceData.CopyTo(span);
}
using var msg = new Message(ptr, size, p => Marshal.FreeHGlobal(p));
socket.Send(msg);

// Receive: Reuse Message
using var msg = new Message();
socket.Recv(ref msg);
ProcessData(msg.Data);
```

### Performance Summary

| Message Size | Strategy | Throughput | GC Allocation | Improvement |
|--------------|----------|------------|---------------|-------------|
| **64B** | ArrayPool | 4.12M msg/sec | 1.85 KB | +0.5% vs ByteArray, GC -99.98% |
| **512B** | ArrayPool | 1.57M msg/sec | 2.04 KB | +5% vs ByteArray, GC -99.99% |
| **1KB** | ArrayPool | 1.11M msg/sec | 2.24 KB | Same as ByteArray, GC -99.99% |
| **64KB** | Message | 83.9K msg/sec | 171 KB | +16% vs ByteArray, GC -99.95% |

### Decision Flow

```
Send Strategy Selection:
├─ Message size ≤ 512B?
│  └─ YES → Use ArrayPool (best performance)
│     └─ ArrayPool<byte>.Shared.Rent(size)
│
└─ NO → Use Message/MessageZeroCopy
   └─ Marshal.AllocHGlobal + Message(ptr, size, callback)

Receive Mode Selection:
├─ Single socket?
│  └─ Blocking or Poller (nearly identical, 0-6% difference)
│
└─ Multiple sockets?
   └─ Poller (required)
      └─ Poller + Message reuse + batch processing
```

---

## Core Findings

### 1. Importance of Measurement-Based Optimization

**Difference Between Theory and Actual Performance**:
- MessagePool: The benefit of reducing allocation actually exists, but copy cost and synchronization overhead offset this above certain sizes
- ZeroCopy: The benefit of eliminating copy is smaller than interop overhead (P/Invoke, GCHandle, etc.) for small messages

**Copy Cost Scaling**:
- 64B: 10.7μs (10,000 messages)
- 1KB: 105.3μs (10,000 messages)
- 64KB: 9,506μs (10,000 messages)

Copy cost increases linearly with message size. Starting from 512B, copy cost begins to exceed allocation savings.

**Impact of Load Patterns**:
Performance characteristics differ between normal load and burst load. MessagePool showed rapid performance degradation under burst conditions due to synchronization overhead.

**Conclusion**: Actual measurement through benchmarks is essential.

### 2. Complexity vs Performance Gains

**MessagePool Complexity**:
- 697 lines of implementation
- 1,753 lines of tests
- 19 size-based bucket management
- Thread-safe mechanisms (ConcurrentStack, Interlocked)
- Statistics tracking and monitoring

**Performance Characteristics**:
- ≤512B: 27% performance improvement
- ≥1KB: Minimal performance difference
- 64KB: 11% performance degradation
- Burst load: Severe performance degradation

**Decision**:
In real environments, various message sizes and variable load patterns exist. Performance gains in limited cases did not justify code complexity and maintenance costs.

### 3. Interop Is Expensive

Crossing the .NET ↔ native code boundary is more expensive than expected:

- P/Invoke: ~50ns
- GCHandle: ~100ns
- Callback: ~100ns
- Native allocation: ~100ns
- **Total: ~450ns**

Copying 64 bytes: **~1ns**

**450x difference.**

When possible, solving problems within .NET is preferable.

### 4. Code Removal Criteria

**Verification Process**:
1. Hypothesis formulation
2. Implementation and testing
3. Benchmark measurement
4. Real environment simulation
5. Cost-benefit analysis
6. Decision making

**Removal Criteria**:
- Measured performance gains < Complexity cost
- Effective only in limited use cases
- Vulnerable to load pattern changes

In this case, 4,185 lines of code were removed.

---

## Summary and Recommendations

### Net.ZMQ Memory Management Strategy

**Adopted Approach**:
```
Message size ≤ 512B:  ArrayPool<byte>.Shared (managed memory pooling)
Message size > 512B:  Message (native memory)
```

### Performance Measurement Summary

| Technique | Advantages | Disadvantages | Conclusion |
|-----------|------------|---------------|------------|
| **MessagePool** | 27% faster at ≤512B | No effect at ≥1KB, performance crash under burst load | Not adopted |
| **ZeroCopy** | Effective at ≥64KB | 2.43x slower at ≤512B | Not adopted |
| **ArrayPool** | Best performance at ≤512B | Slower than native memory for large messages | Adopted (small messages) |
| **Message** | Stable at all sizes | Slower than ArrayPool (small messages) | Adopted (default) |

### Practical Application Guide

**Which strategy is right for your project?**

#### 📊 Check Message Size Patterns

```csharp
// 1. First, check your actual message size distribution
var sizes = new List<int>();
for (int i = 0; i < 10000; i++)
{
    var msg = socket.Recv();
    sizes.Add(msg.Size);
}

var avg = sizes.Average();
var p95 = sizes.OrderBy(x => x).ElementAt((int)(sizes.Count * 0.95));
Console.WriteLine($"Average: {avg}B, P95: {p95}B");
```

#### ✅ Application Criteria

**Case 1: Small messages (average < 512B)**
```csharp
// ArrayPool recommended
var buffer = ArrayPool<byte>.Shared.Rent(size);
try
{
    socket.Send(buffer.AsSpan(0, size));
}
finally
{
    ArrayPool<byte>.Shared.Return(buffer);
}
```
- **Suitable for**: IoT sensor data, chat messages, event logs
- **Expected benefits**: 99.9% GC allocation reduction vs ByteArray, similar performance

**Case 2: Medium messages (512B ~ 64KB)**
```csharp
// Basic Message recommended
using var msg = new Message();
socket.Recv(ref msg);
ProcessData(msg.Data);
```
- **Suitable for**: JSON payloads, typical RPC calls
- **Characteristics**: Neither ArrayPool nor ZeroCopy provides benefits, simple approach is best

**Case 3: Large messages (> 64KB)**
```csharp
// Message or MessageZeroCopy
using var msg = new Message(largeData);
socket.Send(msg);
```
- **Suitable for**: File transfers, image/video data, large batch data
- **Expected benefits**: ~16% performance improvement vs ArrayPool

**Case 4: Mixed patterns (various sizes)**
```csharp
// Dynamic selection based on size
if (size <= 512)
{
    var buffer = ArrayPool<byte>.Shared.Rent(size);
    // Use ArrayPool
}
else
{
    var msg = new Message(size);
    // Use Message
}
```
- **Suitable for**: General-purpose messaging systems
- **Note**: Branch overhead < 1% (negligible)

#### ⚠️ What to Avoid

```csharp
// ❌ Bad Pattern 1: ZeroCopy for small messages
if (size < 100)  // Small message
{
    nint ptr = Marshal.AllocHGlobal(size);  // Actually slower (2.4x)
    // ...
}

// ❌ Bad Pattern 2: ByteArray for large messages
byte[] data = new byte[10_000_000];  // 10MB, heavy GC pressure
socket.Send(data);

// ❌ Bad Pattern 3: Custom pooling under burst load
// Custom pooling like MessagePool suffers from synchronization costs
// Performance degrades rapidly under high load
```

### Value of the Verification Process

Although MessagePool and ZeroCopy were not ultimately adopted, through this verification process:
- Quantitatively identified performance characteristics of each strategy
- Established optimal strategy based on message size
- Confirmed the impact of load patterns on performance

We hope this measurement data helps you make informed decisions in your projects.

### 📚 Additional Resources

- **Benchmark Code**: [GitHub - benchmarks/](https://github.com/ulalax/netzmq/tree/main/benchmarks)
- **Actual Measurement Data**: [BenchmarkDotNet Results](https://github.com/ulalax/netzmq/tree/main/benchmarks/Net.Zmq.Benchmarks/BenchmarkDotNet.Artifacts/results)
- **Net.ZMQ Documentation**: [Performance Guide](https://github.com/ulalax/netzmq/blob/main/docs/benchmarks.md)

If you want to measure it yourself:
```bash
git clone https://github.com/ulalax/netzmq
cd netzmq/benchmarks/Net.Zmq.Benchmarks
dotnet run -c Release
```

---

**December 2025**
