```

BenchmarkDotNet v0.14.0, Ubuntu 24.04.3 LTS (Noble Numbat)
Intel Core Ultra 7 265K, 1 CPU, 20 logical and 20 physical cores
.NET SDK 8.0.122
  [Host]     : .NET 8.0.22 (8.0.2225.52707), X64 RyuJIT AVX2
  Job-KIRUQM : .NET 8.0.22 (8.0.2225.52707), X64 RyuJIT AVX2
  .NET 8.0   : .NET 8.0.22 (8.0.2225.52707), X64 RyuJIT AVX2

Runtime=.NET 8.0  Concurrent=True  Server=True  

```
| Method                       | Job        | Force | Size    | Mean           | Error       | StdDev      | Allocated |
|----------------------------- |----------- |------ |-------- |---------------:|------------:|------------:|----------:|
| **&#39;Send_ZeroCopy (fixed)&#39;**      | **Job-KIRUQM** | **False** | **64**      |       **7.260 ns** |   **0.0129 ns** |   **0.0101 ns** |         **-** |
| Send_WithCopy                | Job-KIRUQM | False | 64      |      16.464 ns |   0.1039 ns |   0.0972 ns |         - |
| &#39;Recv_ZeroCopy (fixed)&#39;      | Job-KIRUQM | False | 64      |       7.935 ns |   0.0303 ns |   0.0268 ns |         - |
| Recv_WithCopy                | Job-KIRUQM | False | 64      |      16.382 ns |   0.0331 ns |   0.0310 ns |         - |
| &#39;Transform_ZeroCopy (fixed)&#39; | Job-KIRUQM | False | 64      |       5.740 ns |   0.0134 ns |   0.0125 ns |         - |
| Transform_WithCopy           | Job-KIRUQM | False | 64      |      16.268 ns |   0.0525 ns |   0.0438 ns |         - |
| &#39;Send_ZeroCopy (fixed)&#39;      | .NET 8.0   | True  | 64      |       7.259 ns |   0.0144 ns |   0.0120 ns |         - |
| Send_WithCopy                | .NET 8.0   | True  | 64      |      16.022 ns |   0.0584 ns |   0.0546 ns |         - |
| &#39;Recv_ZeroCopy (fixed)&#39;      | .NET 8.0   | True  | 64      |       7.846 ns |   0.0292 ns |   0.0273 ns |         - |
| Recv_WithCopy                | .NET 8.0   | True  | 64      |      16.202 ns |   0.0761 ns |   0.0712 ns |         - |
| &#39;Transform_ZeroCopy (fixed)&#39; | .NET 8.0   | True  | 64      |       5.737 ns |   0.0186 ns |   0.0165 ns |         - |
| Transform_WithCopy           | .NET 8.0   | True  | 64      |      16.838 ns |   0.0483 ns |   0.0404 ns |         - |
| **&#39;Send_ZeroCopy (fixed)&#39;**      | **Job-KIRUQM** | **False** | **1024**    |     **105.871 ns** |   **0.2956 ns** |   **0.2621 ns** |         **-** |
| Send_WithCopy                | Job-KIRUQM | False | 1024    |     232.780 ns |   0.6953 ns |   0.6504 ns |         - |
| &#39;Recv_ZeroCopy (fixed)&#39;      | Job-KIRUQM | False | 1024    |     105.160 ns |   0.2687 ns |   0.2382 ns |         - |
| Recv_WithCopy                | Job-KIRUQM | False | 1024    |     238.076 ns |   0.9480 ns |   0.8867 ns |         - |
| &#39;Transform_ZeroCopy (fixed)&#39; | Job-KIRUQM | False | 1024    |      21.876 ns |   0.1065 ns |   0.0996 ns |         - |
| Transform_WithCopy           | Job-KIRUQM | False | 1024    |     262.785 ns |   1.0497 ns |   0.9305 ns |         - |
| &#39;Send_ZeroCopy (fixed)&#39;      | .NET 8.0   | True  | 1024    |     105.971 ns |   0.4314 ns |   0.4035 ns |         - |
| Send_WithCopy                | .NET 8.0   | True  | 1024    |     230.725 ns |   0.6149 ns |   0.5752 ns |         - |
| &#39;Recv_ZeroCopy (fixed)&#39;      | .NET 8.0   | True  | 1024    |     104.749 ns |   0.1673 ns |   0.1483 ns |         - |
| Recv_WithCopy                | .NET 8.0   | True  | 1024    |     240.048 ns |   0.8280 ns |   0.7340 ns |         - |
| &#39;Transform_ZeroCopy (fixed)&#39; | .NET 8.0   | True  | 1024    |      21.867 ns |   0.1121 ns |   0.1049 ns |         - |
| Transform_WithCopy           | .NET 8.0   | True  | 1024    |     265.159 ns |   1.0537 ns |   0.9341 ns |         - |
| **&#39;Send_ZeroCopy (fixed)&#39;**      | **Job-KIRUQM** | **False** | **65536**   |   **6,926.956 ns** |  **18.1281 ns** |  **16.0700 ns** |         **-** |
| Send_WithCopy                | Job-KIRUQM | False | 65536   |   7,814.003 ns |  14.3413 ns |  13.4148 ns |         - |
| &#39;Recv_ZeroCopy (fixed)&#39;      | Job-KIRUQM | False | 65536   |   6,571.924 ns |  13.0714 ns |  10.9152 ns |         - |
| Recv_WithCopy                | Job-KIRUQM | False | 65536   |   7,592.777 ns |  23.7497 ns |  22.2155 ns |         - |
| &#39;Transform_ZeroCopy (fixed)&#39; | Job-KIRUQM | False | 65536   |     673.429 ns |   1.7319 ns |   1.6200 ns |         - |
| Transform_WithCopy           | Job-KIRUQM | False | 65536   |   2,680.348 ns |   4.8138 ns |   4.2673 ns |         - |
| &#39;Send_ZeroCopy (fixed)&#39;      | .NET 8.0   | True  | 65536   |   6,924.969 ns |  13.0847 ns |  11.5993 ns |         - |
| Send_WithCopy                | .NET 8.0   | True  | 65536   |   7,840.274 ns |  24.1289 ns |  22.5702 ns |         - |
| &#39;Recv_ZeroCopy (fixed)&#39;      | .NET 8.0   | True  | 65536   |   6,575.117 ns |   9.3600 ns |   8.2974 ns |         - |
| Recv_WithCopy                | .NET 8.0   | True  | 65536   |   7,586.501 ns |  13.6874 ns |  12.1335 ns |         - |
| &#39;Transform_ZeroCopy (fixed)&#39; | .NET 8.0   | True  | 65536   |   1,182.612 ns |   1.4876 ns |   1.1614 ns |         - |
| Transform_WithCopy           | .NET 8.0   | True  | 65536   |   2,637.703 ns |   3.2929 ns |   2.5709 ns |         - |
| **&#39;Send_ZeroCopy (fixed)&#39;**      | **Job-KIRUQM** | **False** | **1048576** | **111,796.711 ns** | **366.4511 ns** | **306.0033 ns** |         **-** |
| Send_WithCopy                | Job-KIRUQM | False | 1048576 | 125,953.700 ns | 599.6100 ns | 531.5386 ns |         - |
| &#39;Recv_ZeroCopy (fixed)&#39;      | Job-KIRUQM | False | 1048576 | 105,207.510 ns | 398.6171 ns | 372.8667 ns |         - |
| Recv_WithCopy                | Job-KIRUQM | False | 1048576 | 120,583.693 ns | 256.5274 ns | 200.2797 ns |         - |
| &#39;Transform_ZeroCopy (fixed)&#39; | Job-KIRUQM | False | 1048576 |  19,855.235 ns | 139.9274 ns | 130.8882 ns |         - |
| Transform_WithCopy           | Job-KIRUQM | False | 1048576 |  45,582.876 ns | 249.2548 ns | 233.1531 ns |         - |
| &#39;Send_ZeroCopy (fixed)&#39;      | .NET 8.0   | True  | 1048576 | 112,226.381 ns | 383.8894 ns | 320.5650 ns |         - |
| Send_WithCopy                | .NET 8.0   | True  | 1048576 | 125,886.593 ns | 543.0884 ns | 453.5034 ns |         - |
| &#39;Recv_ZeroCopy (fixed)&#39;      | .NET 8.0   | True  | 1048576 | 105,390.645 ns | 313.4038 ns | 293.1581 ns |         - |
| Recv_WithCopy                | .NET 8.0   | True  | 1048576 | 121,610.067 ns | 662.3591 ns | 619.5711 ns |         - |
| &#39;Transform_ZeroCopy (fixed)&#39; | .NET 8.0   | True  | 1048576 |  20,027.210 ns | 161.2455 ns | 150.8292 ns |         - |
| Transform_WithCopy           | .NET 8.0   | True  | 1048576 |  45,372.512 ns | 442.7300 ns | 414.1299 ns |         - |
