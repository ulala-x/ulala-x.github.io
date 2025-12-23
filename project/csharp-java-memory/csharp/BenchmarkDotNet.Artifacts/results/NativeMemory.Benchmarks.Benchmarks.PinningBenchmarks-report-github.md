```

BenchmarkDotNet v0.14.0, Ubuntu 24.04.3 LTS (Noble Numbat)
Intel Core Ultra 7 265K, 1 CPU, 20 logical and 20 physical cores
.NET SDK 8.0.122
  [Host]     : .NET 8.0.22 (8.0.2225.52707), X64 RyuJIT AVX2
  Job-PGQRSE : .NET 8.0.22 (8.0.2225.52707), X64 RyuJIT AVX2
  ShortRun   : .NET 8.0.22 (8.0.2225.52707), X64 RyuJIT AVX2

Concurrent=True  Server=True  

```
| Method                | Job        | Force | IterationCount | LaunchCount | WarmupCount | Size    | Iterations | Mean             | Error          | StdDev        | Ratio      | RatioSD  | Allocated | Alloc Ratio |
|---------------------- |----------- |------ |--------------- |------------ |------------ |-------- |----------- |-----------------:|---------------:|--------------:|-----------:|---------:|----------:|------------:|
| **FixedKeyword**          | **Job-PGQRSE** | **False** | **Default**        | **Default**     | **Default**     | **64**      | **10000**      |         **5.769 μs** |      **0.0131 μs** |     **0.0123 μs** |       **1.00** |     **0.00** |         **-** |          **NA** |
| GCHandlePinned        | Job-PGQRSE | False | Default        | Default     | Default     | 64      | 10000      |       245.363 μs |      0.3789 μs |     0.3544 μs |      42.53 |     0.11 |         - |          NA |
| SpanWithMemoryMarshal | Job-PGQRSE | False | Default        | Default     | Default     | 64      | 10000      |         4.234 μs |      0.0053 μs |     0.0047 μs |       0.73 |     0.00 |         - |          NA |
| FixedWithMemoryCopy   | Job-PGQRSE | False | Default        | Default     | Default     | 64      | 10000      |        16.531 μs |      0.0272 μs |     0.0227 μs |       2.87 |     0.01 |      88 B |          NA |
| SpanCopyTo            | Job-PGQRSE | False | Default        | Default     | Default     | 64      | 10000      |        11.190 μs |      0.0474 μs |     0.0443 μs |       1.94 |     0.01 |      88 B |          NA |
| FixedWithNativeCall   | Job-PGQRSE | False | Default        | Default     | Default     | 64      | 10000      |       259.443 μs |      0.8282 μs |     0.7747 μs |      44.97 |     0.16 |         - |          NA |
| GCHandleLongLived     | Job-PGQRSE | False | Default        | Default     | Default     | 64      | 10000      |         2.138 μs |      0.0028 μs |     0.0025 μs |       0.37 |     0.00 |         - |          NA |
|                       |            |       |                |             |             |         |            |                  |                |               |            |          |           |             |
| FixedKeyword          | ShortRun   | True  | 3              | 1           | 3           | 64      | 10000      |         5.780 μs |      0.0842 μs |     0.0046 μs |       1.00 |     0.00 |         - |          NA |
| GCHandlePinned        | ShortRun   | True  | 3              | 1           | 3           | 64      | 10000      |       227.345 μs |      5.8191 μs |     0.3190 μs |      39.33 |     0.05 |         - |          NA |
| SpanWithMemoryMarshal | ShortRun   | True  | 3              | 1           | 3           | 64      | 10000      |         4.274 μs |      0.2287 μs |     0.0125 μs |       0.74 |     0.00 |         - |          NA |
| FixedWithMemoryCopy   | ShortRun   | True  | 3              | 1           | 3           | 64      | 10000      |        16.278 μs |      0.2803 μs |     0.0154 μs |       2.82 |     0.00 |      88 B |          NA |
| SpanCopyTo            | ShortRun   | True  | 3              | 1           | 3           | 64      | 10000      |        11.125 μs |      0.4484 μs |     0.0246 μs |       1.92 |     0.00 |      88 B |          NA |
| FixedWithNativeCall   | ShortRun   | True  | 3              | 1           | 3           | 64      | 10000      |       259.834 μs |     16.3400 μs |     0.8957 μs |      44.95 |     0.14 |         - |          NA |
| GCHandleLongLived     | ShortRun   | True  | 3              | 1           | 3           | 64      | 10000      |         2.143 μs |      0.0261 μs |     0.0014 μs |       0.37 |     0.00 |         - |          NA |
|                       |            |       |                |             |             |         |            |                  |                |               |            |          |           |             |
| **FixedKeyword**          | **Job-PGQRSE** | **False** | **Default**        | **Default**     | **Default**     | **1024**    | **10000**      |         **5.769 μs** |      **0.0107 μs** |     **0.0095 μs** |       **1.00** |     **0.00** |         **-** |          **NA** |
| GCHandlePinned        | Job-PGQRSE | False | Default        | Default     | Default     | 1024    | 10000      |       230.792 μs |      0.6737 μs |     0.6302 μs |      40.00 |     0.12 |         - |          NA |
| SpanWithMemoryMarshal | Job-PGQRSE | False | Default        | Default     | Default     | 1024    | 10000      |         4.237 μs |      0.0079 μs |     0.0074 μs |       0.73 |     0.00 |         - |          NA |
| FixedWithMemoryCopy   | Job-PGQRSE | False | Default        | Default     | Default     | 1024    | 10000      |       113.294 μs |      0.3270 μs |     0.3059 μs |      19.64 |     0.06 |    1048 B |          NA |
| SpanCopyTo            | Job-PGQRSE | False | Default        | Default     | Default     | 1024    | 10000      |       109.076 μs |      0.2383 μs |     0.2229 μs |      18.91 |     0.05 |    1048 B |          NA |
| FixedWithNativeCall   | Job-PGQRSE | False | Default        | Default     | Default     | 1024    | 10000      |     3,249.016 μs |      7.6379 μs |     6.7708 μs |     563.17 |     1.44 |       3 B |          NA |
| GCHandleLongLived     | Job-PGQRSE | False | Default        | Default     | Default     | 1024    | 10000      |         2.141 μs |      0.0030 μs |     0.0025 μs |       0.37 |     0.00 |         - |          NA |
|                       |            |       |                |             |             |         |            |                  |                |               |            |          |           |             |
| FixedKeyword          | ShortRun   | True  | 3              | 1           | 3           | 1024    | 10000      |         5.772 μs |      0.2120 μs |     0.0116 μs |       1.00 |     0.00 |         - |          NA |
| GCHandlePinned        | ShortRun   | True  | 3              | 1           | 3           | 1024    | 10000      |       225.968 μs |      7.1373 μs |     0.3912 μs |      39.15 |     0.09 |         - |          NA |
| SpanWithMemoryMarshal | ShortRun   | True  | 3              | 1           | 3           | 1024    | 10000      |         4.229 μs |      0.0385 μs |     0.0021 μs |       0.73 |     0.00 |         - |          NA |
| FixedWithMemoryCopy   | ShortRun   | True  | 3              | 1           | 3           | 1024    | 10000      |       110.398 μs |      3.4172 μs |     0.1873 μs |      19.13 |     0.04 |    1048 B |          NA |
| SpanCopyTo            | ShortRun   | True  | 3              | 1           | 3           | 1024    | 10000      |       106.414 μs |      1.9572 μs |     0.1073 μs |      18.44 |     0.04 |    1048 B |          NA |
| FixedWithNativeCall   | ShortRun   | True  | 3              | 1           | 3           | 1024    | 10000      |     3,249.672 μs |    194.2820 μs |    10.6493 μs |     563.00 |     1.88 |       3 B |          NA |
| GCHandleLongLived     | ShortRun   | True  | 3              | 1           | 3           | 1024    | 10000      |         2.143 μs |      0.1065 μs |     0.0058 μs |       0.37 |     0.00 |         - |          NA |
|                       |            |       |                |             |             |         |            |                  |                |               |            |          |           |             |
| **FixedKeyword**          | **Job-PGQRSE** | **False** | **Default**        | **Default**     | **Default**     | **65536**   | **10000**      |         **5.843 μs** |      **0.0061 μs** |     **0.0057 μs** |       **1.00** |     **0.00** |         **-** |          **NA** |
| GCHandlePinned        | Job-PGQRSE | False | Default        | Default     | Default     | 65536   | 10000      |       225.612 μs |      0.2396 μs |     0.2124 μs |      38.61 |     0.05 |         - |          NA |
| SpanWithMemoryMarshal | Job-PGQRSE | False | Default        | Default     | Default     | 65536   | 10000      |         4.235 μs |      0.0048 μs |     0.0043 μs |       0.72 |     0.00 |         - |          NA |
| FixedWithMemoryCopy   | Job-PGQRSE | False | Default        | Default     | Default     | 65536   | 10000      |     9,579.860 μs |     86.9046 μs |    81.2907 μs |   1,639.45 |    13.56 |   65572 B |          NA |
| SpanCopyTo            | Job-PGQRSE | False | Default        | Default     | Default     | 65536   | 10000      |     9,573.367 μs |     79.6744 μs |    74.5274 μs |   1,638.34 |    12.44 |   65572 B |          NA |
| FixedWithNativeCall   | Job-PGQRSE | False | Default        | Default     | Default     | 65536   | 10000      |   204,143.070 μs |    548.4814 μs |   486.2145 μs |  34,936.07 |    86.82 |     261 B |          NA |
| GCHandleLongLived     | Job-PGQRSE | False | Default        | Default     | Default     | 65536   | 10000      |         2.146 μs |      0.0087 μs |     0.0077 μs |       0.37 |     0.00 |         - |          NA |
|                       |            |       |                |             |             |         |            |                  |                |               |            |          |           |             |
| FixedKeyword          | ShortRun   | True  | 3              | 1           | 3           | 65536   | 10000      |         5.762 μs |      0.1197 μs |     0.0066 μs |       1.00 |     0.00 |         - |          NA |
| GCHandlePinned        | ShortRun   | True  | 3              | 1           | 3           | 65536   | 10000      |       233.893 μs |    247.0560 μs |    13.5420 μs |      40.59 |     2.04 |         - |          NA |
| SpanWithMemoryMarshal | ShortRun   | True  | 3              | 1           | 3           | 65536   | 10000      |         4.239 μs |      0.2307 μs |     0.0126 μs |       0.74 |     0.00 |         - |          NA |
| FixedWithMemoryCopy   | ShortRun   | True  | 3              | 1           | 3           | 65536   | 10000      |     9,287.411 μs |  1,560.5217 μs |    85.5375 μs |   1,611.82 |    12.95 |   65572 B |          NA |
| SpanCopyTo            | ShortRun   | True  | 3              | 1           | 3           | 65536   | 10000      |     9,270.411 μs |    442.2585 μs |    24.2417 μs |   1,608.87 |     3.97 |   65572 B |          NA |
| FixedWithNativeCall   | ShortRun   | True  | 3              | 1           | 3           | 65536   | 10000      |   203,719.648 μs |  2,861.1291 μs |   156.8281 μs |  35,355.38 |    42.11 |     245 B |          NA |
| GCHandleLongLived     | ShortRun   | True  | 3              | 1           | 3           | 65536   | 10000      |         2.144 μs |      0.1918 μs |     0.0105 μs |       0.37 |     0.00 |         - |          NA |
|                       |            |       |                |             |             |         |            |                  |                |               |            |          |           |             |
| **FixedKeyword**          | **Job-PGQRSE** | **False** | **Default**        | **Default**     | **Default**     | **1048576** | **10000**      |         **5.766 μs** |      **0.0161 μs** |     **0.0143 μs** |       **1.00** |     **0.00** |         **-** |          **NA** |
| GCHandlePinned        | Job-PGQRSE | False | Default        | Default     | Default     | 1048576 | 10000      |       226.042 μs |      0.5321 μs |     0.4977 μs |      39.20 |     0.13 |         - |          NA |
| SpanWithMemoryMarshal | Job-PGQRSE | False | Default        | Default     | Default     | 1048576 | 10000      |         4.243 μs |      0.0082 μs |     0.0077 μs |       0.74 |     0.00 |         - |          NA |
| FixedWithMemoryCopy   | Job-PGQRSE | False | Default        | Default     | Default     | 1048576 | 10000      |   155,163.156 μs |  3,053.9948 μs | 2,999.4313 μs |  26,908.31 |   508.85 | 1048784 B |          NA |
| SpanCopyTo            | Job-PGQRSE | False | Default        | Default     | Default     | 1048576 | 10000      |   155,097.830 μs |  3,000.5443 μs | 3,571.9348 μs |  26,896.98 |   608.95 | 1048784 B |          NA |
| FixedWithNativeCall   | Job-PGQRSE | False | Default        | Default     | Default     | 1048576 | 10000      | 3,261,298.463 μs |  6,096.6856 μs | 5,702.8435 μs | 565,572.54 | 1,656.81 |     784 B |          NA |
| GCHandleLongLived     | Job-PGQRSE | False | Default        | Default     | Default     | 1048576 | 10000      |         2.141 μs |      0.0036 μs |     0.0034 μs |       0.37 |     0.00 |         - |          NA |
|                       |            |       |                |             |             |         |            |                  |                |               |            |          |           |             |
| FixedKeyword          | ShortRun   | True  | 3              | 1           | 3           | 1048576 | 10000      |         5.766 μs |      0.4150 μs |     0.0227 μs |       1.00 |     0.00 |         - |          NA |
| GCHandlePinned        | ShortRun   | True  | 3              | 1           | 3           | 1048576 | 10000      |       231.734 μs |    175.3612 μs |     9.6121 μs |      40.19 |     1.45 |         - |          NA |
| SpanWithMemoryMarshal | ShortRun   | True  | 3              | 1           | 3           | 1048576 | 10000      |         4.239 μs |      0.0289 μs |     0.0016 μs |       0.74 |     0.00 |         - |          NA |
| FixedWithMemoryCopy   | ShortRun   | True  | 3              | 1           | 3           | 1048576 | 10000      |   154,769.150 μs | 60,279.6379 μs | 3,304.1304 μs |  26,842.92 |   504.67 | 1048784 B |          NA |
| SpanCopyTo            | ShortRun   | True  | 3              | 1           | 3           | 1048576 | 10000      |   153,022.418 μs | 22,424.9323 μs | 1,229.1862 μs |  26,539.97 |   205.63 | 1048784 B |          NA |
| FixedWithNativeCall   | ShortRun   | True  | 3              | 1           | 3           | 1048576 | 10000      | 3,262,324.218 μs | 42,149.8546 μs | 2,310.3758 μs | 565,812.51 | 1,961.12 |     736 B |          NA |
| GCHandleLongLived     | ShortRun   | True  | 3              | 1           | 3           | 1048576 | 10000      |         2.146 μs |      0.1400 μs |     0.0077 μs |       0.37 |     0.00 |         - |          NA |
