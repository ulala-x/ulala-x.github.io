---
author: Ulala-X
pubDatetime: 2025-12-20T00:00:00+09:00
title: 697줄의 코드를 지웠더니 성능이 16% 빨라진 이야기
slug: netzmq-memory-optimization
featured: true
draft: false
tags:
  - zeromq
  - netzmq
  - performance
  - optimization
  - csharp
description: 이렇게 하면 당연히 빠르겠지? 라고 생각했던 최적화들이 어떻게 성능을 망쳤는지에 대한 솔직한 고백
---

# 697줄의 코드를 지웠더니 성능이 16% 빨라진 이야기

> "이렇게 하면 당연히 빠르겠지?" 라고 생각했던 최적화들이 어떻게 성능을 망쳤는지에 대한 솔직한 고백

**2025년 12월 20일** / 커밋: [32b4ee2](https://github.com/ulalax/netzmq/commit/32b4ee2), [d122e62](https://github.com/ulalax/netzmq/commit/d122e62)

---

## 들어가며

NetZMQ 프로젝트를 진행하면서 나는 두 가지 "당연한" 최적화를 구현했다:

1. **MessagePool** - 네이티브 메모리를 미리 만들어두고 재사용하면 빠르겠지?
2. **ZeroCopy** - 복사를 없애면 당연히 빠르겠지?

697줄짜리 MessagePool 구현을 완성하고, 1,753줄의 테스트를 다 작성했다. 모든 테스트가 통과했다. 이제 벤치마크만 돌려보면 "성능 2배 향상!" 같은 멋진 결과가 나올 것 같았다.

BenchmarkDotNet을 돌렸다.

```
MessagePool: 7-16% 느림
ZeroCopy: 작은 메시지에서 2.43배 느림
```

...뭐?

이 글은 내가 "당연히 빠를 것"이라고 믿었던 최적화들이 어떻게 성능을 망쳤는지, 그리고 결국 4,185줄의 코드를 삭제하는 것으로 끝난 이야기다.

---

## Chapter 1: MessagePool, 혹은 "이건 당연히 빠를 거야"라는 착각

### 시작: 너무 당연해 보이는 아이디어

생각은 단순했다:

> 네이티브 메모리를 매번 할당하고 해제하는 건 비싸잖아? 그럼 미리 만들어두고 재사용하면 되지!

C/C++에서 object pool은 기본 중의 기본 아닌가. .NET에도 ArrayPool 같은 게 있는데, 네이티브 메모리용 풀을 만들면 당연히 빠를 것 같았다.

그래서 구현을 시작했다. 그것도 제대로:

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

### 구현 여정: 점점 복잡해지는 코드

구현하면서 계속 "아, 이것도 고려해야 하네", "저것도 처리해야 하네" 하면서 코드가 점점 커졌다:

- **f195d1c**: 초기 구현 (+200 lines) - "간단하네!"
- **78816d9**: Double-free 버그 발견... 수정 (+500 lines) - "어라?"
- **45ac578**: Message 객체 재사용 추가 (+100 lines) - "더 최적화!"
- **cb81713**: ActualSize 추적 필요 (+150 lines) - "음..."
- **e8527f8**: Interlocked 카운터로 수정 (+80 lines) - "Thread-safe 하게..."
- **d122e62**: 테스트 대폭 추가 (+1,753 lines) - "이제 완벽!"

**최종 결과**:
- MessagePool.cs: **697줄**
- MessagePoolTests.cs: **1,753줄**
- 내 자신감: **100%**

모든 테스트 통과. 메모리 누수 없음. 코드 리뷰 완료.

이제 벤치마크만 돌리면 된다. 최소 30%는 빨라질 거라고 예상했다.

### 벤치마크 결과, 혹은 "잠깐, 뭔가 잘못됐는데?"

```bash
$ dotnet run -c Release --filter "*ReceivePoolProfilingTest*"
```

BenchmarkDotNet이 돌아가는 동안 커피를 마시며 여유롭게 기다렸다. 곧 멋진 성능 향상 수치가 나올 거라고 믿으면서.

결과가 나왔다:

| 메시지 크기 | Baseline (new Message) | ReceiveWithPool | Ratio |
|-------------|------------------------|-----------------|-------|
| **64B** | 3.33 ms (3.00M msg/sec) | 2.41 ms (4.16M msg/sec) | **0.72x (27% 빠름!)** |
| **1KB** | 7.25 ms (1.38M msg/sec) | 7.69 ms (1.30M msg/sec) | **1.06x (6% 느림)** |
| **64KB** | 134.3 ms (74.5K msg/sec) | 149.4 ms (66.9K msg/sec) | **1.11x (11% 느림)** |

오! 64B에서 27% 빨라졌네!

그런데 잠깐... 메시지 크기가 커질수록 점점 느려진다?

1KB에서 거의 비슷해지고, 64KB에서는 11% 느려진다.

**문제 발견**: 복사 비용이 메시지 크기에 비례해서 증가한다.

작은 메시지에서는 할당을 줄인 이득이 컸지만, 큰 메시지에서는 복사 비용이 그 이득을 넘어섰다.

### 원인 분석: 뭐가 문제였을까

며칠간 코드를 뜯어보고, 프로파일러를 돌렸다. 문제는 생각보다 명확했다.

그리고 문제를 정확히 파악했다:

> **"작은 메시지에서는 빨랐는데, 왜 큰 메시지에서는 느려지지?"**

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

### 결정: Delete를 누르는 순간

한참을 고민했다.

"512B 이하에서는 확실히 빨랐는데..."
"작은 메시지에서는 효과가 있잖아?"
"나중에 쓸 수도 있지 않을까?"

하지만 냉정하게 생각해보니:
- **512B 이하**: Pool이 확실히 빠름 (27% 개선)
- **1KB 이상**: Pool이나 아니나 거의 비슷
- **64KB**: Pool이 11% 느림 (복사 비용)

그리고 결정적인 문제: **Burst 테스트**

Send/Receive를 더 burst하게 (폭발적으로) 테스트했을 때, Pool 버전의 성능이 상당한 수준으로 떨어졌다. ConcurrentStack 동기화 비용과 크로스 스레드 캐시 미스가 부하 상황에서 더 심각하게 드러난 것.

결론:
- 작은 메시지에서만 이득
- 부하 상황에서는 오히려 불안정
- 복잡도는 크게 증가

여러 측면에서 그냥 Message만 사용하는 게 낫다.

결국 12월 20일, 커밋 메시지를 작성했다:

```bash
$ git commit -m "Remove MessagePool and simplify memory strategies"
# 14 files changed, 271 insertions(+), 4185 deletions(-)
```

**4,185줄을 지웠다.**

작은 메시지에서는 효과가 있었지만, 실제 사용 환경에서는 더 큰 메시지가 많다. 그리고 복잡성을 정당화할 만큼의 이득은 아니었다.

가끔은 코드를 추가하는 것보다 지우는 게 더 어렵다. 특히 일주일 동안 공들여 만든 코드라면 더욱.

---

## Chapter 2: "복사를 없애면 빠를 거야" - 두 번째 착각

### Zero-Copy의 유혹

MessagePool을 지우고 나니 한 가지 생각이 들었다:

> "그래, 문제는 복사였어. 그럼 복사를 아예 없애버리면 되잖아?"

Zero-Copy. 이름부터 매력적이지 않은가. 복사가 **제로**다. 안 할 수가 없다.

개념은 정말 간단하다:

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

### 벤치마크: 또 다시 당황

이번엔 학습했다. 구현하고 바로 벤치마크부터 돌렸다.

#### 64B 메시지 결과

| 전략 | 처리량 | 할당량 |
|------|--------|--------|
| **ArrayPool** | **4.12M msg/sec** | 1.85 KB |
| ByteArray | 4.10M msg/sec | 9860 KB |
| Message | 2.34M msg/sec | 168 KB |
| **MessageZeroCopy** | **1.69M msg/sec** | 168 KB |

...뭐?

**제로카피가 가장 느렸다.** 그것도 ArrayPool보다 **2.43배**나.

"Zero"라고 이름 붙은 게 꼴찌를 했다.

#### 512B 메시지

| 전략 | 처리 시간 | 처리량 | 배율 |
|------|----------|--------|------|
| **ArrayPool** | **6.38 ms** | **1.57M msg/sec** | **0.95x** |
| ByteArray | 6.71 ms | 1.49M msg/sec | 1.00x |
| Message | 8.19 ms | 1.22M msg/sec | 1.22x |
| MessageZeroCopy | 13.37 ms | 748K msg/sec | **1.99x** |

여전히 ZeroCopy가 가장 느립니다. ArrayPool보다 **2.1배** 느립니다.

#### 1KB 메시지

| 전략 | 처리 시간 | 처리량 | 배율 |
|------|----------|--------|------|
| ArrayPool | 9.02 ms | 1.11M msg/sec | 1.01x |
| ByteArray | 8.97 ms | 1.11M msg/sec | 1.00x |
| Message | 9.74 ms | 1.03M msg/sec | 1.09x |
| MessageZeroCopy | 14.61 ms | 684K msg/sec | **1.63x** |

1KB에서도 ZeroCopy는 여전히 가장 느립니다 (**1.63배**).

#### 64KB 메시지 (드디어 역전)

혹시나 하는 마음에 큰 메시지로도 테스트했다:

| 전략 | 처리량 | 할당량 |
|------|--------|--------|
| **Message** | **83.9K msg/sec** | 171 KB |
| MessageZeroCopy | 80.2K msg/sec | 171 KB |
| ArrayPool | 70.0K msg/sec | 4.78 KB |
| ByteArray | 70.6K msg/sec | 4GB! |

**드디어!** 64KB부터는 네이티브 메모리가 ArrayPool보다 **16% 빨랐다.**

그래, 제로카피가 완전히 쓸모없는 건 아니었구나.

### 왜? 복사를 안 하는데 왜 느려?

한참을 고민했다. 복사를 안 하면 당연히 빨라야 하는 거 아닌가?

문제는 **"복사를 안 하는 것"에도 비용이 든다**는 것이었다.

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

### .NET이 생각보다 빠르다

여기서 깨달은 것:

> **.NET 팀이 20년 넘게 관리 메모리 최적화한 게 농담이 아니다.**

ArrayPool 하나만 봐도:
- Lock-free로 동작 (대부분)
- 스레드마다 로컬 캐시
- SIMD 명령어로 복사 최적화
- CPU 캐시 친화적 설계

우리가 만든 "제로카피"는:
- P/Invoke 넘나들기
- GCHandle 관리
- Callback 마샬링
- 네이티브 메모리 할당

**결론**:
- 작은 메시지(≤512B): .NET 관리 메모리가 압도적
- 큰 메시지(≥64KB): 네이티브 메모리가 겨우 역전

역시 프레임워크와 싸우면 안 된다.

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

## 배운 것들

### 1. "당연히"라는 말을 조심하자

내가 했던 잘못된 "당연한" 생각들:
- "할당을 줄이면 당연히 빠르지" → 512B 이하에서만 맞았다. 1KB부터는 차이 없고 64KB에서는 오히려 느렸다
- "풀링은 당연히 빠르지" → 메시지 크기와 부하 패턴에 따라 완전히 달랐다
- "제로카피는 당연히 빠르지" → 2.43배 느렸다
- "네이티브가 관리 메모리보다 당연히 빠르지" → ArrayPool이 더 빨랐다

벤치마크를 돌리기 전까지는 다 맞는 말 같았다.

**가장 큰 착각**:
- 일반론(할당 > 복사)은 맞다
- 그래서 작은 메시지(≤512B)에서는 실제로 풀링이 효과가 있었다
- 하지만 복사 비용은 **메시지 크기에 비례**해서 증가한다
- 64B: 10.7μs → 1KB: 105.3μs → 64KB: 9,506μs
- 512B를 넘어가면서 복사 비용이 할당 절약분을 따라잡는다
- 그리고 **Burst 부하 상황에서는 동기화 비용이 폭발**한다

**교훈**:
- 전체 비용을 계산하지 않고 한 부분만 최적화하면 안 된다
- 정상 부하뿐만 아니라 최대 부하 상황도 테스트해야 한다

이제는 코드 짜기 전에 먼저 생각한다: "정말? 측정해봤어?"

### 2. 프레임워크 최적화를 과소평가하지 말자

.NET 팀이 20년 넘게 최적화해온 결과물:

- **ArrayPool**: Lock-free, thread-local 캐싱, SIMD 최적화
- **GC**: 세대별 수집, 대용량 객체 힙, 압축
- **JIT**: 런타임 최적화, 인라이닝

직접 구현한 네이티브 풀링보다 ArrayPool이 더 빨랐다. 프레임워크가 제공하는 최적화된 도구들은 대부분의 경우 직접 구현보다 우수하다.

검증된 도구를 먼저 사용하고, 병목이 실제로 측정될 때만 커스텀 구현을 고려하자.

### 3. 코드는 자산이 아니라 부채다

MessagePool:
- 697줄 구현
- 1,753줄 테스트
- 19개 버킷
- ConcurrentStack
- Interlocked 카운터
- 통계 추적
- 온갖 버그 수정

**얻은 것**:
- ≤512B에서 확실히 빠름 (좋음!)
- ≥1KB에서 거의 차이 없음 (음...)
- 64KB에서 11% 느림 (나쁨!)
- Burst 부하에서 성능 급락 (치명적!)

실제 사용 환경에서는 큰 메시지도 많고, 부하도 들쑥날쑥하다. 복잡성을 정당화하기엔 부족했다.

4,185줄을 지우니 코드도 단순해지고 큰 메시지 성능도 좋아졌다. 코드는 많을수록 좋은 게 아니다.

### 4. Interop은 비싸다

.NET과 네이티브 코드를 넘나드는 건 생각보다 훨씬 비싸다:

- P/Invoke: ~50ns
- GCHandle: ~100ns
- Callback: ~100ns
- 네이티브 할당: ~100ns
- **합계: ~450ns**

64바이트 복사: **~1ns**

**450배 차이.**

가능하면 .NET 안에서 해결하는 게 낫다.

### 5. 삭제하는 것도 용기다

성능 측정 → 나쁨 → 코드 삭제

이게 정답인 건 알지만, 막상 Delete 키를 누르기는 쉽지 않다.

특히 일주일 동안 공들여 만든 코드라면 더욱.

하지만 때로는 코드를 지우는 게 더 나은 선택이다. 이번엔 4,185줄을 지웠고, 성능은 16% 빨라졌다.

---

## 마치며

이 프로젝트를 시작할 때는 "최적화"를 추가하려고 했다.

끝날 때는 "최적화"를 삭제하고 있었다.

**지운 것**:
- MessagePool 697줄
- 테스트 1,753줄
- 벤치마크 클래스 4개
- 복잡한 버킷 로직
- 내 자존심

**얻은 것**:
- 16% 성능 향상
- 단순한 코드
- 한 가지 교훈

**최종 결론**:

```
작은 메시지(≤512B): ArrayPool<byte>.Shared 쓰세요
큰 메시지(>512B):   Message/MessageZeroCopy 쓰세요
```

간단하다.

그리고 다음에 누가 "이렇게 하면 당연히 빠를 거야"라고 말하면, 나는 이렇게 답할 것이다:

> "그래요? 벤치마크 돌려봤어요?"

---

2025년 12월

*"측정하고, 배우고, 때로는 지우자."*
