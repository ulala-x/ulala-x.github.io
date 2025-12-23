---
author: Ulala-X
pubDatetime: 2025-12-23T00:00:00+09:00
title: Net.ZMQ 메모리 최적화 - MessagePool과 ZeroCopy 검증 기록
slug: netzmq-memory-optimization-ko
featured: true
draft: false
lang: ko
lang_ref: netzmq-memory-optimization
tags:
  - zeromq
  - netzmq
  - performance
  - optimization
  - csharp
description: 성능 최적화 기법의 실제 효과 검증 - 벤치마크 데이터 기반 분석
---

# Net.ZMQ 메모리 최적화 - MessagePool과 ZeroCopy 검증 기록

> 성능 최적화 기법의 실제 효과 검증: 벤치마크 데이터 기반 분석

**2025년 12월 23일**

---

## 개요

ZeroMQ는 고성능 비동기 메시징 라이브러리로, 초당 수백만 건의 메시지를 처리할 수 있습니다. 이런 고성능 라이브러리를 .NET에서 사용할 때, 많은 개발자들이 자연스럽게 두 가지 최적화를 떠올립니다:

> "메모리 풀링을 사용하면 할당 오버헤드를 줄일 수 있지 않을까?"
> "ZeroCopy를 사용하면 복사 비용을 없앨 수 있지 않을까?"

이론적으로는 당연해 보이는 최적화입니다. 이 글은 두 가지 최적화 기법을 실제로 구현하고 성능을 측정한 결과를 공유합니다.

### 검증한 최적화 기법

1. **MessagePool**: 네이티브 메모리 풀링을 통한 할당 오버헤드 감소
2. **ZeroCopy**: 메모리 복사 제거를 통한 성능 향상

### 결론 요약

```
MessagePool: 할당만 측정하면 2.5배 빠름, 하지만 실제 Send/Recv에서는 복잡성 대비 10~16% 개선
ZeroCopy: 작은 메시지에서 2.47배 느림, 64KB부터 효과 발생
최종 결정: 복잡성 대비 이점이 크지 않아 미채택
```

---

## Chapter 1: MessagePool - 네이티브 메모리 풀링 검증

### 배경: 왜 MessagePool이 필요했나

Net.ZMQ는 ZeroMQ의 네이티브 메시지(`zmq_msg_t`)를 다뤄야 합니다. 각 메시지 송수신마다 네이티브 메모리 할당/해제가 발생하며, 이는 성능 오버헤드의 원인이 될 수 있습니다.

**가설**: 네이티브 메모리를 미리 할당해두고 재사용하면 할당 오버헤드를 줄일 수 있을 것이다.

**참고**: .NET의 ArrayPool은 관리 메모리(managed memory)를 풀링합니다. MessagePool은 네이티브 메모리(`zmq_msg_t`)를 풀링하는 별도의 메커니즘이 필요했습니다.

### 구현: 네이티브 메모리 풀링 메커니즘

```csharp
public sealed class MessagePool
{
    // 19개의 버킷: 16B ~ 4MB (2의 거듭제곱)
    private static readonly int[] BucketSizes =
    [
        16, 32, 64, 128, 256, 512,
        1024, 2048, 4096, 8192, 16384, 32768, 65536,
        131072, 262144, 524288,
        1048576, 2097152, 4194304
    ];

    // 버킷별 ConcurrentStack 풀
    private readonly ConcurrentStack<Message>[] _pooledMessages;

    // 버킷별 카운터 (Interlocked 기반 thread-safe)
    private long[] _pooledMessageCounts;
}
```

**핵심 메커니즘**:

1. **버킷 할당**: 요청된 크기를 가장 가까운 2의 거듭제곱으로 올림
2. **재사용 가능한 Message**: `zmq_msg_t`를 버킷 크기로 한 번만 초기화
3. **자동 반환**: ZeroMQ의 free callback을 통해 풀로 자동 반환
4. **Thread-safe**: ConcurrentStack + Interlocked 카운터

### 벤치마크 1: 순수 할당 성능 (I/O 제외)

먼저 Send/Recv 없이 순수하게 메모리 할당만 측정했습니다.

**테스트 환경**:
- CPU: Intel Core Ultra 7 265K (20 cores)
- OS: Ubuntu 24.04.3 LTS
- Runtime: .NET 8.0.22
- BenchmarkDotNet v0.14.0

| 크기 | NewMessage | PoolRent (크기만) | PoolRent (데이터 복사) | Ratio |
|------|------------|-------------------|------------------------|-------|
| **64B** | 170 μs | 70 μs | 73 μs | **0.41x (2.4배 빠름)** |
| **512B** | 170 μs | 68 μs | 82 μs | **0.40x (2.5배 빠름)** |
| **1KB** | 200 μs | 68 μs | 78 μs | **0.35x (2.9배 빠름)** |
| **64KB** | 200 μs | 73 μs | 1,019 μs | 복사 비용 폭발 |
| **1MB** | 204 μs | 70 μs | 211,031 μs | 복사 비용 극심 |

**관찰**:
- **PoolRent (크기만)**: 모든 크기에서 **2.5~3배 빠름**
- **PoolRent (데이터 복사)**: 64KB 이상에서 복사 비용이 폭발적으로 증가

**결론**: 메모리 풀링 자체는 효과가 있다!

### 벤치마크 2: 실제 Send/Recv 사이클

그러나 실제 ZeroMQ 송수신 환경에서 측정하면 결과가 달라집니다.

| 방식 | 64B | 512B | 1KB | 64KB |
|------|-----|------|-----|------|
| **ByteArray** (Baseline) | 2.51 ms | 6.99 ms | 8.64 ms | 155 ms |
| **ArrayPool** | 2.59 ms (1.03x) | 6.65 ms (0.95x) | 8.77 ms (1.02x) | 147 ms (0.95x) |
| **Message** | 5.48 ms (2.18x) | 7.08 ms (1.01x) | 8.89 ms (1.03x) | **123 ms (0.79x)** |
| **MessagePooled** (Send만) | 4.47 ms (1.78x) | **5.84 ms (0.84x)** | **7.79 ms (0.90x)** | 136 ms (0.88x) |
| **MessagePooled+RecvPool** | 5.55 ms (2.21x) | 6.94 ms (0.99x) | 8.60 ms (1.00x) | 146 ms (0.94x) |

**관찰**:
- **64B**: MessagePooled가 **78% 느림** (할당에서 2.4배 빠른데 왜?)
- **512B~1KB**: MessagePooled가 **10~16% 빠름**
- **64KB**: Message가 가장 빠름 (21% 개선)

### 원인 분석: 왜 작은 메시지에서 느린가?

할당만 측정하면 Pool이 2.5배 빠른데, 실제 Send/Recv에서는 왜 느려질까?

#### 추가 오버헤드 분석

MessagePool을 사용하면 다음 오버헤드가 추가됩니다:

```csharp
// MessagePool 사용 시 추가되는 작업들
public Message Rent(ReadOnlySpan<byte> data)
{
    // 1. 버킷 인덱스 계산
    var bucketIndex = GetBucketIndex(size);

    // 2. ConcurrentStack에서 Pop (동기화 비용)
    if (_pooledMessages[bucketIndex].TryPop(out var msg))
    {
        Interlocked.Decrement(ref _pooledMessageCounts[bucketIndex]);
        // ...
    }

    // 3. 데이터 복사
    Buffer.MemoryCopy(srcPtr, (void*)msg.DataPtr, actualSize, actualSize);

    // 4. 실제 크기 설정
    msg.SetActualDataSize(actualSize);

    return msg;
}
```

그리고 Send 후 자동 반환 시:

```csharp
// ZeroMQ free callback에서 호출됨
private void ReturnMessageToPool(Message msg)
{
    // GCHandle 관리
    // ConcurrentStack Push (동기화 비용)
    // Interlocked 카운터 갱신
}
```

**비용 분석**:
- ConcurrentStack Pop/Push: ~50ns
- Interlocked 연산: ~20ns × 여러 번
- GCHandle 관리: ~100ns
- 버킷 인덱스 계산: ~10ns

**작은 메시지(64B)에서는 이 오버헤드가 풀링 이점을 상쇄하고도 남음.**

512B 이상에서는 네이티브 메모리 할당 비용이 충분히 커서 풀링 이점이 오버헤드를 상회합니다.

### 결정: MessagePool을 제거한 이유

**성능 개선 vs 복잡성 비용**:

| 크기 | 개선율 | 복잡성 비용 |
|------|--------|-------------|
| 64B | **-78% (더 느림)** | 697줄 구현 코드 |
| 512B | +16% | 1,753줄 테스트 코드 |
| 1KB | +10% | ConcurrentStack 관리 |
| 64KB | +12% | GCHandle 생명주기 관리 |

**10~16% 성능 개선**을 위해 감수해야 할 것들:
- 네이티브 메모리 풀 관리 코드
- GCHandle 생명주기 관리
- 버킷별 ConcurrentStack 동기화
- 메모리 누수 위험
- 복잡한 디버깅

**반면 ArrayPool은**:
- .NET 기본 제공 (유지보수 비용 0)
- GC 할당 99.9% 감소
- 성능은 ByteArray와 거의 동일

**최종 결정**:
복잡성 대비 이점이 크지 않다고 판단하여 MessagePool을 제거했습니다.

```bash
$ git commit -m "Remove MessagePool and simplify memory strategies"
# 14 files changed, 271 insertions(+), 4185 deletions(-)
```

---

## Chapter 2: ZeroCopy - 메모리 복사 제거 검증

### 배경: ZeroCopy의 이론적 이점

MessagePool 분석에서 대용량 메시지의 복사 비용이 병목임을 확인했습니다:

| 크기 | 복사 비용 |
|------|-----------|
| 64B | ~3 μs |
| 512B | ~14 μs |
| 64KB | **~946 μs** |
| 1MB | **~211 ms** |

**가설**: 메모리 복사를 제거하면 대용량 메시지에서 성능이 향상될 것이다.

### ZeroCopy 개념

```csharp
// 일반 방식: 데이터 복사 발생
var msg = new Message(data);  // data를 Message 내부로 복사
socket.Send(msg);

// ZeroCopy 방식: 네이티브 메모리를 직접 전달
nint ptr = Marshal.AllocHGlobal(size);
// 데이터를 ptr에 직접 쓰기
using var msg = new Message(ptr, size, p => Marshal.FreeHGlobal(p));
socket.Send(msg);  // 복사 없이 포인터만 전달
```

### 벤치마크 결과

| 크기 | ByteArray | Message | MessageZeroCopy | ZeroCopy Ratio |
|------|-----------|---------|-----------------|----------------|
| **64B** | 2.51 ms | 5.48 ms | 6.20 ms | **2.47x 느림** |
| **512B** | 6.99 ms | 7.08 ms | 8.12 ms | **1.16x 느림** |
| **1KB** | 8.64 ms | 8.89 ms | 11.83 ms | **1.37x 느림** |
| **64KB** | 155 ms | **123 ms** | 130 ms | 0.84x (16% 빠름) |

**관찰**: ZeroCopy가 모든 크기에서 Message보다 느림!

### 원인 분석: ZeroCopy의 오버헤드

복사를 제거했음에도 왜 더 느릴까?

#### 1. P/Invoke가 생각보다 비싸다

```csharp
// MessageZeroCopy: 여러 번의 P/Invoke 호출
nint ptr = Marshal.AllocHGlobal(size);        // P/Invoke #1
// ... 데이터 복사 ...
var handle = GCHandle.Alloc(callback);        // GCHandle 할당
var result = LibZmq.MsgInitDataPtr(           // P/Invoke #2
    msgPtr, ptr, size, ffnPtr, hintPtr);
socket.Send(msg);                             // P/Invoke #3
// ZeroMQ callback 실행 시 managed -> unmanaged 전환
```

**비용 분석** (64B 메시지 기준):
- P/Invoke 전환: ~50ns × 3회 = 150ns
- GCHandle 할당/해제: ~100ns
- Callback 마샬링: ~100ns
- 네이티브 메모리 할당: ~100ns
- **총 오버헤드: ~450ns**

#### 2. 복사 비용은 생각보다 작다

64바이트를 복사하는 데 걸리는 시간: **~1ns**

ZeroCopy로 절약되는 시간: **1ns**
ZeroCopy를 하려고 추가로 드는 시간: **450ns**

**순손실: 449ns**

#### 3. 왜 64KB에서는 효과가 있나?

64KB 복사 비용: ~946 μs
ZeroCopy 오버헤드: ~450ns

**드디어 복사 비용이 오버헤드를 초과!**

하지만 일반 Message도 64KB에서 가장 빠름 (123ms). ZeroMQ 내부 최적화가 이미 잘 되어 있습니다.

---

## 최종 아키텍처: 단순함의 승리

### 권장 전략

```
메시지 크기 ≤ 1KB:   ArrayPool<byte>.Shared (관리 메모리 풀링)
메시지 크기 > 64KB:  Message (네이티브 메모리, ZMQ 내부 최적화)
```

### 성능 요약표

| 메시지 크기 | 권장 전략 | 이유 |
|-------------|----------|------|
| **≤512B** | ArrayPool | 가장 빠름, GC 99.9% 감소 |
| **1KB** | ArrayPool 또는 Message | 거의 동일 |
| **≥64KB** | Message | 21% 빠름 (ZMQ 내부 최적화) |

### 구현 예시

```csharp
// 작은 메시지: ArrayPool 사용
var buffer = ArrayPool<byte>.Shared.Rent(size);
try
{
    // buffer에 데이터 쓰기
    socket.Send(buffer.AsSpan(0, size));
}
finally
{
    ArrayPool<byte>.Shared.Return(buffer);
}

// 큰 메시지: Message 사용
using var msg = new Message(largeData);
socket.Send(msg);
```

---

## 핵심 발견사항

### 1. 풀링 효과는 실제로 존재한다

MessagePool의 순수 할당 성능은 2.5~3배 빠릅니다. 하지만 실제 사용 환경에서는:
- 추가 오버헤드 (GCHandle, ConcurrentStack, 콜백)
- 작은 메시지에서 오버헤드가 이점을 상쇄
- 10~16% 개선을 위해 복잡한 코드 유지 필요

### 2. 복사 비용의 스케일링

| 크기 | 복사 비용 | 전체 대비 |
|------|-----------|-----------|
| 64B | ~3 μs | 무시 가능 |
| 512B | ~14 μs | 작음 |
| 64KB | ~946 μs | 상당함 |
| 1MB | ~211 ms | 지배적 |

작은 메시지에서는 복사 비용이 거의 없습니다. ZeroCopy의 오버헤드가 훨씬 큽니다.

### 3. Interop은 비싸다

.NET과 네이티브 코드를 넘나드는 비용:
- P/Invoke: ~50ns
- GCHandle: ~100ns
- Callback 마샬링: ~100ns

64바이트 복사: **~1ns**

**가능하면 .NET 안에서 해결하는 게 낫다.**

### 4. 복잡성 vs 성능 트레이드오프

| 기법 | 구현 복잡도 | 성능 개선 | 결론 |
|------|-------------|-----------|------|
| MessagePool | 높음 (2,450줄) | 10~16% | 미채택 |
| ZeroCopy | 중간 | -16% ~ +16% | 미채택 |
| ArrayPool | 없음 (.NET 기본) | 동등 | **채택** |
| Message | 낮음 | 64KB+ 최적 | **채택** |

---

## 요약 및 권장사항

### 채택된 전략

```
작은 메시지 (≤1KB):  ArrayPool<byte>.Shared
큰 메시지 (≥64KB):   Message
```

### 미채택 기법과 그 이유

| 기법 | 이유 |
|------|------|
| **MessagePool** | 10~16% 개선을 위해 2,450줄 코드 유지 필요. 복잡성 대비 이점 부족 |
| **ZeroCopy** | 작은 메시지에서 2.5배 느림. Interop 오버헤드가 복사 비용보다 큼 |

### 검증 과정의 가치

MessagePool과 ZeroCopy는 최종적으로 채택되지 않았지만, 이 검증 과정을 통해:
- 각 전략의 성능 특성을 정량적으로 파악
- 메시지 크기에 따른 최적 전략 수립
- "당연히 빠를 것"이라는 가정의 위험성 확인

**벤치마크 없이 최적화하지 마세요.**

### 직접 테스트해보기

이 블로그의 GitHub 저장소에 벤치마크 코드가 포함되어 있습니다:

```bash
git clone https://github.com/ulala-x/ulala-x.github.io
cd ulala-x.github.io/project/net-zmq
./benchmarks/run-benchmarks.sh memory
```

또는 원본 저장소에서 테스트:

```bash
git clone https://github.com/ulala-x/net-zmq
cd net-zmq
git checkout feature/message-pool
./benchmarks/run-benchmarks.sh memory
```

---

**2025년 12월**
