---
author: Ulala-X
pubDatetime: 2025-12-23T00:00:00+09:00
title: C# vs Java 네이티브 메모리 처리 방식의 결정적 차이 - Pin vs Copy
slug: csharp-java-native-memory-ko
featured: true
draft: false
lang: ko
lang_ref: csharp-java-native-memory
tags:
  - csharp
  - java
  - native-memory
  - performance
  - benchmark
description: C#의 Zero-copy Pinning과 Java의 필수 Copy 메커니즘 비교 분석
---

# C# vs Java 네이티브 메모리 처리 방식의 결정적 차이 - Pin vs Copy

> C#의 Zero-copy Pinning과 Java의 필수 Copy 메커니즘 비교 분석

**2025년 12월 23일**

---

## 왜 이 차이가 중요한가

네이티브 라이브러리와 통신하거나 고성능 I/O를 다룰 때, 관리 메모리(Managed Memory)와 네이티브 메모리(Native Memory) 간의 데이터 전달은 피할 수 없습니다. 이때 C#과 Java가 근본적으로 다른 메커니즘을 사용합니다:

- **C#**: `fixed` 키워드로 관리 배열을 네이티브에 Zero-copy로 전달 가능
- **Java**: Pin 기능이 없어서 Heap → Native 복사 필수

이 차이는 대용량 데이터를 빈번하게 처리할 때 성능 차이로 직결됩니다. 이 글에서는 두 언어의 메모리 처리 방식을 벤치마크 데이터를 통해 구체적으로 비교합니다.

---

## Chapter 1: C# 네이티브 메모리 처리

C#은 네이티브 메모리를 다루는 여러 방법을 제공합니다. 각각의 성능 특성을 벤치마크를 통해 살펴보겠습니다.

### 1.1 네이티브 메모리 할당 방식

**테스트 환경**:
- CPU: Intel Core Ultra 7 265K (20 cores)
- OS: Ubuntu 24.04.3 LTS
- Runtime: .NET 8.0.22
- BenchmarkDotNet v0.14.0

> **시간 단위 안내**
> - **μs (마이크로초)**: 1μs = 0.000001초 = 1/1,000,000초
> - **ns (나노초)**: 1ns = 0.000000001초 = 1/1,000,000,000초
> - 참고: 1ms(밀리초) = 1,000μs = 1,000,000ns

| 방식 | 64B | 1KB | 64KB | 1MB |
|-----|-----|-----|------|-----|
| StackAlloc | 11.7 μs | 12.1 μs | 30.0 μs | 106.1 μs |
| NativeMemory.Alloc | 63.6 μs | 61.5 μs | 61.2 μs | 61.4 μs |
| Marshal.AllocHGlobal | 74.3 μs | 72.6 μs | 82.8 μs | 86.3 μs |

**핵심 통찰**:

1. **StackAlloc**이 64B에서 11.7 μs로 가장 빠르지만, 크기가 커질수록 성능이 저하됩니다 (1MB에서 106.1 μs).
2. **NativeMemory.Alloc**은 크기와 무관하게 일정한 성능 (약 61-63 μs)을 보입니다. 이는 힙 할당 메커니즘의 안정성을 보여줍니다.
3. **Marshal.AllocHGlobal**은 레거시 API로 모든 크기에서 가장 느립니다.

**실무 가이드**: 스택에 안전하게 할당 가능한 작은 크기(< 1KB)는 StackAlloc, 그 외는 NativeMemory.Alloc을 권장합니다.

**각 할당 방식의 구체적인 사용 예제**:

```csharp
// 1. StackAlloc - 스택 메모리에 할당
unsafe
{
    byte* buffer = stackalloc byte[1024];
    // 함수 종료 시 자동 해제
}

// 2. NativeMemory.Alloc - 네이티브 힙에 할당 (.NET 6+)
unsafe
{
    byte* buffer = (byte*)NativeMemory.Alloc(1024);
    try {
        // 사용
    } finally {
        NativeMemory.Free(buffer);
    }
}

// 3. Marshal.AllocHGlobal - 네이티브 힙에 할당 (레거시)
IntPtr buffer = Marshal.AllocHGlobal(1024);
try {
    // 사용
} finally {
    Marshal.FreeHGlobal(buffer);
}
```

**메모리 할당 방식 특징 비교**:

| 방식 | 할당 위치 | GC 관리 | 해제 방식 | 특징 |
|-----|---------|--------|---------|-----|
| `stackalloc` | 스택 | ❌ | 자동 (스코프 종료) | 가장 빠름, 크기 제한 (최대 ~1MB, 권장 < 1KB) |
| `NativeMemory.Alloc` | 네이티브 힙 | ❌ | 수동 (`Free`) | 일정한 성능, 현대적 API |
| `Marshal.AllocHGlobal` | 네이티브 힙 | ❌ | 수동 (`FreeHGlobal`) | 레거시, P/Invoke 호환 |
| `new byte[]` | Managed Heap | ✅ | GC | 가장 안전, GC 부하 |

**C# 프로세스의 메모리 구조**:

```
┌─────────────────────────────────────────────────────────────┐
│                      프로세스 메모리                          │
├─────────────────┬─────────────────┬─────────────────────────┤
│   Stack         │  Managed Heap   │     Native Heap         │
│   (스택)         │  (관리 힙)       │     (네이티브 힙)        │
├─────────────────┼─────────────────┼─────────────────────────┤
│ • stackalloc    │ • new byte[]    │ • NativeMemory.Alloc    │
│ • 함수 로컬 변수  │ • GC가 관리      │ • Marshal.AllocHGlobal  │
│ • 자동 해제      │ • 자동 해제      │ • 수동 해제 필요         │
│ • ~1MB 제한     │ • 크기 제한 없음  │ • 크기 제한 없음         │
└─────────────────┴─────────────────┴─────────────────────────┘
```

### 1.2 C#의 핵심 강점: Zero-copy Pinning

C#의 가장 강력한 기능은 `fixed` 키워드를 사용한 Zero-copy 전달입니다.

```csharp
byte[] managedArray = new byte[1024];

// fixed로 GC가 메모리를 이동하지 못하도록 고정
unsafe
{
    fixed (byte* ptr = managedArray)
    {
        // 복사 없이 네이티브 코드에 포인터 전달
        NativeLibrary.ProcessData(ptr, managedArray.Length);
    }
}
// fixed 블록을 벗어나면 자동으로 unpin
```

**Pinning 벤치마크** (10,000회 반복):

| 방식 | 64B | 1KB | 64KB | 1MB |
|-----|-----|-----|------|-----|
| Fixed (pin only) | 5.8 μs | 5.8 μs | 26.4 μs | 28.9 μs |
| GCHandle.Pinned | 245.4 μs | 245.5 μs | 260.5 μs | 259.8 μs |
| MemoryMarshal (Span) | 4.2 μs | 4.3 μs | 4.5 μs | 4.6 μs |

**핵심 통찰**:

1. **MemoryMarshal.GetReference**와 Span을 사용하는 방식이 모든 크기에서 약 4.2-4.6 μs로 가장 빠릅니다.
2. **fixed** 키워드는 64B~1KB에서 5.8 μs로 준수한 성능을 보입니다.
3. **GCHandle.Pinned**는 245 μs로 현저히 느립니다. 명시적 할당/해제 오버헤드가 큽니다.

### 1.3 Managed → Native 데이터 전달 성능

관리 배열에서 네이티브 메모리로 데이터를 전달하는 방식을 비교했습니다.

**벤치마크 결과** (10,000회 반복):

| 방식 | 64B | 1KB | 64KB | 1MB |
|-----|-----|-----|------|-----|
| Marshal.Copy | 11.2 μs | 13.8 μs | 106.1 μs | 1,330 μs |
| Buffer.MemoryCopy (fixed) | 12.7 μs | 16.1 μs | 176.1 μs | 2,146 μs |
| Span.CopyTo (native) | 11.2 μs | 15.8 μs | 166.4 μs | 2,104 μs |

**핵심 통찰**:

1. **작은 크기(64B)에서는** 모든 방법이 비슷합니다 (약 11-13 μs).
2. **1MB 데이터에서는** Marshal.Copy가 1,330 μs로 가장 빠르고, Buffer.MemoryCopy는 2,146 μs로 1.6배 느립니다.
3. **복사가 필요하다면** Marshal.Copy가 가장 빠릅니다. 단, 읽기 전용 작업이라면 섹션 1.2처럼 fixed로 pin하여 복사 없이 전달하는 것이 최선입니다.

**각 복사 방식의 구체적인 사용법**:

```csharp
byte[] managedArray = new byte[1024];
IntPtr nativePtr = Marshal.AllocHGlobal(1024);

// 1. Marshal.Copy - Managed → Native 복사
Marshal.Copy(managedArray, 0, nativePtr, 1024);

// 2. Buffer.MemoryCopy - fixed로 pin 후 복사
unsafe
{
    fixed (byte* src = managedArray)
    {
        Buffer.MemoryCopy(src, (void*)nativePtr, 1024, 1024);
    }
}

// 3. Span.CopyTo - Span 기반 복사
unsafe
{
    Span<byte> nativeSpan = new Span<byte>((void*)nativePtr, 1024);
    managedArray.AsSpan().CopyTo(nativeSpan);
}
```

**복사 방식별 내부 동작 비교**:

| 방식 | 내부 동작 | Pin 필요 | 장점 | 단점 |
|-----|---------|---------|-----|-----|
| `Marshal.Copy` | 내부적으로 pin + memcpy | 자동 | 가장 빠름, 간단, unsafe 불필요 | IntPtr 사용 필요 |
| `Buffer.MemoryCopy` | memcpy 직접 호출 | 수동 (fixed) | 유연함, 포인터 직접 제어 | unsafe 블록 필요 |
| `Span.CopyTo` | 내부적으로 memmove | 수동 (fixed) | 현대적 API, 타입 안전 | 약간 느림 |

**복사 방식과 Zero-copy 방식의 메모리 사용 패턴 비교**:

```
┌─────────────────────────────────────────────────────────────┐
│ [복사 방식] Marshal.Copy / Buffer.MemoryCopy                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Managed Heap          memcpy           Native Heap        │
│  ┌──────────┐    ──────────────────>   ┌──────────┐        │
│  │ byte[]   │      데이터 복사          │ IntPtr   │        │
│  └──────────┘                          └──────────┘        │
│                                                             │
│   → 메모리 2배 사용, 복사 시간 소요                           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ [Zero-copy] fixed 키워드                                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Managed Heap                                              │
│  ┌──────────┐                                               │
│  │ byte[]   │ ←── fixed로 pin (GC가 이동 못함)               │
│  └────┬─────┘                                               │
│       │                                                     │
│       └──→ 포인터를 네이티브 코드에 직접 전달                  │
│                                                             │
│   → 메모리 1배만 사용, 복사 시간 제로                         │
└─────────────────────────────────────────────────────────────┘
```

**실무 적용**:
```csharp
// 읽기 전용 작업: Zero-copy Pinning
unsafe
{
    fixed (byte* ptr = managedArray)
    {
        NativeLib.Read(ptr, length); // 복사 없음
    }
}

// 쓰기 작업: 복사 필요
IntPtr nativePtr = Marshal.AllocHGlobal(size);
Marshal.Copy(managedArray, 0, nativePtr, size);
NativeLib.Write(nativePtr, size);
Marshal.FreeHGlobal(nativePtr);
```

---

## Chapter 2: Java 네이티브 메모리 처리

Java는 JDK 14부터 Foreign Memory API (현재 Foreign Function & Memory API)를 통해 네이티브 메모리 관리를 개선했습니다. 하지만 핵심적인 제약이 있습니다: **Heap 배열을 pin할 수 없습니다**.

### 2.1 Arena 기반 메모리 할당

**테스트 환경**:
- Java: OpenJDK 22.0.2
- JMH 1.37
- OS: Ubuntu 24.04.3 LTS
- CPU: Intel Core Ultra 7 265K

| Arena 타입 | 64B | 1KB | 64KB | 1MB |
|-----------|-----|-----|------|-----|
| Confined (thread-local) | 42 ns | 47 ns | 887 ns | 13,400 ns |
| Shared (thread-safe) | 31,628 ns | 32,578 ns | 34,144 ns | 53,592 ns |
| Global (영구) | 88 ns | 630 ns | 35,520 ns | 559,392 ns |
| Auto (자동 관리) | 482 ns | 818 ns | 37,572 ns | 635,644 ns |

**핵심 통찰**:

1. **Confined Arena**가 모든 크기에서 가장 빠릅니다. 단일 스레드 전용이기 때문입니다.
2. **Shared Arena**는 작은 크기에서 약 31 μs로 매우 느립니다. 동기화 오버헤드 때문입니다.
3. **큰 크기(1MB)**에서는 모든 타입이 비슷한 성능을 보입니다 (약 13-635 μs).

**C#과의 비교**:
- C# NativeMemory.Alloc: 61-63 μs (크기 무관)
- Java Confined Arena: 42 ns ~ 13.4 μs (크기 의존적)

작은 크기에서는 Java가 더 빠르지만, Java는 스레드 안전성을 위해 Shared Arena를 사용하면 성능이 급격히 떨어집니다.

**각 Arena 타입의 사용 예제**:

```java
// 1. Confined Arena - 단일 스레드 전용, 가장 빠름
try (Arena arena = Arena.ofConfined()) {
    MemorySegment segment = arena.allocate(1024);
    // 이 스레드에서만 사용 가능
} // 자동 해제

// 2. Shared Arena - 멀티스레드 안전
try (Arena arena = Arena.ofShared()) {
    MemorySegment segment = arena.allocate(1024);
    // 여러 스레드에서 공유 가능
} // 자동 해제

// 3. Global Arena - 영구 할당
MemorySegment segment = Arena.global().allocate(1024);
// 프로세스 종료 시까지 유지, 해제 불가

// 4. Auto Arena - GC 기반 자동 관리
Arena arena = Arena.ofAuto();
MemorySegment segment = arena.allocate(1024);
// close() 불필요, GC가 관리
```

**Arena 타입별 특징 비교**:

| Arena 타입 | 스레드 안전 | 해제 방식 | 성능 | 사용 사례 |
|-----------|-----------|---------|-----|---------|
| `Confined` | ❌ 단일 스레드 | try-with-resources | 🚀 가장 빠름 | 로컬 작업 |
| `Shared` | ✅ 멀티스레드 | try-with-resources | 🐢 동기화 오버헤드 | 스레드 간 공유 |
| `Global` | ✅ 멀티스레드 | 해제 불가 | ⚡ 빠름 | 상수, 전역 데이터 |
| `Auto` | ✅ 멀티스레드 | GC 자동 | 🔄 GC 의존 | 수명 불명확 시 |

**Java 프로세스의 메모리 구조**:

```
┌─────────────────────────────────────────────────────────────┐
│                      JVM 프로세스 메모리                      │
├─────────────────────┬───────────────────────────────────────┤
│     Java Heap       │           Native Memory               │
│     (자바 힙)        │          (네이티브 메모리)              │
├─────────────────────┼───────────────────────────────────────┤
│ • byte[] 배열        │ • MemorySegment (Arena)               │
│ • 객체 인스턴스       │ • DirectByteBuffer 내부 버퍼           │
│ • GC가 관리          │ • JNI 네이티브 할당                    │
│                     │ • Arena.close()로 해제                 │
├─────────────────────┴───────────────────────────────────────┤
│  ⚠️ Java는 Heap 배열을 Pin할 수 없음 → Native 전달 시 복사 필수  │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Java의 근본적 제약: Heap → Native 복사 필수

Java는 Heap 배열을 pin할 수 없습니다. 따라서 **모든 Heap 데이터를 네이티브로 전달하려면 복사가 필수**입니다.

**Heap → Native 복사 벤치마크**:

| 방식 | 64B | 1KB | 64KB | 1MB |
|-----|-----|-----|------|-----|
| Heap → MemorySegment Copy | 44 ns | 52 ns | 1,825 ns | 39,434 ns |
| Heap → DirectBuffer Copy | 373 ns | 743 ns | 36,175 ns | 553,142 ns |
| Heap → Native (재사용) | 2.5 ns | 8.4 ns | 1,390 ns | 25,074 ns |

**핵심 통찰**:

1. **재사용 가능한 버퍼**를 사용하면 복사 오버헤드를 크게 줄일 수 있습니다 (1MB에서 25 μs).
2. **DirectBuffer**는 매번 할당하면 매우 느립니다 (1MB에서 553 μs).
3. **MemorySegment**가 DirectBuffer보다 일반적으로 빠릅니다.

**C#과의 결정적 차이**:

```java
// Java: 복사 필수
byte[] heapArray = new byte[1024];
try (Arena arena = Arena.ofConfined()) {
    MemorySegment segment = arena.allocate(1024);
    MemorySegment.copy(heapArray, 0, segment, ValueLayout.JAVA_BYTE, 0, 1024); // 복사 발생
    nativeProcess(segment);
}
```

```csharp
// C#: Zero-copy 가능
byte[] managedArray = new byte[1024];
unsafe {
    fixed (byte* ptr = managedArray) {
        NativeProcess(ptr); // 복사 없음
    }
}
```

### 2.3 DirectByteBuffer vs MemorySegment

**할당 성능**:

| 방식 | 64B | 1KB | 64KB | 1MB |
|-----|-----|-----|------|-----|
| DirectByteBuffer.allocate | 367 ns | 708 ns | 33,489 ns | 578,204 ns |
| MemorySegment.allocate | 43 ns | 50 ns | 930 ns | 13,480 ns |

**MemorySegment가 DirectByteBuffer보다 42배 빠릅니다** (1MB 기준).

**실무 가이드**:
- 레거시 코드가 아니라면 DirectByteBuffer 대신 MemorySegment 사용 권장
- 빈번한 할당이 필요하면 Arena를 재사용하여 할당 오버헤드 최소화

---

## Chapter 3: C# vs Java 직접 비교

이제 두 언어의 성능을 직접 비교해보겠습니다. 동일한 테스트 환경에서 측정한 결과입니다.

### 3.1 메모리 할당 성능 비교

**64B 작은 데이터 할당**:

```
빠름 ◀─────────────────────────────────────────────▶ 느림

Java Confined   ██ 0.042 μs
Java Global     ███ 0.088 μs
Java Auto       █████ 0.48 μs
C# StackAlloc   ██████████████████████████████ 11.7 μs
Java Shared     ███████████████████████████████████████████████████████████████ 31.6 μs
C# NativeAlloc  ███████████████████████████████████████████████████████████████████████████████████ 63.6 μs
C# Marshal      ██████████████████████████████████████████████████████████████████████████████████████████ 74.3 μs
```

**1MB 대용량 데이터 할당**:

```
빠름 ◀─────────────────────────────────────────────▶ 느림

Java Confined   ██ 13.4 μs
Java Shared     ████ 53.6 μs
C# NativeAlloc  █████ 61.4 μs
C# Marshal      ███████ 86.3 μs
C# StackAlloc   █████████ 106.1 μs
Java Global     ██████████████████████████████████████████████████████████████████████ 559 μs
Java Auto       ███████████████████████████████████████████████████████████████████████████████ 636 μs
```

| 크기 | C# 최고 성능 | Java 최고 성능 | 승자 |
|-----|-------------|---------------|------|
| 64B | StackAlloc (11.7 μs) | Confined (0.042 μs) | **Java 279배 빠름** |
| 1KB | StackAlloc (12.1 μs) | Confined (0.047 μs) | **Java 257배 빠름** |
| 64KB | StackAlloc (30.0 μs) | Confined (0.89 μs) | **Java 34배 빠름** |
| 1MB | NativeAlloc (61.4 μs) | Confined (13.4 μs) | **Java 4.6배 빠름** |

> **통찰**: 메모리 할당만 보면 Java의 Confined Arena가 압도적으로 빠릅니다.
> 하지만 이는 "할당"만의 비교이고, 실제 네이티브 전달에는 복사 비용이 추가됩니다.

### 3.2 Heap → Native 데이터 전달 성능 비교

실제로 Heap 데이터를 네이티브 코드에 전달하는 전체 비용을 비교합니다.

**64B 작은 데이터 전달**:

```
빠름 ◀─────────────────────────────────────────────▶ 느림

Java 재사용버퍼   █ 0.0025 μs (복사)
C# MemoryMarshal ████████████████████████████████████████████ 4.2 μs (Zero-copy)
C# Fixed         ██████████████████████████████████████████████████████████ 5.8 μs (Zero-copy)
C# Marshal.Copy  ██████████████████████████████████████████████████████████████████████████████████████████████████ 11.2 μs (복사)
Java 매번할당     ████████████████████████████████████████ 0.044 μs (복사+할당)
```

**1MB 대용량 데이터 전달**:

```
빠름 ◀─────────────────────────────────────────────▶ 느림

Java 재사용버퍼    ██ 25.1 μs (복사)
C# Fixed          ██ 28.9 μs (Zero-copy, Pin만)
Java 매번할당      ███ 39.4 μs (복사+할당)
C# Marshal.Copy   █████████████████████████████████████████████████████████████████████████████████ 1,330 μs (복사)
```

| 크기 | C# Zero-copy (Fixed) | Java 재사용 버퍼 복사 | 비고 |
|-----|---------------------|---------------------|------|
| 64B | 5.8 μs | 0.0025 μs | Java 2,320배 빠름 |
| 1KB | 5.8 μs | 0.0084 μs | Java 690배 빠름 |
| 64KB | 26.4 μs | 1.39 μs | Java 19배 빠름 |
| 1MB | 28.9 μs | 25.1 μs | **거의 동일** |

> **핵심 발견**:
> - **작은 데이터(< 64KB)**: Java의 재사용 버퍼 복사가 C#의 Zero-copy보다 빠릅니다!
> - **대용량 데이터(≥ 1MB)**: 두 언어가 거의 동일한 성능을 보입니다.
> - C#의 `Marshal.Copy`는 모든 크기에서 가장 느립니다.

### 3.3 왜 이런 결과가 나올까?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         성능 역전 현상 분석                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  [작은 데이터 < 64KB]                                                    │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ C# Fixed: GC 중단 + 메모리 고정 오버헤드 > 실제 복사 비용         │   │
│  │ Java 복사: 단순 memcpy만 수행 (매우 빠름)                        │   │
│  │                                                                   │   │
│  │ → 작은 데이터는 복사하는 게 고정(pin)보다 빠름!                    │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  [대용량 데이터 ≥ 1MB]                                                   │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ C# Fixed: 고정 오버헤드 28.9 μs (크기 무관)                       │   │
│  │ Java 복사: memcpy 25.1 μs (크기에 비례)                          │   │
│  │                                                                   │
│  │ → 크기가 커지면 복사 비용도 증가하여 Zero-copy와 비슷해짐          │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  [결론]                                                                 │
│  • 64KB 미만: Java 재사용 버퍼가 더 효율적                              │
│  • 1MB 이상: C# Zero-copy와 Java 복사 성능 동등                         │
│  • 10MB+ 초대용량: C# Zero-copy가 유리해지기 시작                       │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.4 실무 선택 가이드 (크기별)

| 데이터 크기 | 권장 언어/방식 | 이유 |
|-----------|--------------|------|
| < 1KB | Java 또는 C# | 성능 차이 무의미 (< 10μs) |
| 1KB ~ 64KB | Java (재사용 버퍼) | 복사가 pin보다 빠름 |
| 64KB ~ 1MB | 둘 다 OK | 성능 수렴 구간 |
| > 1MB | C# (Fixed) | Zero-copy 장점 발휘 |
| > 10MB | **C# (Fixed) 강력 권장** | 복사 비용이 급증 |

---

## Chapter 4: Pin vs Copy - 아키텍처 차이

### 4.1 아키텍처 수준의 차이

| 특성 | C# | Java |
|-----|-----|------|
| **Pinning 지원** | ✅ `fixed`, `GCHandle` | ❌ 불가능 |
| **Zero-copy 전달** | ✅ Managed → Native 직접 전달 | ❌ 복사 필수 |
| **GC 영향** | Pin 중에는 메모리 이동 불가 | GC와 독립적 (복사하므로) |
| **성능 특성** | 복사 비용 제로 | 크기에 비례한 복사 비용 |
| **안전성** | Unsafe 블록 필요 | 타입 안전 |

### 4.2 성능 임팩트 비교

**1MB 데이터를 네이티브로 전달하는 실제 벤치마크 결과**:

#### Send 작업 (읽기 전용 데이터 전달)

| 언어 | 방식 | 시간 | 비고 |
|-----|-----|------|------|
| C# | **Send_ZeroCopy (fixed)** | **112 μs** | Pin 오버헤드만 |
| Java | send_HeapCopyToNative (재사용 버퍼) | 127 μs | 복사 + 네이티브 호출 |
| C# | Send_WithCopy | 126 μs | 복사 + 네이티브 호출 |
| Java | send_AllocateAndCopy (매번 할당) | 131 μs | 할당 + 복사 + 호출 |

**결과 분석**:
- C#의 Zero-copy가 **112 μs**로 가장 빠름
- Java의 재사용 버퍼 복사가 127 μs로 C#의 With-copy (126 μs)와 거의 동일
- 1MB 데이터에서는 **C#이 약 13% 빠름** (Zero-copy 사용 시)

#### Transform 작업 (읽기-쓰기 데이터 변환)

| 언어 | 방식 | 시간 | 비고 |
|-----|-----|------|------|
| C# | **Transform_ZeroCopy (fixed)** | **20 μs** | Pin만, 제자리 변환 |
| C# | Transform_WithCopy | 46 μs | 복사 2회 (왕복) |
| Java | transform_RoundTripCopy (재사용 버퍼) | 65 μs | 복사 2회 (왕복) |
| Java | transform_AllocateAndRoundTrip | 80 μs | 할당 + 복사 2회 |

**결과 분석**:
- C#의 Zero-copy가 **20 μs**로 압도적으로 빠름
- Java의 재사용 버퍼 복사가 65 μs로 C#의 With-copy (46 μs)보다 1.4배 느림
- C# Zero-copy는 Java 최적화 복사보다 **3.25배 빠름**

**핵심 통찰**:

1. **Send 작업 (읽기 전용)**: C#과 Java의 차이는 작음 (약 13%)
2. **Transform 작업 (읽기-쓰기)**: C#의 Zero-copy가 결정적인 우위 (3.25배 빠름)
3. **Java의 제약**: Heap 배열을 pin할 수 없어 필수적으로 복사해야 함
4. **C#의 유연성**: fixed 키워드로 복사 오버헤드를 완전히 제거 가능

### 4.3 대용량 데이터 처리 시나리오

**시나리오 1**: 1MB 데이터를 1,000번 네이티브로 Send (읽기 전용)

| 언어 | 방식 | 총 시간 (예측) | 차이 |
|-----|-----|---------------|------|
| C# | Send_ZeroCopy (fixed) | 112 ms | 기준 |
| Java | send_HeapCopyToNative (재사용) | 127 ms | +13% |
| C# | Send_WithCopy | 126 ms | +13% |

**시나리오 2**: 1MB 데이터를 1,000번 Transform (읽기-쓰기)

| 언어 | 방식 | 총 시간 (예측) | 차이 |
|-----|-----|---------------|------|
| C# | Transform_ZeroCopy (fixed) | 20 ms | 기준 (최고 성능) |
| C# | Transform_WithCopy | 46 ms | +130% |
| Java | transform_RoundTripCopy (재사용) | 65 ms | +225% |
| Java | transform_AllocateAndRoundTrip | 80 ms | +300% |

**실무 의미**:
- **읽기 전용 작업**: C#과 Java의 차이는 크지 않음 (약 13%)
- **읽기-쓰기 작업**: C#의 Zero-copy가 압도적 우위 (3.25배 빠름)
- **네이티브 라이브러리가 데이터를 제자리에서 변환하는 경우**: C#의 fixed 키워드 사용을 강력히 권장
- Java는 버퍼를 재사용하는 것이 필수입니다. 매번 할당하면 57% 더 느립니다.

---

## Chapter 5: 실무 적용 가이드

### 5.1 C# 네이티브 메모리 사용 패턴

**패턴 1: 읽기 전용 데이터를 네이티브로 전달**

```csharp
// Zero-copy로 네이티브에 전달
byte[] imageData = LoadImage();

unsafe
{
    fixed (byte* ptr = imageData)
    {
        ProcessImageNative(ptr, imageData.Length);
    }
}
```

**적용 케이스**:
- 이미지/비디오 처리 (OpenCV, FFmpeg 등)
- 암호화 라이브러리 (OpenSSL 등)
- 네트워크 패킷 처리

**패턴 2: 쓰기 가능한 네이티브 버퍼**

```csharp
// NativeMemory.Alloc으로 네이티브 버퍼 할당
unsafe
{
    byte* nativeBuffer = (byte*)NativeMemory.Alloc(1024);

    try
    {
        FillDataNative(nativeBuffer, 1024);

        // 결과를 managed로 복사
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

**패턴 3: 고성능 I/O with Span**

```csharp
// Span으로 Zero-copy 읽기/쓰기
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

### 5.2 Java 네이티브 메모리 사용 패턴

**패턴 1: Arena 재사용으로 할당 최소화**

```java
// Arena를 미리 생성하여 재사용
private final Arena arena = Arena.ofShared();
private final MemorySegment reusableBuffer =
    arena.allocate(1024 * 1024); // 1MB 버퍼

public void processData(byte[] heapData) {
    // 재사용 버퍼에 복사
    MemorySegment.copy(heapData, 0,
        reusableBuffer, ValueLayout.JAVA_BYTE, 0, heapData.length);

    nativeProcess(reusableBuffer);
}

// 종료 시
public void close() {
    arena.close();
}
```

**패턴 2: 스트리밍 데이터 처리**

```java
// 청크 단위로 처리하여 메모리 효율성 확보
try (Arena arena = Arena.ofConfined()) {
    MemorySegment chunk = arena.allocate(4096);

    while (hasMoreData()) {
        byte[] heapChunk = readChunk();
        MemorySegment.copy(heapChunk, 0, chunk, ValueLayout.JAVA_BYTE, 0, heapChunk.length);
        processChunk(chunk);
    }
}
```

**패턴 3: DirectByteBuffer 레거시 코드**

```java
// 레거시 NIO 코드와 통합
ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024);
directBuffer.put(heapArray);
directBuffer.flip();

// MemorySegment로 래핑 (Zero-copy)
MemorySegment segment = MemorySegment.ofBuffer(directBuffer);
nativeProcess(segment.address());
```

### 5.3 언제 어떤 언어를 선택할 것인가

**C#을 선택해야 하는 경우**:

1. **대용량 데이터 읽기 전용 처리**
   - 예: 100MB 이미지를 네이티브 라이브러리로 처리
   - 이유: Zero-copy로 복사 비용 제로

2. **빈번한 Managed ↔ Native 전환**
   - 예: 초당 10,000번 네이티브 함수 호출
   - 이유: fixed 키워드로 오버헤드 최소화

3. **메모리 효율성이 중요한 경우**
   - 예: IoT 디바이스, 임베디드 시스템
   - 이유: 복사 없이 메모리 재사용 가능

**Java를 선택해야 하는 경우**:

1. **안전성이 최우선인 경우**
   - 예: 금융 시스템, 의료 시스템
   - 이유: Unsafe 없이 타입 안전한 API

2. **크로스 플랫폼 일관성**
   - 예: Linux/macOS/Windows 동일 코드
   - 이유: JVM이 플랫폼 차이 흡수

3. **작은 크기 데이터 처리**
   - 예: 1KB 미만 빈번한 네이티브 호출
   - 이유: 복사 오버헤드가 미미함 (< 100ns)

---

## 결론

### 핵심 요약

1. **C#의 강점: Zero-copy Pinning**
   - `fixed` 키워드로 관리 배열을 네이티브에 직접 전달
   - 대용량 데이터에서 복사 비용 제로
   - Unsafe 코드 필요

2. **Java의 제약: 복사 필수**
   - Heap 배열을 pin할 수 없음
   - 모든 데이터를 Native로 복사해야 함
   - Arena 재사용으로 오버헤드 최소화 가능

3. **성능 차이**
   - 작은 크기(< 1KB): 두 언어 모두 비슷 (< 100ns 차이)
   - 큰 크기(> 64KB): C# Zero-copy가 우위
   - 최적화된 Java 복사도 충분히 빠름 (1MB에 25μs)

4. **실무 선택 기준**
   - 읽기 전용 대용량 데이터: C# 유리
   - 안전성과 타입 안전: Java 유리
   - 성능 극한 최적화: C# 유리
   - 크로스 플랫폼 일관성: Java 유리

### 최종 권장사항

**C# 개발자**:
- 가능하면 `fixed` 또는 `Span<T>`로 Zero-copy 구현
- 작은 크기는 `stackalloc`, 큰 크기는 `NativeMemory.Alloc`
- `Marshal.AllocHGlobal`은 레거시 코드에만 사용

**Java 개발자**:
- 레거시가 아니면 DirectByteBuffer 대신 MemorySegment 사용
- Arena를 재사용하여 할당 오버헤드 최소화
- 대용량 데이터는 청크 단위로 처리하여 메모리 효율성 확보

두 언어 모두 현대적이고 강력한 네이티브 메모리 API를 제공합니다. 핵심은 각 언어의 특성을 이해하고, 사용 사례에 맞는 최적의 패턴을 선택하는 것입니다.

---

**관련 자료**:
- [C# NativeMemory API 문서](https://learn.microsoft.com/en-us/dotnet/api/system.runtime.interopservices.nativememory)
- [Java Foreign Function & Memory API](https://openjdk.org/jeps/454)
- [벤치마크 소스 코드](https://github.com/ulala-x/ulala-x.github.io/tree/main/project/csharp-java-memory)
