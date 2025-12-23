using System;
using System.Runtime.CompilerServices;
using System.Runtime.InteropServices;
using BenchmarkDotNet.Attributes;

namespace NativeMemory.Benchmarks.Benchmarks;

/// <summary>
/// Benchmarks different approaches to pin managed memory for native interop.
/// This is a critical performance area in C# where choosing the right pinning strategy
/// can significantly impact performance, especially for high-throughput native calls.
/// </summary>
[MemoryDiagnoser]
public class PinningBenchmarks
{
    private byte[] _managedArray = null!;

    /// <summary>
    /// Size of the managed array to pin.
    /// </summary>
    [Params(64, 1024, 65536, 1048576)]
    public int Size { get; set; }

    /// <summary>
    /// Number of pinning operations to perform.
    /// </summary>
    [Params(10000)]
    public int Iterations { get; set; }

    [GlobalSetup]
    public void Setup()
    {
        _managedArray = new byte[Size];
        // Initialize with some data
        for (int i = 0; i < Size; i++)
        {
            _managedArray[i] = (byte)(i % 256);
        }
    }

    /// <summary>
    /// Uses the 'fixed' keyword for zero-copy pinning.
    /// This is the fastest approach for short-lived pinning operations.
    /// The GC cannot move the object while it's pinned, but unpinning is automatic
    /// when the fixed block exits.
    /// RECOMMENDED for most scenarios.
    /// </summary>
    [Benchmark(Baseline = true)]
    public unsafe void FixedKeyword()
    {
        for (int i = 0; i < Iterations; i++)
        {
            fixed (byte* ptr = _managedArray)
            {
                // Simulate native operation - read first and last byte
                byte first = ptr[0];
                byte last = ptr[Size - 1];

                // Prevent compiler optimization
                if (first == 255 && last == 255)
                {
                    throw new InvalidOperationException();
                }
            }
            // Automatically unpinned here
        }
    }

    /// <summary>
    /// Uses GCHandle.Alloc with Pinned type for long-lived pinning.
    /// Suitable when the pointer needs to outlive the current scope.
    /// More expensive than 'fixed' due to handle allocation overhead.
    /// Use when passing pointers to async operations or storing for later use.
    /// </summary>
    [Benchmark]
    public unsafe void GCHandlePinned()
    {
        for (int i = 0; i < Iterations; i++)
        {
            GCHandle handle = GCHandle.Alloc(_managedArray, GCHandleType.Pinned);
            try
            {
                IntPtr ptr = handle.AddrOfPinnedObject();
                byte* bytePtr = (byte*)ptr;

                // Simulate native operation - read first and last byte
                byte first = bytePtr[0];
                byte last = bytePtr[Size - 1];

                // Prevent compiler optimization
                if (first == 255 && last == 255)
                {
                    throw new InvalidOperationException();
                }
            }
            finally
            {
                handle.Free();
            }
        }
    }

    /// <summary>
    /// Uses Span&lt;T&gt; with MemoryMarshal.GetReference for modern zero-copy access.
    /// Combines the safety of Span with the performance of pointers.
    /// Excellent for interop scenarios where you want to avoid unsafe code.
    /// </summary>
    [Benchmark]
    public unsafe void SpanWithMemoryMarshal()
    {
        for (int i = 0; i < Iterations; i++)
        {
            Span<byte> span = _managedArray.AsSpan();

            fixed (byte* ptr = &MemoryMarshal.GetReference(span))
            {
                // Simulate native operation - read first and last byte
                byte first = ptr[0];
                byte last = ptr[Size - 1];

                // Prevent compiler optimization
                if (first == 255 && last == 255)
                {
                    throw new InvalidOperationException();
                }
            }
        }
    }

    /// <summary>
    /// Pins and performs a native memory copy operation using Buffer.MemoryCopy.
    /// Demonstrates a complete interop scenario: pin + native operation.
    /// </summary>
    [Benchmark]
    public unsafe void FixedWithMemoryCopy()
    {
        byte[] destination = new byte[Size];

        for (int i = 0; i < Iterations; i++)
        {
            fixed (byte* srcPtr = _managedArray)
            fixed (byte* dstPtr = destination)
            {
                Buffer.MemoryCopy(srcPtr, dstPtr, Size, Size);
            }
        }
    }

    /// <summary>
    /// Uses Span&lt;T&gt;.CopyTo for managed-optimized copying.
    /// No pinning required - the runtime handles memory access efficiently.
    /// Often faster than explicit pinning for pure managed-to-managed operations.
    /// This shows C#'s advantage: when you don't need native interop, avoid it!
    /// </summary>
    [Benchmark]
    public void SpanCopyTo()
    {
        byte[] destination = new byte[Size];

        for (int i = 0; i < Iterations; i++)
        {
            _managedArray.AsSpan().CopyTo(destination.AsSpan());
        }
    }

    /// <summary>
    /// Simulates passing a pinned pointer to a native function.
    /// This represents the real-world scenario: C# managed array → native C function.
    /// </summary>
    [Benchmark]
    public unsafe void FixedWithNativeCall()
    {
        for (int i = 0; i < Iterations; i++)
        {
            fixed (byte* ptr = _managedArray)
            {
                // Simulate calling a native function that expects byte*
                ProcessNativePointer(ptr, Size);
            }
        }
    }

    /// <summary>
    /// Simulates a native function that processes a pointer.
    /// In real scenarios, this would be a P/Invoke call.
    /// </summary>
    [MethodImpl(MethodImplOptions.NoInlining)]
    private static unsafe void ProcessNativePointer(byte* ptr, int size)
    {
        // Simulate native processing - calculate checksum
        int checksum = 0;
        for (int i = 0; i < size; i++)
        {
            checksum += ptr[i];
        }

        // Prevent optimization
        if (checksum == -1)
        {
            throw new InvalidOperationException();
        }
    }

    /// <summary>
    /// Uses GCHandle for long-lived pinning scenario.
    /// Demonstrates when you need to keep memory pinned across multiple operations.
    /// </summary>
    [Benchmark]
    public unsafe void GCHandleLongLived()
    {
        GCHandle handle = GCHandle.Alloc(_managedArray, GCHandleType.Pinned);
        try
        {
            byte* ptr = (byte*)handle.AddrOfPinnedObject();

            for (int i = 0; i < Iterations; i++)
            {
                // Simulate multiple native operations with same pinned pointer
                byte first = ptr[0];
                byte last = ptr[Size - 1];

                // Prevent compiler optimization
                if (first == 255 && last == 255)
                {
                    throw new InvalidOperationException();
                }
            }
        }
        finally
        {
            handle.Free();
        }
    }
}
