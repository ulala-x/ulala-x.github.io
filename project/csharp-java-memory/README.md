# C# vs Java Native Memory Benchmarks

A comprehensive benchmark comparing native memory operations between C# (.NET 8) and Java (JDK 22+) using their modern native memory APIs.

## Blog Posts

- **한국어**: [C#과 Java의 네이티브 메모리 처리 비교](https://ulala-x.github.io/ko/posts/csharp-java-native-memory/)
- **English**: [Comparing Native Memory Handling in C# and Java](https://ulala-x.github.io/en/posts/csharp-java-native-memory/)

## Overview

This project benchmarks the performance of native memory operations in:
- **C#**: Using `NativeMemory` API (.NET 6+)
- **Java**: Using Foreign Function & Memory (FFM) API (JDK 22+)

## Features

- Allocation and deallocation performance
- Memory read/write operations (sequential and random)
- Different data sizes (64B, 512B, 1KB, 64KB, 1MB)
- Statistical analysis with BenchmarkDotNet (C#) and JMH (Java)
- Cross-platform support (Windows, Linux, macOS)

## Project Structure

```
csharp-java-memory/
├── README.md
├── results/                   # Benchmark results
├── csharp/                    # C# benchmarks
│   ├── NativeMemory.Benchmarks.sln
│   └── src/
│       └── NativeMemory.Benchmarks/
│           ├── NativeMemory.Benchmarks.csproj
│           ├── Program.cs
│           └── Benchmarks/
└── java/                      # Java benchmarks
    ├── pom.xml
    └── src/
        └── main/
            └── java/
                └── com/
                    └── ulalax/
                        └── benchmark/
```

## Requirements

### C# (.NET)
- .NET 8.0 SDK or later
- BenchmarkDotNet 0.14.0

### Java
- JDK 22 or later (FFM API is finalized in JDK 22)
- Apache Maven 3.9+
- JMH 1.37

## Build Instructions

### C# Benchmarks

```bash
cd csharp
dotnet restore
dotnet build -c Release
```

### Java Benchmarks

```bash
cd java
mvn clean package
```

## Running Benchmarks

### C# Benchmarks

```bash
cd csharp
dotnet run -c Release --project src/NativeMemory.Benchmarks
```

### Java Benchmarks

```bash
cd java
java -jar target/benchmarks.jar
```

## Benchmark Categories

1. **Allocation/Deallocation**
   - Single allocation
   - Batch allocations
   - Mixed allocation/deallocation patterns

2. **Sequential Access**
   - Sequential read
   - Sequential write
   - Sequential read-write mix

3. **Random Access**
   - Random read
   - Random write
   - Random read-write mix

4. **Memory Copy**
   - Small buffer copy (< 1KB)
   - Large buffer copy (> 1MB)
   - Memory fill operations

## Data Sizes

- 64 bytes (cache line)
- 512 bytes
- 1 KB
- 64 KB (typical page size)
- 1 MB

## Results

Benchmark results are stored in the `results/` directory with timestamps and system information.

## Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.

## License

MIT License - see LICENSE file for details.

## Related Projects

- [net-zmq](https://github.com/ulala-x/net-zmq) - .NET binding for ZeroMQ
