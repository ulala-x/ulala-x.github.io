using System;
using System.Runtime.InteropServices;
using BenchmarkDotNet.Attributes;

namespace NativeMemory.Benchmarks.Benchmarks;

/// <summary>
/// Compares different native memory allocation methods in C#.
/// Tests traditional Marshal.AllocHGlobal vs modern NativeMemory.Alloc vs stack allocation.
/// </summary>
[MemoryDiagnoser]
public class AllocationBenchmarks
{
    /// <summary>
    /// Size of memory to allocate in bytes.
    /// </summary>
    [Params(64, 1024, 65536, 1048576)]
    public int Size { get; set; }

    /// <summary>
    /// Number of allocation/deallocation iterations.
    /// </summary>
    [Params(10000)]
    public int Iterations { get; set; }

    /// <summary>
    /// Traditional method using Marshal.AllocHGlobal (pre-.NET 6).
    /// Allocates uninitialized native memory.
    /// </summary>
    [Benchmark(Baseline = true)]
    public void MarshalAllocHGlobal()
    {
        for (int i = 0; i < Iterations; i++)
        {
            IntPtr ptr = Marshal.AllocHGlobal(Size);

            // Simulate some work - write a byte
            Marshal.WriteByte(ptr, 0, 42);

            Marshal.FreeHGlobal(ptr);
        }
    }

    /// <summary>
    /// Modern method using NativeMemory.Alloc (.NET 6+).
    /// Allocates uninitialized native memory using standard C malloc semantics.
    /// This is the recommended approach for new code.
    /// </summary>
    [Benchmark]
    public unsafe void NativeMemoryAlloc()
    {
        for (int i = 0; i < Iterations; i++)
        {
            void* ptr = System.Runtime.InteropServices.NativeMemory.Alloc((nuint)Size);

            // Simulate some work - write a byte
            ((byte*)ptr)[0] = 42;

            System.Runtime.InteropServices.NativeMemory.Free(ptr);
        }
    }

    /// <summary>
    /// Modern method using NativeMemory.AllocZeroed (.NET 6+).
    /// Allocates and initializes native memory to zero (like C calloc).
    /// Slightly slower than Alloc due to initialization, but safer.
    /// </summary>
    [Benchmark]
    public unsafe void NativeMemoryAllocZeroed()
    {
        for (int i = 0; i < Iterations; i++)
        {
            void* ptr = System.Runtime.InteropServices.NativeMemory.AllocZeroed((nuint)Size);

            // Simulate some work - write a byte
            ((byte*)ptr)[0] = 42;

            System.Runtime.InteropServices.NativeMemory.Free(ptr);
        }
    }

    /// <summary>
    /// Stack allocation using stackalloc keyword.
    /// Extremely fast as it allocates on the stack (no heap allocation).
    /// Only suitable for small sizes (typically &lt; 1KB) to avoid stack overflow.
    /// Memory is automatically freed when method returns.
    /// </summary>
    [Benchmark]
    public unsafe void StackAlloc()
    {
        // Skip if size is too large for stack allocation
        if (Size > 4096)
        {
            return;
        }

        for (int i = 0; i < Iterations; i++)
        {
            byte* ptr = stackalloc byte[Size];

            // Simulate some work - write a byte
            ptr[0] = 42;

            // No explicit free needed - automatically freed when method returns
        }
    }

    /// <summary>
    /// Stack allocation with zeroed memory using stackalloc with initialization.
    /// Combines stack allocation speed with memory initialization.
    /// </summary>
    [Benchmark]
    public unsafe void StackAllocZeroed()
    {
        // Skip if size is too large for stack allocation
        if (Size > 4096)
        {
            return;
        }

        for (int i = 0; i < Iterations; i++)
        {
            Span<byte> span = stackalloc byte[Size];
            span.Clear(); // Explicitly zero the memory

            // Simulate some work - write a byte
            span[0] = 42;

            // No explicit free needed
        }
    }
}
