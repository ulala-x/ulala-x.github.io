using System;
using System.Runtime.InteropServices;
using BenchmarkDotNet.Attributes;

namespace NativeMemory.Benchmarks.Benchmarks;

/// <summary>
/// Benchmarks realistic data transfer scenarios between managed and native memory.
/// Tests different strategies for copying data: pinning, marshaling, and zero-copy approaches.
/// These patterns are common in native interop, graphics programming, and high-performance computing.
/// </summary>
[MemoryDiagnoser]
public class DataTransferBenchmarks
{
    private byte[] _sourceManaged = null!;
    private byte[] _destinationManaged = null!;
    private IntPtr _sourceNative;
    private IntPtr _destinationNative;

    /// <summary>
    /// Size of data to transfer in bytes.
    /// </summary>
    [Params(64, 1024, 65536, 1048576)]
    public int Size { get; set; }

    /// <summary>
    /// Number of transfer operations to perform.
    /// </summary>
    [Params(10000)]
    public int Iterations { get; set; }

    [GlobalSetup]
    public void Setup()
    {
        // Setup managed arrays
        _sourceManaged = new byte[Size];
        _destinationManaged = new byte[Size];

        // Initialize source with test data
        for (int i = 0; i < Size; i++)
        {
            _sourceManaged[i] = (byte)(i % 256);
        }

        // Setup native memory
        _sourceNative = Marshal.AllocHGlobal(Size);
        _destinationNative = Marshal.AllocHGlobal(Size);

        // Copy initial data to native source
        Marshal.Copy(_sourceManaged, 0, _sourceNative, Size);
    }

    [GlobalCleanup]
    public void Cleanup()
    {
        Marshal.FreeHGlobal(_sourceNative);
        Marshal.FreeHGlobal(_destinationNative);
    }

    /// <summary>
    /// Managed-to-Managed copy using Array.Copy.
    /// This is the baseline for pure managed memory operations.
    /// </summary>
    [Benchmark(Baseline = true)]
    public void ManagedToManaged_ArrayCopy()
    {
        for (int i = 0; i < Iterations; i++)
        {
            Array.Copy(_sourceManaged, _destinationManaged, Size);
        }
    }

    /// <summary>
    /// Managed-to-Managed copy using Span.CopyTo.
    /// Modern C# approach that's often faster than Array.Copy.
    /// Shows how C# optimizes managed code without needing unsafe operations.
    /// </summary>
    [Benchmark]
    public void ManagedToManaged_SpanCopy()
    {
        for (int i = 0; i < Iterations; i++)
        {
            _sourceManaged.AsSpan().CopyTo(_destinationManaged.AsSpan());
        }
    }

    /// <summary>
    /// Managed-to-Managed copy using fixed + Buffer.MemoryCopy.
    /// Demonstrates that sometimes unsafe code isn't faster for managed-to-managed operations.
    /// </summary>
    [Benchmark]
    public unsafe void ManagedToManaged_BufferMemoryCopy()
    {
        for (int i = 0; i < Iterations; i++)
        {
            fixed (byte* srcPtr = _sourceManaged)
            fixed (byte* dstPtr = _destinationManaged)
            {
                Buffer.MemoryCopy(srcPtr, dstPtr, Size, Size);
            }
        }
    }

    /// <summary>
    /// Managed-to-Native copy using Marshal.Copy.
    /// Traditional approach for copying managed data to native memory.
    /// </summary>
    [Benchmark]
    public void ManagedToNative_MarshalCopy()
    {
        for (int i = 0; i < Iterations; i++)
        {
            Marshal.Copy(_sourceManaged, 0, _destinationNative, Size);
        }
    }

    /// <summary>
    /// Managed-to-Native copy using fixed + Buffer.MemoryCopy.
    /// Often faster than Marshal.Copy for larger buffers.
    /// This is the recommended approach for high-performance interop.
    /// </summary>
    [Benchmark]
    public unsafe void ManagedToNative_BufferMemoryCopy()
    {
        for (int i = 0; i < Iterations; i++)
        {
            fixed (byte* srcPtr = _sourceManaged)
            {
                Buffer.MemoryCopy(srcPtr, (byte*)_destinationNative, Size, Size);
            }
        }
    }

    /// <summary>
    /// Managed-to-Native copy using Span with MemoryMarshal.
    /// Modern safe approach that combines Span performance with native interop.
    /// </summary>
    [Benchmark]
    public unsafe void ManagedToNative_SpanCopy()
    {
        for (int i = 0; i < Iterations; i++)
        {
            Span<byte> source = _sourceManaged;
            Span<byte> destination = new Span<byte>((byte*)_destinationNative, Size);
            source.CopyTo(destination);
        }
    }

    /// <summary>
    /// Native-to-Managed copy using Marshal.Copy.
    /// Traditional approach for reading data from native memory.
    /// </summary>
    [Benchmark]
    public void NativeToManaged_MarshalCopy()
    {
        for (int i = 0; i < Iterations; i++)
        {
            Marshal.Copy(_sourceNative, _destinationManaged, 0, Size);
        }
    }

    /// <summary>
    /// Native-to-Managed copy using fixed + Buffer.MemoryCopy.
    /// Higher performance alternative to Marshal.Copy.
    /// </summary>
    [Benchmark]
    public unsafe void NativeToManaged_BufferMemoryCopy()
    {
        for (int i = 0; i < Iterations; i++)
        {
            fixed (byte* dstPtr = _destinationManaged)
            {
                Buffer.MemoryCopy((byte*)_sourceNative, dstPtr, Size, Size);
            }
        }
    }

    /// <summary>
    /// Native-to-Native copy using Buffer.MemoryCopy.
    /// Pure native memory operation - fastest for native-to-native scenarios.
    /// </summary>
    [Benchmark]
    public unsafe void NativeToNative_BufferMemoryCopy()
    {
        for (int i = 0; i < Iterations; i++)
        {
            Buffer.MemoryCopy((byte*)_sourceNative, (byte*)_destinationNative, Size, Size);
        }
    }

    /// <summary>
    /// Native-to-Native copy using NativeMemory and Unsafe.CopyBlock.
    /// Modern .NET 6+ approach for native memory operations.
    /// </summary>
    [Benchmark]
    public unsafe void NativeToNative_UnsafeCopyBlock()
    {
        for (int i = 0; i < Iterations; i++)
        {
            System.Runtime.CompilerServices.Unsafe.CopyBlock(
                (byte*)_destinationNative,
                (byte*)_sourceNative,
                (uint)Size);
        }
    }

    /// <summary>
    /// Zero-copy approach: Pin managed array and pass pointer to native code.
    /// No actual copy happens - this shows the theoretical minimum overhead.
    /// Use this when the native code can work directly with managed memory.
    /// </summary>
    [Benchmark]
    public unsafe void ZeroCopy_FixedPointerPass()
    {
        for (int i = 0; i < Iterations; i++)
        {
            fixed (byte* ptr = _sourceManaged)
            {
                // Simulate passing pointer to native function
                // In real scenario: nativeFunction(ptr, Size);

                // Just read first and last byte to prevent optimization
                byte first = ptr[0];
                byte last = ptr[Size - 1];

                if (first == 255 && last == 255)
                {
                    throw new InvalidOperationException();
                }
            }
        }
    }

    /// <summary>
    /// Bidirectional transfer: Managed → Native → Managed.
    /// Simulates a complete round-trip scenario like calling a native processing function.
    /// </summary>
    [Benchmark]
    public unsafe void RoundTrip_ManagedNativeManaged()
    {
        for (int i = 0; i < Iterations; i++)
        {
            // Managed to Native
            fixed (byte* srcPtr = _sourceManaged)
            {
                Buffer.MemoryCopy(srcPtr, (byte*)_destinationNative, Size, Size);
            }

            // Simulate native processing (just modify one byte)
            Marshal.WriteByte(_destinationNative, 0, 99);

            // Native to Managed
            fixed (byte* dstPtr = _destinationManaged)
            {
                Buffer.MemoryCopy((byte*)_destinationNative, dstPtr, Size, Size);
            }
        }
    }

    /// <summary>
    /// Batch transfer: Multiple small managed arrays to single native buffer.
    /// Common pattern in graphics/audio programming where you aggregate data.
    /// </summary>
    [Benchmark]
    public unsafe void BatchTransfer_MultipleManagedToNative()
    {
        // Use smaller chunks for batch scenario
        int chunkSize = Size / 4;
        if (chunkSize < 16) chunkSize = Size;

        byte[] chunk1 = new byte[chunkSize];
        byte[] chunk2 = new byte[chunkSize];
        byte[] chunk3 = new byte[chunkSize];
        byte[] chunk4 = new byte[chunkSize];

        for (int i = 0; i < Iterations; i++)
        {
            fixed (byte* c1 = chunk1)
            fixed (byte* c2 = chunk2)
            fixed (byte* c3 = chunk3)
            fixed (byte* c4 = chunk4)
            {
                byte* destPtr = (byte*)_destinationNative;

                Buffer.MemoryCopy(c1, destPtr, chunkSize, chunkSize);
                Buffer.MemoryCopy(c2, destPtr + chunkSize, chunkSize, chunkSize);
                Buffer.MemoryCopy(c3, destPtr + chunkSize * 2, chunkSize, chunkSize);
                Buffer.MemoryCopy(c4, destPtr + chunkSize * 3, chunkSize, chunkSize);
            }
        }
    }
}
