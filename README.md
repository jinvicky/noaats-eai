# EAI — Enterprise Application Integration

여러 이기종 시스템 사이의 인터페이스를 **정의·실행·모니터링**하는 Spring Boot 기반 플랫폼.

## 특징

- **4개 어댑터**: REST/HTTP, JDBC, File (CSV/JSON/XML), Kafka
- **4가지 트리거**: 수동 (REST API/웹), Cron 스케줄, Kafka 이벤트, 폴더 파일 감지
- **JSONPath 기반 필드 매핑**: 웹 UI에서 규칙 편집, 런타임에 변환 함수(uppercase, concat, substring, dateFormat, constant 등) 적용
- **웹 관리 콘솔**: Thymeleaf로 서버 렌더, 대시보드·인터페이스·어댑터·실행 이력·상세 로그
- **H2 embedded DB**: 설정·실행 이력 영속화 (파일 기반, AUTO_SERVER)
- **Spring Actuator + Micrometer**: `/actuator/health`, `eai.runs.total` / `eai.runs.failed` / `eai.runs.duration`

## 요구 사항

- Java 21
- Gradle 8+ (wrapper 포함 아님 — `gradle` 전역 설치 사용. 필요 시 `gradle wrapper`로 생성)

## 실행

```bash
# 일반 부팅
gradle bootRun

# 데모 데이터(HTTPBin REST → 파일) 포함 부팅
SPRING_PROFILES_ACTIVE=demo gradle bootRun
```

- 웹 콘솔: http://localhost:8080/
- H2 콘솔: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/eai;AUTO_SERVER=TRUE;MODE=PostgreSQL`, user `sa`, 비밀번호 없음)
- 헬스: http://localhost:8080/actuator/health
- 메트릭: http://localhost:8080/actuator/metrics/eai.runs.total

## 빠른 워크플로

1. **/adapters** 로 이동 → "+ New" → 소스·타겟 어댑터 등록 (타입 선택 시 예시 JSON이 자동 채워짐)
2. **/interfaces** → "+ New" → 소스·타겟 어댑터 선택, 매핑 규칙 추가, 트리거 설정
3. **Run now** 로 수동 실행하거나, 트리거 설정에 따라 자동 실행 대기
4. **/runs** 에서 실행 이력·성공/실패·records read/written·상세 로그 확인

## 트리거 설정 예시

| 타입 | JSON |
| --- | --- |
| 수동 | `{"type":"MANUAL"}` |
| Cron (매 5초) | `{"type":"CRON","cron":"*/5 * * * * *"}` |
| Kafka 이벤트 | `{"type":"EVENT","topic":"orders","groupId":"eai"}` |
| 파일 감지 | `{"type":"FILE","watchPath":"./in"}` |

## 어댑터 설정 예시

**REST**
```json
{"url":"https://httpbin.org/json","method":"GET","headers":{},"timeoutSec":30}
```

**JDBC (읽기)**
```json
{"url":"jdbc:h2:mem:x","username":"sa","password":"",
 "query":"SELECT id, name FROM customers"}
```

**JDBC (쓰기)**
```json
{"url":"jdbc:h2:mem:x","username":"sa","password":"",
 "writeSql":"INSERT INTO log(id,msg) VALUES (?,?)",
 "writeParams":["id","msg"]}
```

**File**
```json
{"path":"./out/data.json","format":"json"}
```
`format`: `json` | `csv` | `xml` (생략 시 확장자 기반 자동 감지)

**Kafka**
```json
{"topic":"orders","groupId":"eai","keyField":"id"}
```
`keyField`: 레코드의 해당 필드 값을 메시지 key로 사용 (쓰기 전용)

## 매핑 규칙

`interface_version.mapping_rules` JSON 배열 (웹 매핑 편집기가 생성):

```json
[
  {"source":"$.user.firstName","target":"$.customer.name","transform":"concat","args":["$.user.lastName"," "]},
  {"source":"$.user.email","target":"$.customer.email","transform":"lowercase"},
  {"target":"$.source","transform":"constant","args":["eai-platform"]}
]
```

내장 트랜스폼: `identity` · `uppercase` · `lowercase` · `trim` · `toString` · `toNumber` · `concat` · `substring` · `dateFormat(pattern)` · `constant(value)` · `default(value)`

새 트랜스폼을 추가하려면 `com.eai.mapping.TransformFn` 을 구현하는 `@Component` 를 작성하면 자동 등록됩니다.

## 아키텍처 한 페이지

```
  Trigger                    Execution                     Adapter SPI
  ────────                   ─────────                     ────────────
  ManualRunController ─┐
  CronTriggerRegistry  ├──▶  ExecutionEngine ──▶ Adapter.bind(configJson)
  FileWatcherService   │     (@Async pool:         │
  KafkaListenerReg     ┘      4/16/100)            ├─ read() → Stream<DataRecord>
                                                    │         ↓
                                                    │    MappingEngine.apply()
                                                    │         ↓
                                                    └─ write(Stream) → WriteResult

  ExecutionRun (JPA) ◀─ 상태/카운트 ────┘
  ExecutionLog (JPA) ◀─ phase별 로그 ───┘
  Micrometer counters ◀────────────────┘
```

모든 트리거가 `ExecutionEngine.start(interfaceId, TriggerType, meta)` 로 수렴하고, 실행은 `@Async` ThreadPoolTaskExecutor 에서 수행됩니다. `Stream<DataRecord>` 파이프라인 덕분에 어댑터가 큰 데이터셋을 스트리밍·페이지 단위로 처리할 수 있습니다.

## 구현 상태 (2026-04-22)

- ✅ Phase 0 — 스켈레톤 + 빌드 성공
- ✅ Phase 1 — REST 어댑터, ExecutionEngine, 수동 트리거 엔드투엔드 (REST→File, 매핑 포함)
- ✅ Phase 2 — File 어댑터 (CSV/JSON/XML), FileWatcher 트리거 (macOS 기준 ~10s 폴링 지연)
- ✅ Phase 3 — JDBC 어댑터 (스트리밍 ResultSet), 동적 Cron 재등록
- ✅ Phase 4 — Kafka 어댑터 + EventListener 동적 등록 (broker 부재 시 graceful 경고)
- ⬜ Phase 5 — README ✅ / 대시보드 메트릭 위젯 / 드래그앤드롭 SVG 업그레이드 (현재는 리스트 기반 편집기)

## 레이아웃

```
src/main/java/com/eai/
├── EaiApplication.java
├── config/           # AsyncConfig, DemoSeed
├── domain/           # JPA 엔티티 + Repository
├── catalog/          # CatalogService
├── adapter/
│   ├── spi/          # Adapter SPI (DataRecord, AdapterContext, WriteResult, Capabilities, AdapterFactory)
│   ├── rest/         # RestAdapter (java.net.http)
│   ├── jdbc/         # JdbcAdapter (streaming ResultSet)
│   ├── file/         # FileAdapter (CSV/JSON/XML)
│   └── mq/           # KafkaAdapter
├── mapping/          # MappingEngine, TransformRegistry
├── execution/        # ExecutionEngine (@Async)
├── trigger/
│   ├── cron/         # CronTriggerRegistry
│   ├── event/        # KafkaListenerRegistry
│   ├── file/         # FileWatcherService
│   ├── manual/       # ManualRunController
│   └── TriggerRegistrar (동적 재등록)
└── web/              # Dashboard/Interface/Adapter/Run Controllers
src/main/resources/
├── application.yml
├── templates/        # Thymeleaf (layout, dashboard, interfaces/, adapters/, runs/, error)
└── static/
    ├── css/app.css
    └── js/mapping-canvas.js
```

## 알려진 제약 / TODO

- Kafka 소스 어댑터의 `read()` 는 `triggerMeta.payload` 를 통해 단건 레코드를 반환 — 실제 poll 은 `KafkaListenerRegistry` 가 담당하므로 Kafka 를 "수동/스케줄 트리거의 소스"로 쓰는 것은 권장되지 않음 (이벤트 트리거 전용)
- Path 작성 시 `$.a.b.c` 중간 세그먼트가 객체가 아닌 경우(배열/스칼라) 경로 덮어쓰기 정책이 정밀하지 않음
- 드래그앤드롭 매핑 캔버스는 리스트 편집 기반 (추후 SVG 라인 업그레이드 가능)
- 인증/권한 없음 (개발용)
- SFTP/FTP, RabbitMQ는 Phase 2로 이월

## 라이선스

사내 사용.
