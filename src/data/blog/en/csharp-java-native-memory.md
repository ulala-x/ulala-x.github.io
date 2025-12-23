---
author: Ulala-X
pubDatetime: 2025-12-23T00:00:00+09:00
title: C# vs Java Native Memory Handling - The Decisive Difference Between Pin and Copy
slug: csharp-java-native-memory
featured: true
draft: false
lang: en
lang_ref: csharp-java-native-memory
tags:
  - csharp
  - java
  - native-memory
  - performance
  - benchmark
description: Comparing C#'s Zero-copy Pinning with Java's Mandatory Copy Mechanism
---

# C# vs Java Native Memory Handling - The Decisive Difference Between Pin and Copy

> Comparing C#'s Zero-copy Pinning with Java's Mandatory Copy Mechanism

**December 23, 2025**

---

## Why This Difference Matters

When communicating with native libraries or handling high-performance I/O, data transfer between managed memory and native memory is unavoidable. C# and Java use fundamentally different mechanisms:

- **C#**: Can pass managed arrays to native code via Zero-copy using the `fixed` keyword
- **Java**: No pinning capability - Heap → Native copying is mandatory

This difference directly impacts performance when frequently processing large volumes of data. This article provides a concrete comparison of memory handling approaches in both languages through benchmark data.

---

## Chapter 1: C# Native Memory Handling

C# provides several methods for handling native memory. Let's examine the performance characteristics of each through benchmarks.

### 1.1 Native Memory Allocation Methods

**Test Environment**:
- CPU: Intel Core Ultra 7 265K (20 cores)
- OS: Ubuntu 24.04.3 LTS
- Runtime: .NET 8.0.22
- BenchmarkDotNet v0.14.0

> **Time Unit Reference**
> - **μs (microsecond)**: 1μs = 0.000001 sec = 1/1,000,000 sec
> - **ns (nanosecond)**: 1ns = 0.000000001 sec = 1/1,000,000,000 sec
> - Note: 1ms (millisecond) = 1,000μs = 1,000,000ns

| Method | 64B | 1KB | 64KB | 1MB |
|--------|-----|-----|------|-----|
| StackAlloc | 11.7 μs | 12.1 μs | 30.0 μs | 106.1 μs |
| NativeMemory.Alloc | 63.6 μs | 61.5 μs | 61.2 μs | 61.4 μs |
| Marshal.AllocHGlobal | 74.3 μs | 72.6 μs | 82.8 μs | 86.3 μs |

**Key Insights**:

1. **StackAlloc** is fastest at 64B with 11.7 μs, but performance degrades as size increases (106.1 μs at 1MB).
2. **NativeMemory.Alloc** shows consistent performance regardless of size (approximately 61-63 μs). This demonstrates the stability of heap allocation mechanisms.
3. **Marshal.AllocHGlobal** is a legacy API and slowest across all sizes.

**Practical Guide**: Use StackAlloc for small sizes that can be safely allocated on the stack (< 1KB), otherwise use NativeMemory.Alloc.

**Specific usage examples for each allocation method**:

```csharp
// 1. StackAlloc - Allocate on stack memory
unsafe
{
    byte* buffer = stackalloc byte[1024];
    // Automatically freed when function exits
}

// 2. NativeMemory.Alloc - Allocate on native heap (.NET 6+)
unsafe
{
    byte* buffer = (byte*)NativeMemory.Alloc(1024);
    try {
        // Use
    } finally {
        NativeMemory.Free(buffer);
    }
}

// 3. Marshal.AllocHGlobal - Allocate on native heap (legacy)
IntPtr buffer = Marshal.AllocHGlobal(1024);
try {
    // Use
} finally {
    Marshal.FreeHGlobal(buffer);
}
```

**Memory allocation method characteristics comparison**:

| Method | Location | GC Managed | Deallocation | Characteristics |
|--------|----------|------------|--------------|-----------------|
| `stackalloc` | Stack | ❌ | Automatic (scope exit) | Fastest, size limited (max ~1MB, recommended < 1KB) |
| `NativeMemory.Alloc` | Native Heap | ❌ | Manual (`Free`) | Consistent performance, modern API |
| `Marshal.AllocHGlobal` | Native Heap | ❌ | Manual (`FreeHGlobal`) | Legacy, P/Invoke compatible |
| `new byte[]` | Managed Heap | ✅ | GC | Safest, GC overhead |

**C# process memory structure**:

```
┌─────────────────────────────────────────────────────────────┐
│                      Process Memory                          │
├─────────────────┬─────────────────┬─────────────────────────┤
│   Stack         │  Managed Heap   │     Native Heap         │
├─────────────────┼─────────────────┼─────────────────────────┤
│ • stackalloc    │ • new byte[]    │ • NativeMemory.Alloc    │
│ • Local vars    │ • GC managed    │ • Marshal.AllocHGlobal  │
│ • Auto freed    │ • Auto freed    │ • Manual free required  │
│ • ~1MB limit    │ • No size limit │ • No size limit         │
└─────────────────┴─────────────────┴─────────────────────────┘
```

### 1.2 C#'s Core Strength: Zero-copy Pinning

C#'s most powerful feature is Zero-copy transfer using the `fixed` keyword.

```csharp
byte[] managedArray = new byte[1024];

// Pin with fixed to prevent GC from moving memory
unsafe
{
    fixed (byte* ptr = managedArray)
    {
        // Pass pointer to native code without copying
        NativeLibrary.ProcessData(ptr, managedArray.Length);
    }
}
// Automatically unpinned when exiting fixed block
```

**Pinning Benchmark** (10,000 iterations):

| Method | 64B | 1KB | 64KB | 1MB |
|--------|-----|-----|------|-----|
| Fixed (pin only) | 5.8 μs | 5.8 μs | 26.4 μs | 28.9 μs |
| GCHandle.Pinned | 245.4 μs | 245.5 μs | 260.5 μs | 259.8 μs |
| MemoryMarshal (Span) | 4.2 μs | 4.3 μs | 4.5 μs | 4.6 μs |

**Key Insights**:

1. **MemoryMarshal.GetReference** with Span is fastest across all sizes at approximately 4.2-4.6 μs.
2. **fixed** keyword shows respectable performance at 5.8 μs for 64B~1KB.
3. **GCHandle.Pinned** is significantly slower at 245 μs. Explicit allocation/deallocation overhead is substantial.

### 1.3 Managed → Native Data Transfer Performance

We compared methods for transferring data from managed arrays to native memory.

**Benchmark Results** (10,000 iterations):

| Method | 64B | 1KB | 64KB | 1MB |
|--------|-----|-----|------|-----|
| Marshal.Copy | 11.2 μs | 13.8 μs | 106.1 μs | 1,330 μs |
| Buffer.MemoryCopy (fixed) | 12.7 μs | 16.1 μs | 176.1 μs | 2,146 μs |
| Span.CopyTo (native) | 11.2 μs | 15.8 μs | 166.4 μs | 2,104 μs |

**Key Insights**:

1. **For small sizes (64B)**, all methods are similar (approximately 11-13 μs).
2. **For 1MB data**, Marshal.Copy is fastest at 1,330 μs, while Buffer.MemoryCopy is 1.6× slower at 2,146 μs.
3. **If copying is necessary**, Marshal.Copy is the fastest. However, for read-only operations, pinning with fixed for zero-copy transfer as shown in Section 1.2 is optimal.

**Specific usage for each copy method**:

```csharp
byte[] managedArray = new byte[1024];
IntPtr nativePtr = Marshal.AllocHGlobal(1024);

// 1. Marshal.Copy - Managed → Native copy
Marshal.Copy(managedArray, 0, nativePtr, 1024);

// 2. Buffer.MemoryCopy - Copy after pinning with fixed
unsafe
{
    fixed (byte* src = managedArray)
    {
        Buffer.MemoryCopy(src, (void*)nativePtr, 1024, 1024);
    }
}

// 3. Span.CopyTo - Span-based copy
unsafe
{
    Span<byte> nativeSpan = new Span<byte>((void*)nativePtr, 1024);
    managedArray.AsSpan().CopyTo(nativeSpan);
}
```

**Internal operation comparison for copy methods**:

| Method | Internal Operation | Pinning Required | Advantages | Disadvantages |
|--------|-------------------|------------------|------------|---------------|
| `Marshal.Copy` | Internally pin + memcpy | Automatic | Fastest, simple, no unsafe needed | Requires IntPtr |
| `Buffer.MemoryCopy` | Direct memcpy call | Manual (fixed) | Flexible, direct pointer control | Requires unsafe block |
| `Span.CopyTo` | Internally memmove | Manual (fixed) | Modern API, type-safe | Slightly slower |

**Memory usage pattern comparison between copy and zero-copy methods**:

```
┌─────────────────────────────────────────────────────────────┐
│ [Copy Method] Marshal.Copy / Buffer.MemoryCopy               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Managed Heap          memcpy           Native Heap        │
│  ┌──────────┐    ──────────────────>   ┌──────────┐        │
│  │ byte[]   │      Data copied          │ IntPtr   │        │
│  └──────────┘                          └──────────┘        │
│                                                             │
│   → 2× memory usage, copy time required                    │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ [Zero-copy] fixed keyword                                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Managed Heap                                              │
│  ┌──────────┐                                               │
│  │ byte[]   │ ←── Pinned with fixed (GC cannot move)        │
│  └────┬─────┘                                               │
│       │                                                     │
│       └──→ Pointer directly passed to native code           │
│                                                             │
│   → 1× memory usage, zero copy time                        │
└─────────────────────────────────────────────────────────────┘
```

**Practical Application**:
```csharp
// Read-only operations: Zero-copy Pinning
unsafe
{
    fixed (byte* ptr = managedArray)
    {
        NativeLib.Read(ptr, length); // No copying
    }
}

// Write operations: Copying required
IntPtr nativePtr = Marshal.AllocHGlobal(size);
Marshal.Copy(managedArray, 0, nativePtr, size);
NativeLib.Write(nativePtr, size);
Marshal.FreeHGlobal(nativePtr);
```

---

## Chapter 2: Java Native Memory Handling

Since JDK 14, Java has improved native memory management through the Foreign Memory API (now Foreign Function & Memory API). However, there's a critical constraint: **Heap arrays cannot be pinned**.

### 2.1 Arena-based Memory Allocation

**Test Environment**:
- Java: OpenJDK 22.0.2
- JMH 1.37
- OS: Ubuntu 24.04.3 LTS
- CPU: Intel Core Ultra 7 265K

| Arena Type | 64B | 1KB | 64KB | 1MB |
|-----------|-----|-----|------|-----|
| Confined (thread-local) | 42 ns | 47 ns | 887 ns | 13,400 ns |
| Shared (thread-safe) | 31,628 ns | 32,578 ns | 34,144 ns | 53,592 ns |
| Global (permanent) | 88 ns | 630 ns | 35,520 ns | 559,392 ns |
| Auto (automatic) | 482 ns | 818 ns | 37,572 ns | 635,644 ns |

**Key Insights**:

1. **Confined Arena** is fastest across all sizes because it's single-threaded only.
2. **Shared Arena** is very slow at approximately 31 μs for small sizes due to synchronization overhead.
3. **For large sizes (1MB)**, all types show similar performance (approximately 13-635 μs).

**Comparison with C#**:
- C# NativeMemory.Alloc: 61-63 μs (size-independent)
- Java Confined Arena: 42 ns ~ 13.4 μs (size-dependent)

Java is faster for small sizes, but performance drops dramatically when using Shared Arena for thread safety.

**Usage examples for each Arena type**:

```java
// 1. Confined Arena - Single thread only, fastest
try (Arena arena = Arena.ofConfined()) {
    MemorySegment segment = arena.allocate(1024);
    // Only usable in this thread
} // Automatically freed

// 2. Shared Arena - Thread-safe
try (Arena arena = Arena.ofShared()) {
    MemorySegment segment = arena.allocate(1024);
    // Shareable across threads
} // Automatically freed

// 3. Global Arena - Permanent allocation
MemorySegment segment = Arena.global().allocate(1024);
// Persists until process termination, cannot be freed

// 4. Auto Arena - GC-based automatic management
Arena arena = Arena.ofAuto();
MemorySegment segment = arena.allocate(1024);
// No close() needed, GC manages
```

**Arena type characteristics comparison**:

| Arena Type | Thread-Safe | Deallocation | Performance | Use Case |
|-----------|-------------|--------------|-------------|----------|
| `Confined` | ❌ Single thread | try-with-resources | 🚀 Fastest | Local operations |
| `Shared` | ✅ Multi-threaded | try-with-resources | 🐢 Sync overhead | Cross-thread sharing |
| `Global` | ✅ Multi-threaded | Cannot free | ⚡ Fast | Constants, global data |
| `Auto` | ✅ Multi-threaded | GC automatic | 🔄 GC dependent | Unclear lifetime |

**Java process memory structure**:

```
┌─────────────────────────────────────────────────────────────┐
│                      JVM Process Memory                      │
├─────────────────────┬───────────────────────────────────────┤
│     Java Heap       │           Native Memory               │
├─────────────────────┼───────────────────────────────────────┤
│ • byte[] arrays     │ • MemorySegment (Arena)               │
│ • Object instances  │ • DirectByteBuffer internal buffer    │
│ • GC managed        │ • JNI native allocation               │
│                     │ • Released via Arena.close()          │
├─────────────────────┴───────────────────────────────────────┤
│  ⚠️ Java cannot pin Heap arrays → Copy mandatory for Native │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Java's Fundamental Constraint: Mandatory Heap → Native Copying

Java cannot pin heap arrays. Therefore, **copying is mandatory for all heap data transfer to native**.

**Heap → Native Copy Benchmark**:

| Method | 64B | 1KB | 64KB | 1MB |
|--------|-----|-----|------|-----|
| Heap → MemorySegment Copy | 44 ns | 52 ns | 1,825 ns | 39,434 ns |
| Heap → DirectBuffer Copy | 373 ns | 743 ns | 36,175 ns | 553,142 ns |
| Heap → Native (reusable) | 2.5 ns | 8.4 ns | 1,390 ns | 25,074 ns |

**Key Insights**:

1. **Using reusable buffers** significantly reduces copy overhead (25 μs at 1MB).
2. **DirectBuffer** is very slow when allocated each time (553 μs at 1MB).
3. **MemorySegment** is generally faster than DirectBuffer.

**Decisive Difference from C#**:

```java
// Java: Copying mandatory
byte[] heapArray = new byte[1024];
try (Arena arena = Arena.ofConfined()) {
    MemorySegment segment = arena.allocate(1024);
    MemorySegment.copy(heapArray, 0, segment, ValueLayout.JAVA_BYTE, 0, 1024); // Copy occurs
    nativeProcess(segment);
}
```

```csharp
// C#: Zero-copy possible
byte[] managedArray = new byte[1024];
unsafe {
    fixed (byte* ptr = managedArray) {
        NativeProcess(ptr); // No copying
    }
}
```

### 2.3 DirectByteBuffer vs MemorySegment

**Allocation Performance**:

| Method | 64B | 1KB | 64KB | 1MB |
|--------|-----|-----|------|-----|
| DirectByteBuffer.allocate | 367 ns | 708 ns | 33,489 ns | 578,204 ns |
| MemorySegment.allocate | 43 ns | 50 ns | 930 ns | 13,480 ns |

**MemorySegment is 42× faster than DirectByteBuffer** (at 1MB).

**Practical Guide**:
- Use MemorySegment instead of DirectByteBuffer unless working with legacy code
- Reuse Arena to minimize allocation overhead for frequent allocations

---

## Chapter 3: C# vs Java Direct Comparison

Now let's directly compare the performance of both languages. These results were measured in the same test environment.

### 3.1 Memory Allocation Performance Comparison

**64B Small Data Allocation**:

```
Fast ◀─────────────────────────────────────────────▶ Slow

Java Confined   ██ 0.042 μs
Java Global     ███ 0.088 μs
Java Auto       █████ 0.48 μs
C# StackAlloc   ██████████████████████████████ 11.7 μs
Java Shared     ███████████████████████████████████████████████████████████████ 31.6 μs
C# NativeAlloc  ███████████████████████████████████████████████████████████████████████████████████ 63.6 μs
C# Marshal      ██████████████████████████████████████████████████████████████████████████████████████████ 74.3 μs
```

**1MB Large Data Allocation**:

```
Fast ◀─────────────────────────────────────────────▶ Slow

Java Confined   ██ 13.4 μs
Java Shared     ████ 53.6 μs
C# NativeAlloc  █████ 61.4 μs
C# Marshal      ███████ 86.3 μs
C# StackAlloc   █████████ 106.1 μs
Java Global     ██████████████████████████████████████████████████████████████████████ 559 μs
Java Auto       ███████████████████████████████████████████████████████████████████████████████ 636 μs
```

| Size | C# Best Performance | Java Best Performance | Winner |
|------|--------------------|-----------------------|--------|
| 64B | StackAlloc (11.7 μs) | Confined (0.042 μs) | **Java 279× faster** |
| 1KB | StackAlloc (12.1 μs) | Confined (0.047 μs) | **Java 257× faster** |
| 64KB | StackAlloc (30.0 μs) | Confined (0.89 μs) | **Java 34× faster** |
| 1MB | NativeAlloc (61.4 μs) | Confined (13.4 μs) | **Java 4.6× faster** |

> **Insight**: Looking at allocation alone, Java's Confined Arena is overwhelmingly faster.
> However, this only compares "allocation" - actual native transfer requires additional copy costs.

### 3.2 Heap → Native Data Transfer Performance Comparison

Comparing the total cost of transferring heap data to native code.

**64B Small Data Transfer**:

```
Fast ◀─────────────────────────────────────────────▶ Slow

Java Reusable    █ 0.0025 μs (copy)
C# MemoryMarshal ████████████████████████████████████████████ 4.2 μs (Zero-copy)
C# Fixed         ██████████████████████████████████████████████████████████ 5.8 μs (Zero-copy)
C# Marshal.Copy  ██████████████████████████████████████████████████████████████████████████████████████████████████ 11.2 μs (copy)
Java Each Alloc  ████████████████████████████████████████ 0.044 μs (copy+alloc)
```

**1MB Large Data Transfer**:

```
Fast ◀─────────────────────────────────────────────▶ Slow

Java Reusable     ██ 25.1 μs (copy)
C# Fixed          ██ 28.9 μs (Zero-copy, pin only)
Java Each Alloc   ███ 39.4 μs (copy+alloc)
C# Marshal.Copy   █████████████████████████████████████████████████████████████████████████████████ 1,330 μs (copy)
```

| Size | C# Zero-copy (Fixed) | Java Reusable Buffer Copy | Notes |
|------|---------------------|---------------------------|-------|
| 64B | 5.8 μs | 0.0025 μs | Java 2,320× faster |
| 1KB | 5.8 μs | 0.0084 μs | Java 690× faster |
| 64KB | 26.4 μs | 1.39 μs | Java 19× faster |
| 1MB | 28.9 μs | 25.1 μs | **Nearly identical** |

> **Key Findings**:
> - **Small data (< 64KB)**: Java's reusable buffer copy is faster than C#'s Zero-copy!
> - **Large data (≥ 1MB)**: Both languages show nearly identical performance.
> - C#'s `Marshal.Copy` is the slowest across all sizes.

### 3.3 Why These Results?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    Performance Reversal Analysis                         │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  [Small Data < 64KB]                                                    │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ C# Fixed: GC pause + memory pinning overhead > actual copy cost  │   │
│  │ Java Copy: Only performs simple memcpy (very fast)               │   │
│  │                                                                   │   │
│  │ → For small data, copying is faster than pinning!                │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  [Large Data ≥ 1MB]                                                     │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ C# Fixed: Pinning overhead 28.9 μs (size-independent)            │   │
│  │ Java Copy: memcpy 25.1 μs (proportional to size)                 │   │
│  │                                                                   │
│  │ → As size grows, copy cost increases to match Zero-copy          │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  [Conclusion]                                                           │
│  • Under 64KB: Java reusable buffer is more efficient                  │
│  • 1MB+: C# Zero-copy and Java copy perform equally                    │
│  • 10MB+: C# Zero-copy starts showing advantages                       │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.4 Practical Selection Guide (By Size)

| Data Size | Recommended Language/Method | Reason |
|-----------|----------------------------|--------|
| < 1KB | Java or C# | Performance difference negligible (< 10μs) |
| 1KB ~ 64KB | Java (reusable buffer) | Copy faster than pin |
| 64KB ~ 1MB | Either works | Performance convergence zone |
| > 1MB | C# (Fixed) | Zero-copy advantage emerges |
| > 10MB | **C# (Fixed) strongly recommended** | Copy costs skyrocket |

---

## Chapter 4: Pin vs Copy - Architectural Differences

### 4.1 Architectural-Level Differences

| Feature | C# | Java |
|---------|-----|------|
| **Pinning Support** | ✅ `fixed`, `GCHandle` | ❌ Not possible |
| **Zero-copy Transfer** | ✅ Managed → Native direct | ❌ Copying mandatory |
| **GC Impact** | Memory immovable during pinning | Independent of GC (copies) |
| **Performance** | Zero copy cost | Copy cost proportional to size |
| **Safety** | Requires unsafe blocks | Type-safe |

### 4.2 Performance Impact Comparison

**Actual benchmark results for transferring 1MB data to native**:

#### Send Operation (Read-only data transfer)

| Language | Method | Time | Notes |
|----------|--------|------|-------|
| C# | **Send_ZeroCopy (fixed)** | **112 μs** | Pinning overhead only |
| Java | send_HeapCopyToNative (reusable) | 127 μs | Copy + native call |
| C# | Send_WithCopy | 126 μs | Copy + native call |
| Java | send_AllocateAndCopy (allocate each time) | 131 μs | Allocate + copy + call |

**Analysis**:
- C#'s Zero-copy is fastest at **112 μs**
- Java's reusable buffer copy at 127 μs is nearly identical to C#'s With-copy (126 μs)
- For 1MB data, **C# is approximately 13% faster** (with Zero-copy)

#### Transform Operation (Read-write data transformation)

| Language | Method | Time | Notes |
|----------|--------|------|-------|
| C# | **Transform_ZeroCopy (fixed)** | **20 μs** | Pin only, in-place transformation |
| C# | Transform_WithCopy | 46 μs | 2 copies (round-trip) |
| Java | transform_RoundTripCopy (reusable) | 65 μs | 2 copies (round-trip) |
| Java | transform_AllocateAndRoundTrip | 80 μs | Allocate + 2 copies |

**Analysis**:
- C#'s Zero-copy dominates at **20 μs**
- Java's reusable buffer copy at 65 μs is 1.4× slower than C#'s With-copy (46 μs)
- C# Zero-copy is **3.25× faster** than Java's optimized copying

**Key Insights**:

1. **Send Operation (read-only)**: C# and Java difference is small (~13%)
2. **Transform Operation (read-write)**: C#'s Zero-copy has decisive advantage (3.25× faster)
3. **Java's Constraint**: Cannot pin heap arrays, must copy
4. **C#'s Flexibility**: Can completely eliminate copy overhead with fixed keyword

### 4.3 Large Data Processing Scenario

**Scenario 1**: Send 1MB data to native 1,000 times (read-only)

| Language | Method | Total Time (Estimated) | Difference |
|----------|--------|------------------------|------------|
| C# | Send_ZeroCopy (fixed) | 112 ms | Baseline |
| Java | send_HeapCopyToNative (reusable) | 127 ms | +13% |
| C# | Send_WithCopy | 126 ms | +13% |

**Scenario 2**: Transform 1MB data 1,000 times (read-write)

| Language | Method | Total Time (Estimated) | Difference |
|----------|--------|------------------------|------------|
| C# | Transform_ZeroCopy (fixed) | 20 ms | Baseline (best performance) |
| C# | Transform_WithCopy | 46 ms | +130% |
| Java | transform_RoundTripCopy (reusable) | 65 ms | +225% |
| Java | transform_AllocateAndRoundTrip | 80 ms | +300% |

**Practical Implications**:
- **Read-only operations**: C# and Java difference is minimal (~13%)
- **Read-write operations**: C#'s Zero-copy has overwhelming advantage (3.25× faster)
- **For in-place data transformation by native library**: strongly recommend C#'s fixed keyword
- For Java, buffer reuse is essential. Allocating each time is 57% slower.

---

## Chapter 5: Practical Application Guide

### 5.1 C# Native Memory Usage Patterns

**Pattern 1: Passing Read-only Data to Native**

```csharp
// Pass to native via Zero-copy
byte[] imageData = LoadImage();

unsafe
{
    fixed (byte* ptr = imageData)
    {
        ProcessImageNative(ptr, imageData.Length);
    }
}
```

**Use Cases**:
- Image/video processing (OpenCV, FFmpeg, etc.)
- Cryptography libraries (OpenSSL, etc.)
- Network packet processing

**Pattern 2: Writable Native Buffer**

```csharp
// Allocate native buffer with NativeMemory.Alloc
unsafe
{
    byte* nativeBuffer = (byte*)NativeMemory.Alloc(1024);

    try
    {
        FillDataNative(nativeBuffer, 1024);

        // Copy result to managed
        byte[] result = new byte[1024];
        fixed (byte* dest = result)
        {
            Buffer.MemoryCopy(nativeBuffer, dest, 1024, 1024);
        }
    }
    finally
    {
        NativeMemory.Free(nativeBuffer);
    }
}
```

**Pattern 3: High-performance I/O with Span**

```csharp
// Zero-copy read/write with Span
Span<byte> buffer = stackalloc byte[512];

int bytesRead = socket.Receive(buffer);

unsafe
{
    fixed (byte* ptr = buffer)
    {
        ProcessData(ptr, bytesRead);
    }
}
```

### 5.2 Java Native Memory Usage Patterns

**Pattern 1: Minimize Allocation by Reusing Arena**

```java
// Pre-create and reuse Arena
private final Arena arena = Arena.ofShared();
private final MemorySegment reusableBuffer =
    arena.allocate(1024 * 1024); // 1MB buffer

public void processData(byte[] heapData) {
    // Copy to reusable buffer
    MemorySegment.copy(heapData, 0,
        reusableBuffer, ValueLayout.JAVA_BYTE, 0, heapData.length);

    nativeProcess(reusableBuffer);
}

// On shutdown
public void close() {
    arena.close();
}
```

**Pattern 2: Streaming Data Processing**

```java
// Process in chunks for memory efficiency
try (Arena arena = Arena.ofConfined()) {
    MemorySegment chunk = arena.allocate(4096);

    while (hasMoreData()) {
        byte[] heapChunk = readChunk();
        MemorySegment.copy(heapChunk, 0, chunk, ValueLayout.JAVA_BYTE, 0, heapChunk.length);
        processChunk(chunk);
    }
}
```

**Pattern 3: DirectByteBuffer Legacy Code**

```java
// Integration with legacy NIO code
ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024);
directBuffer.put(heapArray);
directBuffer.flip();

// Wrap as MemorySegment (Zero-copy)
MemorySegment segment = MemorySegment.ofBuffer(directBuffer);
nativeProcess(segment.address());
```

### 5.3 When to Choose Which Language

**Choose C# when**:

1. **Large read-only data processing**
   - Example: Processing 100MB images with native libraries
   - Reason: Zero-copy eliminates copy costs

2. **Frequent Managed ↔ Native transitions**
   - Example: 10,000 native function calls per second
   - Reason: Minimal overhead with fixed keyword

3. **Memory efficiency is critical**
   - Example: IoT devices, embedded systems
   - Reason: Memory reuse without copying

**Choose Java when**:

1. **Safety is paramount**
   - Example: Financial systems, medical systems
   - Reason: Type-safe API without unsafe

2. **Cross-platform consistency**
   - Example: Identical code on Linux/macOS/Windows
   - Reason: JVM abstracts platform differences

3. **Small data processing**
   - Example: Frequent native calls with < 1KB data
   - Reason: Copy overhead is negligible (< 100ns)

---

## Conclusion

### Key Summary

1. **C#'s Strength: Zero-copy Pinning**
   - Direct transfer of managed arrays to native with `fixed` keyword
   - Zero copy cost for large data
   - Requires unsafe code

2. **Java's Constraint: Mandatory Copying**
   - Cannot pin heap arrays
   - All data must be copied to native
   - Overhead minimizable through Arena reuse

3. **Performance Differences**
   - Small sizes (< 1KB): Both languages similar (< 100ns difference)
   - Large sizes (> 64KB): C# Zero-copy advantage
   - Optimized Java copying is still fast enough (25μs for 1MB)

4. **Practical Selection Criteria**
   - Read-only large data: C# advantage
   - Safety and type safety: Java advantage
   - Extreme performance optimization: C# advantage
   - Cross-platform consistency: Java advantage

### Final Recommendations

**For C# Developers**:
- Use `fixed` or `Span<T>` for Zero-copy when possible
- Small sizes: `stackalloc`, large sizes: `NativeMemory.Alloc`
- Use `Marshal.AllocHGlobal` only for legacy code

**For Java Developers**:
- Use MemorySegment instead of DirectByteBuffer unless legacy
- Reuse Arena to minimize allocation overhead
- Process large data in chunks for memory efficiency

Both languages provide modern and powerful native memory APIs. The key is understanding each language's characteristics and selecting the optimal pattern for your use case.

---

**Related Resources**:
- [C# NativeMemory API Documentation](https://learn.microsoft.com/en-us/dotnet/api/system.runtime.interopservices.nativememory)
- [Java Foreign Function & Memory API](https://openjdk.org/jeps/454)
- [Benchmark Source Code](https://github.com/ulala-x/ulala-x.github.io/tree/main/project/csharp-java-memory)
