---
author: Ulala-X
pubDatetime: 2025-12-20T00:00:00+09:00
title: Net.ZMQ에서 Message Pooling과 ZeroCopy를 사용하지 않는 이유
slug: netzmq-memory-optimization
featured: true
draft: false
tags:
  - zeromq
  - netzmq
  - performance
  - optimization
  - csharp
description: 성능 최적화 기법의 실제 효과 검증 - 벤치마크 데이터 기반 분석
---

# Net.ZMQ에서 Message Pooling과 ZeroCopy를 사용하지 않는 이유

> 성능 최적화 기법의 실제 효과 검증: 벤치마크 데이터 기반 분석

**2025년 12월 20일** / 커밋: [32b4ee2](https://github.com/ulalax/netzmq/commit/32b4ee2), [d122e62](https://github.com/ulalax/netzmq/commit/d122e62)

---

## 개요

ZeroMQ는 고성능 비동기 메시징 라이브러리로, 초당 수백만 건의 메시지를 처리할 수 있습니다. 이런 고성능 라이브러리를 .NET에서 사용할 때, 많은 개발자들이 자연스럽게 두 가지 최적화를 떠올립니다:

> "메모리 풀링을 사용하면 할당 오버헤드를 줄일 수 있지 않을까?"
> "ZeroCopy를 사용하면 복사 비용을 없앨 수 있지 않을까?"

이론적으로는 당연해 보이는 최적화입니다. Net.ZMQ는 ZeroCopy 기능을 제공하지만, 네이티브 메모리 풀링(MessagePool)은 제공하지 않습니다.

이 글은 두 가지 최적화 기법을 실제로 구현하고 성능을 측정한 결과를 공유합니다. MessagePool은 왜 제거되었는지, ZeroCopy는 언제 사용해야 하는지, 벤치마크 데이터와 함께 설명합니다.

### 검증한 최적화 기법

1. **MessagePool**: 네이티브 메모리 풀링을 통한 할당 오버헤드 감소
2. **ZeroCopy**: 메모리 복사 제거를 통한 성능 향상

### 측정 결과

```
MessagePool: 작은 메시지(≤512B)에서 27% 빠름, 큰 메시지에서 11% 느림, Burst 부하에서 성능 급락
ZeroCopy: 작은 메시지에서 2.43배 느림, 64KB부터 효과 발생
```

### 결론

두 기법 모두 이론적 이점에도 불구하고 실제 환경에서는 채택하지 않기로 결정했습니다. 이 글에서는 그 이유를 벤치마크 데이터와 함께 설명합니다.

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

    // 통계 추적
    private long _totalRents;
    private long _totalReturns;
    private long _poolHits;
    private long _poolMisses;
}
```

**핵심 메커니즘**:

1. **버킷 할당**: 요청된 크기를 가장 가까운 2의 거듭제곱으로 올림
2. **재사용 가능한 Message**: `zmq_msg_t`를 버킷 크기로 한 번만 초기화
3. **자동 반환**: ZeroMQ의 free callback을 통해 풀로 자동 반환
4. **Thread-safe**: ConcurrentStack + Interlocked 카운터

**사용 예시**:

```csharp
// MessagePool을 사용한 수신
using var msg = socket.ReceiveWithPool();  // 풀에서 Message 대여
// 데이터 처리
// Dispose 시 자동으로 풀로 반환
```

### 구현 세부사항

MessagePool은 여러 단계를 거쳐 완성되었습니다:

- **f195d1c**: 초기 구현 (+200 lines)
- **78816d9**: Double-free 버그 수정 (+500 lines)
- **45ac578**: Message 객체 재사용 추가 (+100 lines)
- **cb81713**: ActualSize 추적 추가 (+150 lines)
- **e8527f8**: Interlocked 카운터로 thread-safety 보장 (+80 lines)
- **d122e62**: 포괄적인 테스트 추가 (+1,753 lines)

**최종 결과**:
- MessagePool.cs: **697줄**
- MessagePoolTests.cs: **1,753줄**
- 모든 테스트 통과, 메모리 누수 없음

### 벤치마크 결과

**테스트 환경**:
- CPU: Intel Core Ultra 7 265K (20 cores)
- OS: Ubuntu 24.04.3 LTS
- Runtime: .NET 8.0.22
- BenchmarkDotNet v0.14.0

**측정 결과**:

| 메시지 크기 | Baseline (new Message) | ReceiveWithPool | Ratio |
|-------------|------------------------|-----------------|-------|
| **64B** | 3.33 ms (3.00M msg/sec) | 2.41 ms (4.16M msg/sec) | **0.72x (27% 빠름)** |
| **1KB** | 7.25 ms (1.38M msg/sec) | 7.69 ms (1.30M msg/sec) | **1.06x (거의 동일)** |
| **64KB** | 134.3 ms (74.5K msg/sec) | 149.4 ms (66.9K msg/sec) | **1.11x (11% 느림)** |

**관찰**:
- ≤512B: 할당 오버헤드 감소 효과가 복사 비용을 상회
- ≥1KB: 복사 비용이 할당 절감분과 비슷해짐
- 64KB: 복사 비용이 할당 절감분을 초과

**추가 테스트 - Burst 부하**:
Send/Receive를 burst 패턴으로 테스트한 결과, Pool 버전의 성능이 급격히 저하되었습니다. ConcurrentStack 동기화 비용과 크로스 스레드 캐시 미스가 부하 상황에서 더욱 심각하게 나타났습니다.

### 원인 분석

프로파일러와 추가 벤치마크를 통해 성능 특성을 분석했습니다.

#### 핵심 질문: 왜 메시지 크기에 따라 성능이 달라지는가?

**실제 구현**:
```csharp
// Socket 생성 시 4MB 고정 버퍼 할당
private nint _recvBufferPtr;
private const int MaxRecvBufferSize = 4 * 1024 * 1024;  // 4 MB

public Message? ReceiveWithPool(RecvFlags flags)
{
    // 1. 4MB 버퍼로 수신
    int actualSize = Recv(_recvBufferPtr, MaxRecvBufferSize, flags);

    // 2. 실제 크기만큼 풀에서 빌림
    var msg = MessagePool.Shared.Rent(actualSize);

    // 3. 복사! (여기가 문제)
    msg.CopyFromNative(_recvBufferPtr, actualSize);

    return msg;
}
```

**비용 분석**:
- **≤512B**: 할당 절약 >> 복사 비용 → **Pool이 확실히 빠름** (64B에서 27%) ✓
- **1KB**: 할당 절약 ≈ 복사 비용 → **거의 동일** (Pool이나 아니나 차이 없음)
- **64KB**: 할당 절약 << 복사 비용 → **Pool이 11% 느림** ✗

복사 비용은 메시지 크기에 비례해서 증가한다. 512B를 넘어가면서 복사 비용이 할당 절약분을 따라잡기 시작한다.

벤치마크에서 복사만의 오버헤드를 측정했다:

| 메시지 크기 | SpanCopy 오버헤드 | 전체 대비 비율 |
|-------------|------------------|--------------|
| **64B** | 10.7 μs | 0.3% |
| **1KB** | 105.3 μs | 1.4% |
| **64KB** | 9,506 μs | **6.4%** |

작은 메시지에서는 복사가 거의 무시할 수 있지만, **64KB에서는 전체 시간의 6.4%**를 차지한다.

그런데 왜 복사를 해야 했을까?

ZeroMQ의 `zmq_recv()`는 메시지를 받을 때 내부적으로 메모리를 할당한다. 우리가 제어할 수 없다. 그래서:
1. 미리 큰 버퍼(4MB)를 할당해두고
2. 거기로 받은 다음
3. 실제 크기만큼만 풀에서 빌려서
4. 복사한다

이 방식의 장점은 ZeroMQ 할당을 우회할 수 있다는 것. 단점은 복사가 필수라는 것.

작은 메시지에서는 "ZeroMQ 할당 절약 > 복사 비용"이어서 이득이지만, 메시지가 커질수록 복사 비용이 지배적이 된다.

#### 2. **LIFO 비효율** (크로스 스레드 시나리오)

```csharp
// ConcurrentStack은 LIFO 구조
private readonly ConcurrentStack<Message>[] _pooledMessages;

// 스레드 A: 메시지 대여
var msg = pool.Rent(64);  // 풀에서 꺼냄

// 스레드 B에서 ZeroMQ callback 호출
pool.Return(msg);  // 다른 CPU 코어의 캐시로 반환

// 스레드 A: 다시 대여
var msg2 = pool.Rent(64);  // 방금 반환된 msg를 받음
                            // -> CPU 캐시 미스 발생!
```

- **문제**: LIFO는 단일 스레드에는 좋지만, 크로스 스레드에서는 캐시 지역성을 해침
- **영향**: 작은 메시지에서의 이득을 일부 상쇄

#### 3. **풀 관리 오버헤드**

```csharp
public Message Rent(int size)
{
    Interlocked.Increment(ref _totalRents);  // 통계

    int bucketIndex = GetBucketIndex(size);   // 버킷 찾기

    if (_pooledMessages[bucketIndex].TryPop(out var msg))  // 풀에서 꺼내기
    {
        Interlocked.Decrement(ref _pooledMessageCounts[bucketIndex]);
        Interlocked.Increment(ref _poolHits);
        return msg;
    }

    Interlocked.Increment(ref _poolMisses);
    return CreatePooledMessage(bucketSize, bucketIndex);  // 새로 생성
}
```

- ConcurrentStack 동기화 비용
- Interlocked 카운터 갱신 비용
- 버킷 인덱스 계산 비용

### 결정: MessagePool을 제거한 이유

**성능 특성 요약**:
- **512B 이하**: Pool이 확실히 빠름 (27% 개선)
- **1KB 이상**: Pool이나 baseline이나 거의 동일
- **64KB**: Pool이 11% 느림 (복사 비용 증가)
- **Burst 부하**: Pool 성능 급락 (동기화 오버헤드)

**판단 기준**:
1. 실제 사용 환경에서는 다양한 크기의 메시지가 사용됨
2. Burst 부하는 실제 환경에서 자주 발생함
3. 697줄의 구현 + 1,753줄의 테스트 = 높은 유지보수 비용

**최종 결정**:
MessagePool의 이득이 복잡성을 정당화하지 못한다고 판단하여 제거하기로 결정했습니다.

```bash
$ git commit -m "Remove MessagePool and simplify memory strategies"
# 14 files changed, 271 insertions(+), 4185 deletions(-)
```

**제거된 코드**: 4,185줄

---

## Chapter 2: ZeroCopy - 메모리 복사 제거 검증

### 배경: ZeroCopy의 이론적 이점

MessagePool 분석 중 메모리 복사가 주요 오버헤드 중 하나로 확인되었습니다. 이에 따라 복사를 제거하는 ZeroCopy 방식을 검증했습니다.

**가설**: 메모리 복사를 제거하면 특히 대용량 메시지에서 성능이 향상될 것이다.

### ZeroCopy 개념

```csharp
// 일반 방식: 데이터 복사 발생
var msg = new Message(data);  // data를 Message 내부로 복사
socket.Send(msg);

// ZeroCopy 방식: 네이티브 메모리를 직접 전달
nint ptr = Marshal.AllocHGlobal(size);
unsafe
{
    var span = new Span<byte>((void*)ptr, size);
    data.CopyTo(span);  // 네이티브 메모리로 복사 (단 한 번)
}

// 소유권을 ZeroMQ에 넘김
using var msg = new Message(ptr, size, p => Marshal.FreeHGlobal(p));
socket.Send(msg);  // 복사 없이 포인터만 전달!
```

**기대 효과**:
- 관리 메모리 → 네이티브 메모리 복사 제거
- GC 압력 감소
- 대용량 메시지에서 큰 성능 향상

### 벤치마크 결과

#### 64B 메시지

| 전략 | 처리량 | 할당량 |
|------|--------|--------|
| **ArrayPool** | **4.12M msg/sec** | 1.85 KB |
| ByteArray | 4.10M msg/sec | 9860 KB |
| Message | 2.34M msg/sec | 168 KB |
| **MessageZeroCopy** | **1.69M msg/sec** | 168 KB |

**관찰**: ZeroCopy가 ArrayPool보다 **2.43배 느림**

#### 512B 메시지

| 전략 | 처리 시간 | 처리량 | 배율 |
|------|----------|--------|------|
| **ArrayPool** | **6.38 ms** | **1.57M msg/sec** | **0.95x** |
| ByteArray | 6.71 ms | 1.49M msg/sec | 1.00x |
| Message | 8.19 ms | 1.22M msg/sec | 1.22x |
| MessageZeroCopy | 13.37 ms | 748K msg/sec | **1.99x** |

**관찰**: ZeroCopy가 ArrayPool보다 **2.1배 느림**

#### 1KB 메시지

| 전략 | 처리 시간 | 처리량 | 배율 |
|------|----------|--------|------|
| ArrayPool | 9.02 ms | 1.11M msg/sec | 1.01x |
| ByteArray | 8.97 ms | 1.11M msg/sec | 1.00x |
| Message | 9.74 ms | 1.03M msg/sec | 1.09x |
| MessageZeroCopy | 14.61 ms | 684K msg/sec | **1.63x** |

**관찰**: ZeroCopy가 여전히 가장 느림 (**1.63배**)

#### 64KB 메시지 - 성능 역전

| 전략 | 처리량 | 할당량 |
|------|--------|--------|
| **Message** | **83.9K msg/sec** | 171 KB |
| MessageZeroCopy | 80.2K msg/sec | 171 KB |
| ArrayPool | 70.0K msg/sec | 4.78 KB |
| ByteArray | 70.6K msg/sec | 4GB |

**관찰**: 64KB부터는 네이티브 메모리(Message/MessageZeroCopy)가 ArrayPool보다 **16% 빠름**

### 원인 분석: ZeroCopy의 오버헤드

복사를 제거했음에도 작은 메시지에서 성능이 저하된 원인을 분석했습니다.

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

#### 2. **ArrayPool은 정말 빠르다**

```csharp
// ArrayPool: 순수 관리 코드
var buffer = ArrayPool<byte>.Shared.Rent(size);  // O(1) 배열 대여
socket.Send(buffer.AsSpan(0, size));             // 단일 P/Invoke
ArrayPool<byte>.Shared.Return(buffer);           // O(1) 반환
```

**비용 분석**:
- 배열 대여: ~20ns (배열 인덱스만)
- P/Invoke: ~50ns (한 번만)
- 배열 반환: ~20ns
- **총 오버헤드: ~90ns**

#### 3. 계산을 해보자

64바이트를 복사하는 데 걸리는 시간:
- 대략 **1ns** (벤치마크: 10.71μs / 10,000 messages = 1.07ns)

ZeroCopy로 절약되는 시간:
- 복사 안 하니까 **1ns**

ZeroCopy를 하려고 추가로 드는 시간:
- P/Invoke, GCHandle, Callback 등등... **450ns**

**순손실: 449ns**

64바이트를 복사 안 해서 1ns를 아꼈는데, 그걸 하려고 450ns를 썼다.

이게 바로 제로카피의 역설이다.

그럼 언제부터 이득일까? 계산해보면 대략 2~3KB 정도면 손익분기점일 것 같은데, 실제로는 **64KB**에서야 역전된다. 왜일까?

#### 4. **추가 요인들**

- **CPU 캐시 효과**: 작은 데이터는 L1/L2 캐시에 머물러 복사가 매우 빠름
- **.NET JIT 최적화**: 관리 메모리 복사는 SIMD 명령어로 최적화됨
- **GCHandle 비용**: GCHandle은 생각보다 비쌈 (~100ns)
- **Callback 오버헤드**: Managed-unmanaged 경계 넘기는 비용

### 비용 비교 분석

**ArrayPool (관리 메모리)**:
- Lock-free 동작 (대부분의 경우)
- 스레드 로컬 캐싱
- SIMD 최적화된 복사
- CPU 캐시 친화적

**ZeroCopy (네이티브 메모리)**:
- P/Invoke 전환 오버헤드
- GCHandle 관리
- Callback 마샬링
- 네이티브 메모리 할당/해제

**측정 결과**:
- 작은 메시지(≤512B): 관리 메모리(ArrayPool)가 우수
- 큰 메시지(≥64KB): 네이티브 메모리가 우수

---

## 최종 아키텍처: 단순함의 승리

MessagePool 삭제와 ZeroCopy 벤치마크를 통해 얻은 결론:

### 단순한 2단계 전략

```
메시지 크기 ≤ 512B:  ArrayPool<byte>.Shared  (관리 메모리 풀링)
메시지 크기 > 512B:  Message/MessageZeroCopy  (네이티브 메모리)
```

**구현 예시**:

```csharp
// 전송: ArrayPool 사용 (≤512B)
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

// 전송: MessageZeroCopy 사용 (>512B)
nint ptr = Marshal.AllocHGlobal(size);
unsafe
{
    var span = new Span<byte>((void*)ptr, size);
    sourceData.CopyTo(span);
}
using var msg = new Message(ptr, size, p => Marshal.FreeHGlobal(p));
socket.Send(msg);

// 수신: Message 재사용
using var msg = new Message();
socket.Recv(ref msg);
ProcessData(msg.Data);
```

### 성능 요약표

| 메시지 크기 | 권장 전략 | 처리량 | GC 할당 | 개선율 |
|-------------|----------|--------|---------|--------|
| **64B** | ArrayPool | 4.12M msg/sec | 1.85 KB | ByteArray 대비 +0.5%, GC -99.98% |
| **512B** | ArrayPool | 1.57M msg/sec | 2.04 KB | ByteArray 대비 +5%, GC -99.99% |
| **1KB** | ArrayPool | 1.11M msg/sec | 2.24 KB | ByteArray와 동급, GC -99.99% |
| **64KB** | Message | 83.9K msg/sec | 171 KB | ByteArray 대비 +16%, GC -99.95% |

### 의사결정 흐름

```
전송 전략 선택:
├─ 메시지 크기 ≤ 512B?
│  └─ YES → ArrayPool 사용 (최고 성능)
│     └─ ArrayPool<byte>.Shared.Rent(size)
│
└─ NO → Message/MessageZeroCopy 사용
   └─ Marshal.AllocHGlobal + Message(ptr, size, callback)

수신 모드 선택:
├─ 단일 소켓?
│  └─ Blocking 또는 Poller (거의 동일, 0-6% 차이)
│
└─ 다중 소켓?
   └─ Poller (필수)
      └─ Poller + Message 재사용 + 배치 처리
```

---

## 핵심 발견사항

### 1. 측정 기반 최적화의 중요성

**이론과 실제 성능의 차이**:
- MessagePool: 할당 감소의 이점은 실제로 존재하지만, 복사 비용과 동기화 오버헤드가 특정 크기 이상에서 이를 상쇄
- ZeroCopy: 복사 제거의 이점은 interop 오버헤드(P/Invoke, GCHandle 등)보다 작은 메시지에서 작음

**복사 비용의 스케일링**:
- 64B: 10.7μs (10,000 messages)
- 1KB: 105.3μs (10,000 messages)
- 64KB: 9,506μs (10,000 messages)

복사 비용은 메시지 크기에 선형적으로 비례하여 증가합니다. 512B를 기준으로 복사 비용이 할당 절약분을 초과하기 시작합니다.

**부하 패턴의 영향**:
정상 부하와 Burst 부하에서 성능 특성이 달라집니다. MessagePool은 Burst 상황에서 동기화 오버헤드로 인해 성능이 급격히 저하되었습니다.

**결론**: 벤치마크를 통한 실측이 필수적입니다.

### 2. 복잡성 vs 성능 이득

**MessagePool의 복잡성**:
- 697줄 구현
- 1,753줄 테스트
- 19개 크기별 버킷 관리
- Thread-safe 메커니즘 (ConcurrentStack, Interlocked)
- 통계 추적 및 모니터링

**성능 특성**:
- ≤512B: 27% 성능 향상
- ≥1KB: 성능 차이 미미
- 64KB: 11% 성능 저하
- Burst 부하: 심각한 성능 저하

**의사결정**:
실제 환경에서는 다양한 크기의 메시지와 가변적인 부하 패턴이 존재합니다. 한정된 케이스에서의 성능 이득이 코드 복잡성과 유지보수 비용을 정당화하지 못한다고 판단했습니다.

### 3. Interop은 비싸다

.NET과 네이티브 코드를 넘나드는 건 생각보다 훨씬 비싸다:

- P/Invoke: ~50ns
- GCHandle: ~100ns
- Callback: ~100ns
- 네이티브 할당: ~100ns
- **합계: ~450ns**

64바이트 복사: **~1ns**

**450배 차이.**

가능하면 .NET 안에서 해결하는 게 낫다.

### 4. 코드 제거의 기준

**검증 프로세스**:
1. 가설 수립
2. 구현 및 테스트
3. 벤치마크 측정
4. 실제 환경 시뮬레이션
5. 비용-이득 분석
6. 의사결정

**제거 기준**:
- 실측된 성능 이득 < 복잡성 비용
- 제한적인 사용 케이스에서만 효과
- 부하 패턴 변화에 취약

이번 케이스에서는 4,185줄의 코드를 제거했습니다.

---

## 요약 및 권장사항

### Net.ZMQ의 메모리 관리 전략

**채택된 방식**:
```
메시지 크기 ≤ 512B:  ArrayPool<byte>.Shared (관리 메모리 풀링)
메시지 크기 > 512B:  Message (네이티브 메모리)
```

### 성능 측정 결과 요약

| 기법 | 장점 | 단점 | 결론 |
|------|------|------|------|
| **MessagePool** | ≤512B에서 27% 빠름 | ≥1KB에서 효과 없음, Burst 부하에서 성능 급락 | 미채택 |
| **ZeroCopy** | ≥64KB에서 효과 | ≤512B에서 2.43배 느림 | 미채택 |
| **ArrayPool** | ≤512B에서 최고 성능 | 큰 메시지에서는 네이티브 메모리보다 느림 | 채택 (작은 메시지) |
| **Message** | 모든 크기에서 안정적 | ArrayPool보다는 느림 (작은 메시지) | 채택 (기본) |

### 실전 적용 가이드

**당신의 프로젝트에는 어떤 전략이 적합할까요?**

#### 📊 메시지 크기 패턴 확인하기

```csharp
// 1. 먼저 실제 메시지 크기 분포를 확인하세요
var sizes = new List<int>();
for (int i = 0; i < 10000; i++)
{
    var msg = socket.Recv();
    sizes.Add(msg.Size);
}

var avg = sizes.Average();
var p95 = sizes.OrderBy(x => x).ElementAt((int)(sizes.Count * 0.95));
Console.WriteLine($"평균: {avg}B, P95: {p95}B");
```

#### ✅ 적용 기준

**케이스 1: 작은 메시지 위주 (평균 < 512B)**
```csharp
// ArrayPool 사용 권장
var buffer = ArrayPool<byte>.Shared.Rent(size);
try
{
    socket.Send(buffer.AsSpan(0, size));
}
finally
{
    ArrayPool<byte>.Shared.Return(buffer);
}
```
- **적합한 경우**: IoT 센서 데이터, 채팅 메시지, 이벤트 로그
- **예상 효과**: ByteArray 대비 GC 할당 99.9% 감소, 성능 거의 동일

**케이스 2: 중간 크기 메시지 (512B ~ 64KB)**
```csharp
// 기본 Message 사용 권장
using var msg = new Message();
socket.Recv(ref msg);
ProcessData(msg.Data);
```
- **적합한 경우**: JSON 페이로드, 일반적인 RPC 호출
- **특징**: ArrayPool과 ZeroCopy 모두 이점 없음, 단순한 방식이 최선

**케이스 3: 대용량 메시지 (> 64KB)**
```csharp
// Message 또는 MessageZeroCopy 사용
using var msg = new Message(largeData);
socket.Send(msg);
```
- **적합한 경우**: 파일 전송, 이미지/영상 데이터, 대용량 배치 데이터
- **예상 효과**: ArrayPool 대비 약 16% 성능 향상

**케이스 4: 혼합 패턴 (다양한 크기)**
```csharp
// 크기에 따라 동적 선택
if (size <= 512)
{
    var buffer = ArrayPool<byte>.Shared.Rent(size);
    // ArrayPool 사용
}
else
{
    var msg = new Message(size);
    // Message 사용
}
```
- **적합한 경우**: 범용 메시징 시스템
- **주의**: 분기 로직 오버헤드 < 1% (무시 가능)

#### ⚠️ 피해야 할 것

```csharp
// ❌ 잘못된 패턴 1: 작은 메시지에 ZeroCopy
if (size < 100)  // 작은 메시지
{
    nint ptr = Marshal.AllocHGlobal(size);  // 오히려 느림 (2.4배)
    // ...
}

// ❌ 잘못된 패턴 2: 큰 메시지를 ByteArray로
byte[] data = new byte[10_000_000];  // 10MB, GC 압박 심함
socket.Send(data);

// ❌ 잘못된 패턴 3: Burst 부하에 커스텀 풀링
// MessagePool 같은 커스텀 풀링은 동기화 비용으로 인해
// 부하가 높을수록 성능 급락
```

### 검증 과정의 가치

MessagePool과 ZeroCopy는 최종적으로 채택되지 않았지만, 이 검증 과정을 통해:
- 각 전략의 성능 특성을 정량적으로 파악
- 메시지 크기에 따른 최적 전략 수립
- 부하 패턴이 성능에 미치는 영향 확인

이 측정 데이터가 여러분의 프로젝트에서 올바른 결정을 내리는 데 도움이 되길 바랍니다.

### 📚 추가 자료

- **벤치마크 코드**: [GitHub - benchmarks/](https://github.com/ulalax/netzmq/tree/main/benchmarks)
- **실측 데이터**: [BenchmarkDotNet Results](https://github.com/ulalax/netzmq/tree/main/benchmarks/Net.Zmq.Benchmarks/BenchmarkDotNet.Artifacts/results)
- **Net.ZMQ 문서**: [Performance Guide](https://github.com/ulalax/netzmq/blob/main/docs/benchmarks.md)

직접 측정해보고 싶으시다면:
```bash
git clone https://github.com/ulalax/netzmq
cd netzmq/benchmarks/Net.Zmq.Benchmarks
dotnet run -c Release
```

---

**2025년 12월**
