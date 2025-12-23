---
author: Ulala-X
pubDatetime: 2025-12-23T00:00:00+09:00
title: Net.ZMQ Memory Optimization - MessagePool and ZeroCopy Verification
slug: netzmq-memory-optimization
featured: true
draft: false
lang: en
lang_ref: netzmq-memory-optimization
tags:
  - zeromq
  - netzmq
  - performance
  - optimization
  - csharp
description: Performance Optimization Verification - Benchmark Data-Driven Analysis
---

# Net.ZMQ Memory Optimization - MessagePool and ZeroCopy Verification

> Verifying Performance Optimization Techniques: Benchmark Data-Driven Analysis

**December 23, 2025**

---

## Overview

ZeroMQ is a high-performance asynchronous messaging library capable of processing millions of messages per second. When using such a high-performance library in .NET, developers naturally consider two optimizations:

> "Wouldn't memory pooling reduce allocation overhead?"
> "Wouldn't ZeroCopy eliminate copy costs?"

These optimizations seem theoretically obvious. This article shares the results of actually implementing and measuring these two optimization techniques.

### Verified Optimization Techniques

1. **MessagePool**: Reducing allocation overhead through native memory pooling
2. **ZeroCopy**: Performance improvement through memory copy elimination

### Conclusion Summary

```
MessagePool: 2.5x faster in allocation-only tests, but only 10-16% improvement in actual Send/Recv
ZeroCopy: 2.47x slower for small messages, effective from 64KB onwards
Final Decision: Not adopted due to insufficient benefits relative to complexity
```

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
}
```

**Core Mechanism**:

1. **Bucket allocation**: Round up requested size to nearest power of 2
2. **Reusable Message**: Initialize `zmq_msg_t` once with bucket size
3. **Automatic return**: Auto-return to pool via ZeroMQ's free callback
4. **Thread-safe**: ConcurrentStack + Interlocked counters

### Benchmark 1: Pure Allocation Performance (Excluding I/O)

First, we measured pure memory allocation without Send/Recv.

**Test Environment**:
- CPU: Intel Core Ultra 7 265K (20 cores)
- OS: Ubuntu 24.04.3 LTS
- Runtime: .NET 8.0.22
- BenchmarkDotNet v0.14.0

| Size | NewMessage | PoolRent (Size Only) | PoolRent (With Data Copy) | Ratio |
|------|------------|----------------------|---------------------------|-------|
| **64B** | 170 μs | 70 μs | 73 μs | **0.41x (2.4x faster)** |
| **512B** | 170 μs | 68 μs | 82 μs | **0.40x (2.5x faster)** |
| **1KB** | 200 μs | 68 μs | 78 μs | **0.35x (2.9x faster)** |
| **64KB** | 200 μs | 73 μs | 1,019 μs | Copy cost explosion |
| **1MB** | 204 μs | 70 μs | 211,031 μs | Copy cost extreme |

**Observations**:
- **PoolRent (Size Only)**: **2.5-3x faster** at all sizes
- **PoolRent (With Data Copy)**: Copy cost explodes at 64KB and above

**Conclusion**: Memory pooling itself is effective!

### Benchmark 2: Actual Send/Recv Cycles

However, when measured in actual ZeroMQ send/receive environment, results differ.

| Strategy | 64B | 512B | 1KB | 64KB |
|----------|-----|------|-----|------|
| **ByteArray** (Baseline) | 2.51 ms | 6.99 ms | 8.64 ms | 155 ms |
| **ArrayPool** | 2.59 ms (1.03x) | 6.65 ms (0.95x) | 8.77 ms (1.02x) | 147 ms (0.95x) |
| **Message** | 5.48 ms (2.18x) | 7.08 ms (1.01x) | 8.89 ms (1.03x) | **123 ms (0.79x)** |
| **MessagePooled** (Send only) | 4.47 ms (1.78x) | **5.84 ms (0.84x)** | **7.79 ms (0.90x)** | 136 ms (0.88x) |
| **MessagePooled+RecvPool** | 5.55 ms (2.21x) | 6.94 ms (0.99x) | 8.60 ms (1.00x) | 146 ms (0.94x) |

**Observations**:
- **64B**: MessagePooled is **78% slower** (2.4x faster in allocation, but why?)
- **512B~1KB**: MessagePooled is **10-16% faster**
- **64KB**: Message is fastest (21% improvement)

### Root Cause Analysis: Why Slower for Small Messages?

If Pool is 2.5x faster in allocation-only tests, why is it slower in actual Send/Recv?

#### Additional Overhead Analysis

Using MessagePool adds the following overheads:

```csharp
// Additional operations when using MessagePool
public Message Rent(ReadOnlySpan<byte> data)
{
    // 1. Calculate bucket index
    var bucketIndex = GetBucketIndex(size);

    // 2. Pop from ConcurrentStack (synchronization cost)
    if (_pooledMessages[bucketIndex].TryPop(out var msg))
    {
        Interlocked.Decrement(ref _pooledMessageCounts[bucketIndex]);
        // ...
    }

    // 3. Copy data
    Buffer.MemoryCopy(srcPtr, (void*)msg.DataPtr, actualSize, actualSize);

    // 4. Set actual size
    msg.SetActualDataSize(actualSize);

    return msg;
}
```

And during auto-return after Send:

```csharp
// Called from ZeroMQ free callback
private void ReturnMessageToPool(Message msg)
{
    // GCHandle management
    // ConcurrentStack Push (synchronization cost)
    // Interlocked counter updates
}
```

**Cost Analysis**:
- ConcurrentStack Pop/Push: ~50ns
- Interlocked operations: ~20ns × multiple times
- GCHandle management: ~100ns
- Bucket index calculation: ~10ns

**For small messages (64B), this overhead cancels out and exceeds pooling benefits.**

At 512B and above, native memory allocation cost is significant enough that pooling benefits outweigh the overhead.

### Decision: Why MessagePool Was Removed

**Performance Improvement vs Complexity Cost**:

| Size | Improvement | Complexity Cost |
|------|-------------|-----------------|
| 64B | **-78% (slower)** | 697 lines of implementation |
| 512B | +16% | 1,753 lines of tests |
| 1KB | +10% | ConcurrentStack management |
| 64KB | +12% | GCHandle lifecycle management |

What must be maintained for **10-16% performance improvement**:
- Native memory pool management code
- GCHandle lifecycle management
- Per-bucket ConcurrentStack synchronization
- Memory leak risks
- Complex debugging

**Meanwhile, ArrayPool**:
- Built into .NET (zero maintenance cost)
- 99.9% reduction in GC allocation
- Performance nearly identical to ByteArray

**Final Decision**:
Decided to remove MessagePool as benefits did not justify complexity.

```bash
$ git commit -m "Remove MessagePool and simplify memory strategies"
# 14 files changed, 271 insertions(+), 4185 deletions(-)
```

---

## Chapter 2: ZeroCopy - Memory Copy Elimination Verification

### Background: Theoretical Benefits of ZeroCopy

MessagePool analysis confirmed that copy cost for large messages is a bottleneck:

| Size | Copy Cost |
|------|-----------|
| 64B | ~3 μs |
| 512B | ~14 μs |
| 64KB | **~946 μs** |
| 1MB | **~211 ms** |

**Hypothesis**: Eliminating memory copy will improve performance for large messages.

### ZeroCopy Concept

```csharp
// Normal way: Data copying occurs
var msg = new Message(data);  // Copy data into Message
socket.Send(msg);

// ZeroCopy way: Pass native memory directly
nint ptr = Marshal.AllocHGlobal(size);
// Write data directly to ptr
using var msg = new Message(ptr, size, p => Marshal.FreeHGlobal(p));
socket.Send(msg);  // Pass pointer only, no copy!
```

### Benchmark Results

| Size | ByteArray | Message | MessageZeroCopy | ZeroCopy Ratio |
|------|-----------|---------|-----------------|----------------|
| **64B** | 2.51 ms | 5.48 ms | 6.20 ms | **2.47x slower** |
| **512B** | 6.99 ms | 7.08 ms | 8.12 ms | **1.16x slower** |
| **1KB** | 8.64 ms | 8.89 ms | 11.83 ms | **1.37x slower** |
| **64KB** | 155 ms | **123 ms** | 130 ms | 0.84x (16% faster) |

**Observation**: ZeroCopy is slower than Message at all sizes!

### Root Cause Analysis: ZeroCopy Overhead

Why is it slower despite eliminating copy?

#### 1. P/Invoke Is More Expensive Than Expected

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

#### 2. Copy Cost Is Smaller Than Expected

Time to copy 64 bytes: **~1ns**

Time saved with ZeroCopy: **1ns**
Time spent to enable ZeroCopy: **450ns**

**Net loss: 449ns**

#### 3. Why Is It Effective at 64KB?

64KB copy cost: ~946 μs
ZeroCopy overhead: ~450ns

**Finally, copy cost exceeds overhead!**

However, plain Message is also fastest at 64KB (123ms). ZeroMQ's internal optimization is already well done.

---

## Final Architecture: Simplicity Wins

### Recommended Strategy

```
Message size ≤ 1KB:   ArrayPool<byte>.Shared (managed memory pooling)
Message size > 64KB:  Message (native memory, ZMQ internal optimization)
```

### Performance Summary Table

| Message Size | Recommended Strategy | Reason |
|--------------|---------------------|--------|
| **≤512B** | ArrayPool | Fastest, 99.9% GC reduction |
| **1KB** | ArrayPool or Message | Nearly identical |
| **≥64KB** | Message | 21% faster (ZMQ internal optimization) |

### Implementation Example

```csharp
// Small messages: Use ArrayPool
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

// Large messages: Use Message
using var msg = new Message(largeData);
socket.Send(msg);
```

---

## Core Findings

### 1. Pooling Effect Actually Exists

MessagePool's pure allocation performance is 2.5-3x faster. But in real usage:
- Additional overhead (GCHandle, ConcurrentStack, callbacks)
- Overhead cancels benefits for small messages
- Complex code maintenance for 10-16% improvement

### 2. Copy Cost Scaling

| Size | Copy Cost | Relative Impact |
|------|-----------|-----------------|
| 64B | ~3 μs | Negligible |
| 512B | ~14 μs | Small |
| 64KB | ~946 μs | Significant |
| 1MB | ~211 ms | Dominant |

For small messages, copy cost is nearly zero. ZeroCopy overhead is much larger.

### 3. Interop Is Expensive

Cost of crossing .NET to native code boundary:
- P/Invoke: ~50ns
- GCHandle: ~100ns
- Callback marshalling: ~100ns

Copying 64 bytes: **~1ns**

**When possible, solving problems within .NET is preferable.**

### 4. Complexity vs Performance Trade-off

| Technique | Implementation Complexity | Performance Improvement | Conclusion |
|-----------|--------------------------|------------------------|------------|
| MessagePool | High (2,450 lines) | 10-16% | Not adopted |
| ZeroCopy | Medium | -16% ~ +16% | Not adopted |
| ArrayPool | None (.NET built-in) | Equivalent | **Adopted** |
| Message | Low | Optimal for 64KB+ | **Adopted** |

---

## Summary and Recommendations

### Adopted Strategy

```
Small messages (≤1KB):  ArrayPool<byte>.Shared
Large messages (≥64KB): Message
```

### Non-Adopted Techniques and Reasons

| Technique | Reason |
|-----------|--------|
| **MessagePool** | 2,450 lines of code for 10-16% improvement. Insufficient benefit for complexity |
| **ZeroCopy** | 2.5x slower for small messages. Interop overhead exceeds copy cost |

### Value of the Verification Process

Although MessagePool and ZeroCopy were not ultimately adopted, through this verification:
- Quantitatively identified performance characteristics of each strategy
- Established optimal strategy based on message size
- Confirmed the danger of assumptions like "it should be faster"

**Don't optimize without benchmarks.**

### Try It Yourself

The benchmark code is included in this blog's GitHub repository:

```bash
git clone https://github.com/ulala-x/ulala-x.github.io
cd ulala-x.github.io/project/net-zmq
./benchmarks/run-benchmarks.sh memory
```

Or test from the original repository:

```bash
git clone https://github.com/ulala-x/net-zmq
cd net-zmq
git checkout feature/message-pool
./benchmarks/run-benchmarks.sh memory
```

---

**December 2025**
