---
author: Ulala-X
pubDatetime: 2025-12-20T00:00:00+09:00
title: I Deleted 697 Lines and Got 16% Faster
slug: en/netzmq-memory-optimization
featured: true
draft: false
tags:
  - zeromq
  - netzmq
  - performance
  - optimization
  - csharp
description: A confession about "obvious optimizations" that made everything slower
---

# I Deleted 697 Lines and Got 16% Faster

> A confession about "obvious optimizations" that made everything slower

**December 20, 2025** / Commits: [32b4ee2](https://github.com/ulalax/netzmq/commit/32b4ee2), [d122e62](https://github.com/ulalax/netzmq/commit/d122e62)

---

## How It Started

Working on NetZMQ, I implemented two "obvious" optimizations:

1. **MessagePool** - Pre-allocate native memory and reuse it. Obviously faster, right?
2. **ZeroCopy** - Skip the copy. No copy = faster, right?

I finished the 697-line MessagePool implementation. Wrote 1,753 lines of tests. All tests passed. Now just run the benchmarks and watch the performance soar. Maybe 2x faster?

Ran BenchmarkDotNet.

```
MessagePool: 7-16% slower
ZeroCopy: 2.43x slower on small messages
```

...wait, what?

This is the story of how my "obvious optimizations" made everything worse, and how I ended up deleting 4,185 lines of code.

---

## Chapter 1: MessagePool, or "This Will Obviously Be Faster"

### The Idea That Made Too Much Sense

The thinking was simple:

> Allocating and freeing native memory is expensive. So pre-allocate and reuse. Duh.

Object pooling is Performance 101. .NET even has ArrayPool built-in. A pool for native memory? Should be a no-brainer.

So I started implementing. Properly:

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

### The Journey Down the Rabbit Hole

As I implemented it, the code kept growing with "oh, I need to handle this" and "wait, what about that":

- **f195d1c**: Initial implementation (+200 lines) - "Easy!"
- **78816d9**: Double-free bug found... fixed (+500 lines) - "Hmm..."
- **45ac578**: Message object reuse (+100 lines) - "More optimization!"
- **cb81713**: Need ActualSize tracking (+150 lines) - "Okay..."
- **e8527f8**: Thread-safety with Interlocked (+80 lines) - "Right..."
- **d122e62**: Comprehensive tests (+1,753 lines) - "Perfect!"

**Final tally**:
- MessagePool.cs: **697 lines**
- MessagePoolTests.cs: **1,753 lines**
- My confidence: **100%**

All tests green. No memory leaks. Code reviewed.

Time to run the benchmarks. I was expecting at least 30% improvement.

### The Benchmark: "Wait, Something's Wrong"

```bash
$ dotnet run -c Release --filter "*ReceivePoolProfilingTest*"
```

Grabbed coffee. Waited confidently for the beautiful performance numbers.

Results came in:

| Message Size | Regular Receive | ReceiveWithPool | Difference |
|--------------|-----------------|-----------------|------------|
| **64B** | 2.19 ms (4.57M msg/sec) | 2.35 ms (4.25M msg/sec) | **+7.5% slower** |
| **1KB** | 7.54 ms (1.33M msg/sec) | 8.75 ms (1.14M msg/sec) | **+16.1% slower** |
| **64KB** | 139.9 ms (71.5K msg/sec) | 150.5 ms (66.5K msg/sec) | **+7.6% slower** |

...huh?

Ran it again thinking the benchmark was wrong. Same results.

Checked compilation flags. `-c Release`. Correct.

Cleared cache and rebuilt. Same results.

**My 697-line optimization made everything 7-16% slower.**

All tests passed, but performance tanked. How is this even possible?

### Root Cause Analysis: What Went Wrong

Spent days digging through code and profiling. The problems were pretty clear.

And I found my biggest wrong assumption:

> **"Reducing allocations will obviously make it faster"**

Generally true. Memory allocation (malloc/new) is slower than copying. That's why pooling works.

But this case was different:

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

**Cost analysis**:
- **≤512B**: Allocation savings >> Copy cost → **Pool clearly faster** (27% at 64B) ✓
- **1KB**: Allocation savings ≈ Copy cost → **About the same** (Pool or not, no difference)
- **64KB**: Allocation savings << Copy cost → **Pool 11% slower** ✗

Copy cost scales with message size. Beyond 512B, copy cost starts catching up with allocation savings.

Measured copy-only overhead in benchmarks:

| Message Size | SpanCopy Overhead | % of Total |
|--------------|------------------|------------|
| **64B** | 10.7 μs | 0.3% |
| **1KB** | 105.3 μs | 1.4% |
| **64KB** | 9,506 μs | **6.4%** |

Copy is negligible for small messages, but **takes 6.4% of total time at 64KB**.

Why did we need to copy?

ZeroMQ's `zmq_recv()` allocates memory internally when receiving messages. We can't control it. So:
1. Pre-allocate a large buffer (4MB)
2. Receive into it
3. Rent from pool with actual size
4. Copy

Advantage: Can bypass ZeroMQ allocation. Disadvantage: Copy is mandatory.

For small messages, "ZeroMQ allocation savings > copy cost" is profitable, but as messages grow, copy cost dominates.

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

### The Decision: Pressing Delete

Stared at the code for a while.

"But... it was clearly faster for ≤512B messages..."
"Small messages showed real improvements..."
"Maybe I'll need it later?"

But thinking objectively:
- **≤512B**: Pool clearly faster (27% improvement)
- **≥1KB**: Pool or not, about the same
- **64KB**: Pool 11% slower (copy cost)

And the critical issue: **Burst Testing**

When testing send/receive more aggressively (burst pattern), Pool version's performance dropped significantly. ConcurrentStack synchronization cost and cross-thread cache misses became much more severe under load.

Conclusion:
- Only benefits small messages
- Actually unstable under load
- Significantly increased complexity

From multiple perspectives, just using Message is better.

December 20th, wrote the commit message:

```bash
$ git commit -m "Remove MessagePool and simplify memory strategies"
# 14 files changed, 271 insertions(+), 4185 deletions(-)
```

**Deleted 4,185 lines.**

It worked for small messages, but real-world usage has larger messages. The complexity wasn't justified by the gains.

Sometimes deleting code is harder than writing it. Especially code you spent a week on.

---

## Chapter 2: "Just Skip The Copy" - Second Mistake

### The Temptation of Zero-Copy

After deleting MessagePool, had a thought:

> "Right, the problem was copying. So just... don't copy?"

Zero-Copy. Even the name sounds good. Zero copies. Can't argue with zero.

The concept is simple:

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
- Huge performance gains for large messages

### The Benchmarks: Deja Vu

Learned my lesson this time. Implemented it, ran benchmarks immediately.

#### 64B Messages

| Strategy | Throughput | Allocated |
|----------|------------|-----------|
| **ArrayPool** | **4.12M msg/sec** | 1.85 KB |
| ByteArray | 4.10M msg/sec | 9860 KB |
| Message | 2.34M msg/sec | 168 KB |
| **MessageZeroCopy** | **1.69M msg/sec** | 168 KB |

...what?

**Zero-copy was the slowest.** By **2.43x** compared to ArrayPool.

The thing with "Zero" in its name came in last place.

#### 512B Messages

Still slowest. **2.1x slower** than ArrayPool.

#### 1KB Messages

Still slowest. **1.63x slower**.

#### 64KB Messages (Finally!)

Tried large messages just in case:

| Strategy | Throughput | Allocated |
|----------|------------|-----------|
| **Message** | **83.9K msg/sec** | 171 KB |
| MessageZeroCopy | 80.2K msg/sec | 171 KB |
| ArrayPool | 70.0K msg/sec | 4.78 KB |
| ByteArray | 70.6K msg/sec | 4GB! |

**Finally!** At 64KB, native memory beat ArrayPool by **16%**.

Okay, so zero-copy isn't completely useless.

### Why? No Copy Should Be Faster, Right?

Spent a while thinking about this. No copying should obviously be faster, right?

Turns out **"not copying" also has a cost**.

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

#### 3. Let's Do The Math

Time to copy 64 bytes:
- About **10ns**

Time saved with ZeroCopy:
- No copy, so **10ns**

Time spent to enable ZeroCopy:
- P/Invoke, GCHandle, Callback, etc... **450ns**

**Net loss: 440ns**

We saved 10ns by not copying, but spent 450ns to make it happen.

This is the zero-copy paradox.

So when does it break even? Math says around 2-3KB, but actual benchmarks show **64KB**. Why?

#### 4. Additional Factors

- **CPU cache**: Small data stays in L1/L2 cache, copy is super fast
- **.NET JIT**: Managed memory copy gets SIMD optimizations
- **GCHandle cost**: More expensive than expected (~100ns)
- **Callback overhead**: Crossing managed-unmanaged boundary costs

### .NET Is Faster Than You Think

What I learned:

> **The .NET team spent 20+ years optimizing managed memory. It's no joke.**

Just look at ArrayPool:
- Lock-free (mostly)
- Per-thread caching
- SIMD-optimized copying
- Cache-friendly design

Our "zero-copy":
- P/Invoke bouncing
- GCHandle juggling
- Callback marshalling
- Native memory allocation

**The verdict**:
- Small messages (≤512B): .NET managed memory dominates
- Large messages (≥64KB): Native memory barely wins

Don't fight the framework.

---

## Final Architecture: The Victory of Simplicity

Conclusion from MessagePool deletion and ZeroCopy benchmarks:

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

## Lessons Learned

### 1. Be Careful With "Obviously"

My wrong "obvious" assumptions:
- "Reducing allocations is obviously faster" → Only true for ≤512B. No difference from 1KB, slower at 64KB
- "Pooling is obviously faster" → Completely depends on message size and load pattern
- "Zero-copy is obviously faster" → 2.43x slower
- "Native is obviously faster than managed" → ArrayPool was faster

Before benchmarks, they all sounded right.

**Biggest mistake**:
- The general rule (allocation > copy) is correct
- That's why pooling actually worked for small messages (≤512B)
- But **copy cost scales with message size**
- 64B: 10.7μs → 1KB: 105.3μs → 64KB: 9,506μs
- Beyond 512B, copy cost catches up with allocation savings
- And **under burst load, synchronization cost explodes**

**Lessons**:
- Don't optimize just one part without calculating total cost
- Test not just normal load but also maximum load scenarios

Now I think first: "Really? Did you measure it?"

### 2. Don't Underestimate Framework Optimizations

What the .NET team has optimized over 20+ years:

- **ArrayPool**: Lock-free, thread-local caching, SIMD
- **GC**: Generational collection, LOH, compaction
- **JIT**: Runtime optimization, inlining

ArrayPool was faster than my custom native pooling. Framework-provided optimized tools are usually superior to custom implementations in most cases.

Use proven tools first, and only consider custom implementations when bottlenecks are actually measured.

### 3. Code Is A Liability, Not An Asset

MessagePool:
- 697 lines implementation
- 1,753 lines tests
- 19 buckets
- ConcurrentStack
- Interlocked counters
- Statistics tracking
- Various bug fixes

**Result**:
- Clearly faster at ≤512B (good!)
- No difference at ≥1KB (hmm...)
- 11% slower at 64KB (bad!)
- Performance crash under burst load (critical!)

Real-world usage has many large messages and bursty load patterns. Complexity wasn't justified.

Deleted 4,185 lines, code got simpler and large message performance improved. More code ≠ better.

### 4. Interop Is Expensive

Crossing .NET ↔ native boundary is way more expensive than you think:

- P/Invoke: ~50ns
- GCHandle: ~100ns
- Callback: ~100ns
- Native alloc: ~100ns
- **Total: ~450ns**

Copying 64 bytes: **~10ns**

**45x difference.**

Stay in .NET when possible.

### 5. Deleting Takes Courage Too

Measure performance → Bad → Delete code

That's the answer, but pressing Delete isn't easy.

Especially on code you spent a week on.

But sometimes deleting code is the better choice. This time I deleted 4,185 lines and got 16% faster.

---

## The End

Started this project wanting to add "optimizations".

Ended it by deleting "optimizations".

**What I Deleted**:
- MessagePool 697 lines
- Tests 1,753 lines
- 4 benchmark classes
- Complex bucket logic
- My pride

**What I Got**:
- 16% performance improvement
- Simpler code
- One lesson

**Final Verdict**:

```
Small messages (≤512B): Use ArrayPool<byte>.Shared
Large messages (>512B):  Use Message/MessageZeroCopy
```

Simple.

And next time someone says "this will obviously be faster", I'll ask:

> "Really? Did you benchmark it?"

---

December 2025

*"Measure, learn, and sometimes delete."*
