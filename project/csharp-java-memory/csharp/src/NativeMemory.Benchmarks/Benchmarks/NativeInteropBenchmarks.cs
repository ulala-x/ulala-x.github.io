using BenchmarkDotNet.Attributes;
using BenchmarkDotNet.Jobs;
using System.Runtime.InteropServices;

namespace NativeMemory.Benchmarks.Benchmarks;

/// <summary>
/// Benchmarks for actual native function calls with ZMQ-style operations
/// This provides fair comparison with Java FFM API benchmarks
/// </summary>
[SimpleJob(RuntimeMoniker.Net80)]
[MemoryDiagnoser]
public class NativeInteropBenchmarks
{
    // P/Invoke declarations for mock ZMQ library
    [DllImport("mockzmq", CallingConvention = CallingConvention.Cdecl)]
    private static extern long mock_send(IntPtr data, nuint len);

    [DllImport("mockzmq", CallingConvention = CallingConvention.Cdecl)]
    private static extern long mock_recv(IntPtr buf, nuint len);

    [DllImport("mockzmq", CallingConvention = CallingConvention.Cdecl)]
    private static extern void mock_transform(IntPtr data, nuint len);

    // Unsafe versions for fixed pointer usage
    [DllImport("mockzmq", CallingConvention = CallingConvention.Cdecl)]
    private static extern unsafe long mock_send(byte* data, nuint len);

    [DllImport("mockzmq", CallingConvention = CallingConvention.Cdecl)]
    private static extern unsafe long mock_recv(byte* buf, nuint len);

    [DllImport("mockzmq", CallingConvention = CallingConvention.Cdecl)]
    private static extern unsafe void mock_transform(byte* data, nuint len);

    /// <summary>
    /// Test data sizes: 64B, 1KB, 64KB, 1MB
    /// These represent typical scenarios from small messages to large data transfers
    /// </summary>
    [Params(64, 1024, 65536, 1048576)]
    public int Size { get; set; }

    private byte[] _managedArray = null!;

    [GlobalSetup]
    public void Setup()
    {
        _managedArray = new byte[Size];
        // Fill with test data
        for (int i = 0; i < Size; i++)
        {
            _managedArray[i] = (byte)(i & 0xFF);
        }
    }

    /// <summary>
    /// Scenario 1: Send with Zero-copy (C# advantage)
    /// Uses fixed keyword to pin managed array and pass pointer directly to native
    /// No copy operation required - this is the key advantage of C# over Java
    /// </summary>
    [Benchmark(Description = "Send_ZeroCopy (fixed)")]
    public unsafe long Send_ZeroCopy()
    {
        fixed (byte* ptr = _managedArray)
        {
            return mock_send(ptr, (nuint)Size);
        }
    }

    /// <summary>
    /// Scenario 1b: Send with Copy (for comparison with Java)
    /// This simulates Java's required approach: allocate native memory + copy
    /// </summary>
    [Benchmark(Description = "Send_WithCopy")]
    public unsafe long Send_WithCopy()
    {
        IntPtr nativePtr = Marshal.AllocHGlobal(Size);
        try
        {
            Marshal.Copy(_managedArray, 0, nativePtr, Size);
            return mock_send(nativePtr, (nuint)Size);
        }
        finally
        {
            Marshal.FreeHGlobal(nativePtr);
        }
    }

    /// <summary>
    /// Scenario 2: Receive with Zero-copy (C# advantage)
    /// Native function writes directly to pinned managed array
    /// </summary>
    [Benchmark(Description = "Recv_ZeroCopy (fixed)")]
    public unsafe long Recv_ZeroCopy()
    {
        fixed (byte* ptr = _managedArray)
        {
            return mock_recv(ptr, (nuint)Size);
        }
    }

    /// <summary>
    /// Scenario 2b: Receive with Copy (for comparison with Java)
    /// Allocate native buffer, call native function, copy back to managed
    /// </summary>
    [Benchmark(Description = "Recv_WithCopy")]
    public unsafe long Recv_WithCopy()
    {
        IntPtr nativePtr = Marshal.AllocHGlobal(Size);
        try
        {
            long result = mock_recv(nativePtr, (nuint)Size);
            Marshal.Copy(nativePtr, _managedArray, 0, Size);
            return result;
        }
        finally
        {
            Marshal.FreeHGlobal(nativePtr);
        }
    }

    /// <summary>
    /// Scenario 3: Transform with Zero-copy (C# advantage)
    /// Native function reads and writes to pinned managed array in-place
    /// This is the most advantageous scenario for C# as it requires:
    /// - Java: copy to native, transform, copy back (2 copies)
    /// - C#: pin only (0 copies)
    /// </summary>
    [Benchmark(Description = "Transform_ZeroCopy (fixed)")]
    public unsafe void Transform_ZeroCopy()
    {
        fixed (byte* ptr = _managedArray)
        {
            mock_transform(ptr, (nuint)Size);
        }
    }

    /// <summary>
    /// Scenario 3b: Transform with Copy (for comparison with Java)
    /// This simulates Java's required approach with round-trip copy
    /// </summary>
    [Benchmark(Description = "Transform_WithCopy")]
    public unsafe void Transform_WithCopy()
    {
        IntPtr nativePtr = Marshal.AllocHGlobal(Size);
        try
        {
            // Copy to native
            Marshal.Copy(_managedArray, 0, nativePtr, Size);
            // Transform
            mock_transform(nativePtr, (nuint)Size);
            // Copy back to managed
            Marshal.Copy(nativePtr, _managedArray, 0, Size);
        }
        finally
        {
            Marshal.FreeHGlobal(nativePtr);
        }
    }
}
