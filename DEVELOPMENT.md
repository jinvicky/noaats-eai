# NOAATS-EAI — 개발 문서

---

## 아키텍처

```
  Trigger Layer              Execution Layer              Adapter SPI
  ─────────────              ───────────────              ───────────
  ManualRunController ─┐
  CronTriggerRegistry  ├──▶  ExecutionEngine  ──▶  Adapter.bind(configJson)
  FileWatcherService   │     (@Async pool)           │
  KafkaListenerReg     ┘                             ├─ read()  → Stream<DataRecord>
                                                     │              ↓
                                                     │       MappingEngine.apply()
                                                     │              ↓
                                                     └─ write() → WriteResult

  ExecutionRun  (JPA) ◀── 상태 / records 카운트
  ExecutionLog  (JPA) ◀── phase별 로그 (LIFECYCLE · TRANSFORM · WRITE)
  Micrometer          ◀── eai.runs.total / failed / duration
```

모든 트리거는 `ExecutionEngine.start(interfaceId, triggerType, meta)`로 수렴하며, 실행은 `@Async` 스레드 풀(core 4 / max 16 / queue 100)에서 비동기 처리됩니다.  
`Stream<DataRecord>` 파이프라인으로 대용량 데이터도 메모리 효율적으로 처리합니다.

---

## 프로젝트 구조

```
src/main/java/com/eai/
├── EaiApplication.java
├── config/              # AsyncConfig, DemoSeed
├── domain/              # JPA 엔티티 + Repository
├── catalog/             # CatalogService
├── adapter/
│   ├── spi/             # Adapter SPI (DataRecord, AdapterContext, WriteResult)
│   ├── rest/            # RestAdapter (java.net.http)
│   ├── jdbc/            # JdbcAdapter (스트리밍 ResultSet)
│   ├── file/            # FileAdapter (CSV/JSON/XML)
│   └── mq/              # KafkaAdapter
├── mapping/             # MappingEngine, TransformRegistry
├── execution/           # ExecutionEngine (@Async)
├── trigger/
│   ├── cron/            # CronTriggerRegistry
│   ├── event/           # KafkaListenerRegistry
│   ├── file/            # FileWatcherService
│   ├── manual/          # ManualRunController
│   └── TriggerRegistrar # 동적 트리거 등록·해제
└── web/                 # Dashboard, Interface, Adapter, Run Controllers
```

---

## 어댑터 SPI

새 어댑터 추가 시 `com.eai.adapter.spi.Adapter` 인터페이스를 구현하고 `@Component`로 등록합니다.

```java
public interface Adapter {
    AdapterType type();
    Capabilities capabilities();
    BoundAdapter bind(String configJson);

    interface BoundAdapter extends AutoCloseable {
        Stream<DataRecord> read(AdapterContext ctx);
        WriteResult write(Stream<DataRecord> records, AdapterContext ctx);
        default void close() {}
    }
}
```

---

## 어댑터 설정 JSON 예시

**REST (소스 — 외부 사용자 목록 조회)**
```json
{"url": "https://jsonplaceholder.typicode.com/users", "method": "GET", "timeoutSec": 30}
```

**REST (타겟 — 외부 API로 데이터 전송)**
```json
{"url": "https://jsonplaceholder.typicode.com/posts", "method": "POST", "timeoutSec": 30}
```

**JDBC (읽기 — 신규 주문 조회)**
```json
{
  "url": "jdbc:postgresql://db.example.com:5432/shop",
  "username": "eai_reader",
  "password": "secret",
  "query": "SELECT order_id, customer_id, total_amount, status FROM orders WHERE status = 'NEW'"
}
```

**JDBC (쓰기 — 연동 이력 적재)**
```json
{
  "url": "jdbc:postgresql://db.example.com:5432/shop",
  "username": "eai_writer",
  "password": "secret",
  "writeSql": "INSERT INTO sync_log (order_id, synced_at, result) VALUES (?, NOW(), ?)",
  "writeParams": ["order_id", "result"]
}
```

**File (읽기 — CSV 업로드 파일 처리)**
```json
{"path": "./in/customers.csv", "format": "csv"}
```

**File (쓰기 — JSON 리포트 생성)**
```json
{"path": "./out/daily-report.json", "format": "json"}
```
`format`: `json` | `csv` | `xml` (생략 시 확장자 자동 감지)

**Kafka (타겟 — 주문 이벤트 발행)**
```json
{"topic": "order-created", "groupId": "eai-group", "keyField": "order_id"}
```

**Kafka (소스 — 결제 완료 이벤트 수신)**
```json
{"topic": "payment-completed", "groupId": "eai-group"}
```
`keyField`: 레코드의 해당 필드 값을 메시지 key로 사용 (쓰기 전용)

---

## 트리거 설정 JSON 예시

```json
// MANUAL — 즉시 수동 실행
{"type": "MANUAL"}

// CRON — 매일 오전 9시 정기 배치
{"type": "CRON", "cron": "0 0 9 * * *"}

// EVENT — Kafka 결제 완료 이벤트 수신 시 실행
{"type": "EVENT", "topic": "payment-completed", "groupId": "eai-group"}

// FILE 감지
{"type": "FILE", "watchPath": "./in"}
```

---

## 매핑 엔진

JSONPath 기반 필드 매핑 규칙을 배열로 정의합니다.

```json
[
  {"source": "$.user.firstName", "target": "$.name", "transform": "concat", "args": ["$.user.lastName", " "]},
  {"source": "$.user.email",     "target": "$.email", "transform": "lowercase"},
  {"target": "$.origin",         "transform": "constant", "args": ["eai-platform"]}
]
```

**내장 변환 함수**

| 함수 | 설명 |
|------|------|
| `identity` | 변환 없이 그대로 |
| `uppercase` / `lowercase` | 대소문자 변환 |
| `trim` | 앞뒤 공백 제거 |
| `toString` / `toNumber` | 타입 변환 |
| `concat` | 다른 필드 또는 문자열과 연결 |
| `substring` | 부분 문자열 추출 |
| `dateFormat` | 날짜 포맷 변환 |
| `constant` | 고정값 삽입 |
| `default` | 값이 없을 때 기본값 사용 |

새 변환 함수 추가: `TransformFn` 인터페이스를 구현한 `@Component` 작성 시 자동 등록됩니다.

---

## 로컬 실행

```bash
# Java 21 필요
./gradlew bootRun
```

| 엔드포인트 | URL |
|------------|-----|
| 웹 콘솔 | http://localhost:8080 |
| H2 콘솔 | http://localhost:8080/h2-console |
| 헬스 체크 | http://localhost:8080/actuator/health |
| 실행 메트릭 | http://localhost:8080/actuator/metrics/eai.runs.total |

H2 접속 정보: JDBC URL `jdbc:h2:file:./data/eai;AUTO_SERVER=TRUE;MODE=PostgreSQL` / Username `sa` / Password 없음

---

## 배포 (Render + Docker)

```yaml
# render.yaml
services:
  - type: web
    name: spring-app
    env: docker
    dockerfilePath: ./Dockerfile
    plan: free
```

```dockerfile
# Dockerfile
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew build -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/build/libs/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xms64m", "-Xmx300m", "-jar", "app.jar"]
```

**Kafka 연동 환경변수 (Render Environment 탭)**

| Key | 설명 |
|-----|------|
| `KAFKA_API_KEY` | Confluent Cloud API Key (Global access) |
| `KAFKA_API_SECRET` | Confluent Cloud API Secret |

---

## 모니터링

Spring Actuator + Micrometer 기반 메트릭을 제공합니다.

| 메트릭 | 설명 |
|--------|------|
| `eai.runs.total` | 전체 실행 횟수 |
| `eai.runs.failed` | 실패 실행 횟수 |
| `eai.runs.duration` | 실행 소요 시간 분포 |
