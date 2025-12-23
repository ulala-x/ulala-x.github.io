# C# Native Memory Benchmarks

This directory contains comprehensive benchmarks comparing different native memory management approaches in C#.

## Benchmark Classes

### 1. AllocationBenchmarks.cs
**Purpose**: Compare different native memory allocation methods

**Methods Tested**:
- `MarshalAllocHGlobal()` - Traditional pre-.NET 6 approach (baseline)
- `NativeMemoryAlloc()` - Modern .NET 6+ uninitialized allocation
- `NativeMemoryAllocZeroed()` - Modern .NET 6+ zero-initialized allocation
- `StackAlloc()` - Stack-based allocation (fastest, limited size)
- `StackAllocZeroed()` - Stack allocation with explicit zeroing

**Key Parameters**:
- Size: 64B, 512B, 1KB, 4KB, 64KB, 1MB
- Iterations: 1,000, 10,000

**Key Insights**:
- `stackalloc` is extremely fast for small sizes (<4KB)
- `NativeMemory.Alloc` is the recommended approach for .NET 6+
- `AllocZeroed` has overhead but provides safety

---

### 2. PinningBenchmarks.cs
**Purpose**: Compare approaches to pin managed memory for native interop

**Methods Tested**:
- `FixedKeyword()` - Zero-copy pinning with `fixed` (baseline, recommended)
- `GCHandlePinned()` - Long-lived pinning with GCHandle
- `SpanWithMemoryMarshal()` - Modern Span-based approach
- `FixedWithMemoryCopy()` - Pin + native copy operation
- `SpanCopyTo()` - Pure managed copy (often fastest!)
- `FixedWithNativeCall()` - Realistic P/Invoke scenario
- `GCHandleLongLived()` - Single pin for multiple operations

**Key Parameters**:
- Size: 64B, 512B, 1KB, 4KB, 64KB, 1MB
- Iterations: 1,000, 10,000

**Key Insights**:
- `fixed` keyword is fastest for short-lived pinning
- `Span.CopyTo` is fastest when you don't need actual native interop
- GCHandle has allocation overhead but allows pointer to outlive scope
- This is where C# shines: zero-copy managed→native transitions

---

### 3. DataTransferBenchmarks.cs
**Purpose**: Benchmark realistic data transfer scenarios

**Scenarios**:

#### Managed-to-Managed
- `ManagedToManaged_ArrayCopy()` - Traditional Array.Copy (baseline)
- `ManagedToManaged_SpanCopy()` - Modern Span approach (often faster)
- `ManagedToManaged_BufferMemoryCopy()` - Unsafe approach

#### Managed-to-Native
- `ManagedToNative_MarshalCopy()` - Traditional Marshal.Copy
- `ManagedToNative_BufferMemoryCopy()` - High-performance approach (recommended)
- `ManagedToNative_SpanCopy()` - Modern safe approach

#### Native-to-Managed
- `NativeToManaged_MarshalCopy()` - Traditional approach
- `NativeToManaged_BufferMemoryCopy()` - High-performance approach

#### Native-to-Native
- `NativeToNative_BufferMemoryCopy()` - Fast native copy
- `NativeToNative_UnsafeCopyBlock()` - Modern .NET 6+ approach

#### Special Scenarios
- `ZeroCopy_FixedPointerPass()` - Theoretical minimum overhead
- `RoundTrip_ManagedNativeManaged()` - Complete round-trip scenario
- `BatchTransfer_MultipleManagedToNative()` - Aggregation pattern

**Key Parameters**:
- Size: 64B, 512B, 1KB, 4KB, 64KB, 1MB
- Iterations: 1,000, 10,000

**Key Insights**:
- Zero-copy (`fixed` pointer) is fastest when possible
- `Buffer.MemoryCopy` outperforms `Marshal.Copy` for larger buffers
- `Span.CopyTo` is excellent for managed-to-managed operations
- Round-trip scenarios show real-world P/Invoke overhead

---

## Running the Benchmarks

### Run All Benchmarks
```bash
cd /home/ulalax/project/ulalax/ulala-x.github.io/project/csharp-java-memory/csharp
dotnet run -c Release --project src/NativeMemory.Benchmarks/NativeMemory.Benchmarks.csproj
```

### Run Specific Benchmark Class
```bash
dotnet run -c Release --project src/NativeMemory.Benchmarks/NativeMemory.Benchmarks.csproj -- --filter *AllocationBenchmarks*
dotnet run -c Release --project src/NativeMemory.Benchmarks/NativeMemory.Benchmarks.csproj -- --filter *PinningBenchmarks*
dotnet run -c Release --project src/NativeMemory.Benchmarks/NativeMemory.Benchmarks.csproj -- --filter *DataTransferBenchmarks*
```

### Run Specific Method
```bash
dotnet run -c Release --project src/NativeMemory.Benchmarks/NativeMemory.Benchmarks.csproj -- --filter *FixedKeyword*
```

### Export Results
```bash
dotnet run -c Release --project src/NativeMemory.Benchmarks/NativeMemory.Benchmarks.csproj -- --exporters json html
```

---

## Expected Performance Characteristics

### Allocation (AllocationBenchmarks)
```
stackalloc < NativeMemory.Alloc < NativeMemory.AllocZeroed < Marshal.AllocHGlobal
```

### Pinning (PinningBenchmarks)
```
fixed < Span.CopyTo < SpanWithMemoryMarshal < GCHandle.Alloc
```

### Data Transfer (DataTransferBenchmarks)
```
Zero-copy < Buffer.MemoryCopy < Span.CopyTo < Marshal.Copy < Array.Copy
```

---

## Architecture Highlights

These benchmarks demonstrate C#'s unique advantages:

1. **Zero-Copy Transitions**: `fixed` keyword allows passing managed memory to native code without copying
2. **Stack Allocation**: `stackalloc` provides heap-free allocation for small buffers
3. **Modern APIs**: `Span<T>` and `NativeMemory` provide safe, high-performance abstractions
4. **Flexible Pinning**: Choose between short-lived (`fixed`) and long-lived (`GCHandle`) pinning
5. **Compiler Optimizations**: JIT compiler optimizes `Span.CopyTo` to use SIMD instructions

---

## Comparison with Java

Unlike Java's JNI which requires:
- Explicit `GetPrimitiveArrayCritical` / `ReleasePrimitiveArrayCritical`
- Manual copy tracking (`isCopy` flag)
- GC pause during critical sections

C# offers:
- Automatic pinning with `fixed` keyword
- True zero-copy with pointer semantics
- No GC pause (just prevents object relocation)
- Modern `Span<T>` for safe high-performance code

This makes C# significantly more efficient for native interop scenarios.
