# EAI 플랫폼 구축 세션 기록

- **세션 ID:** a7dd1fec-82ad-4b4b-8a14-5be68263fdfb
- **날짜:** 2026-04-22
- **프로젝트 디렉토리:** `/Users/seongnamkung/vibe/EAI`
- **사용자 요청:** "여러 시스템 간의 인터페이스를 관리하는 프로그램을 만들고 싶어"
- **원본 트랜스크립트:** `./transcript.jsonl` (373 라인, JSONL)

---

## 1. 요구사항 (사용자 확답)

| 항목 | 선택 |
| --- | --- |
| 인터페이스 유형 | REST/HTTP, 데이터베이스, 파일 (CSV/XML/JSON), 메시지 큐 (Kafka/RabbitMQ 등) |
| 사용 목적 | 풀 EAI 플랫폼 (정의 + 실행 + 모니터링) |
| 기술 스택 | Java / Spring Boot |
| UI 형태 | 웹 기반 관리 콘솔 |
| 트리거 | Cron, 이벤트(MQ), 수동/API, 파일 감지 |
| 데이터 변환 | 웹 UI 필드 매핑 (드래그앤드롭) |
| DB | H2 (개발용 임베디드) |
| MVP 범위 | 전체 어댑터 포함 |

---

## 2. 최종 설계 결정

승인된 계획 파일: `~/.claude/plans/deep-humming-porcupine.md`

| 항목 | 최종 결정 |
| --- | --- |
| 프레임워크 | **Spring Boot 3.3.5 + Java 21** |
| 빌드 | **Gradle 9.0 (Kotlin DSL), 단일 모듈** |
| DB | **H2 embedded** (파일 기반, AUTO_SERVER, PostgreSQL 모드) |
| ORM | Spring Data JPA, `ddl-auto=update` |
| 웹 UI | **Thymeleaf + 바닐라 JS (매핑 캔버스)** |
| JSONPath | Jayway JsonPath 2.9 |
| MQ (MVP) | **Kafka만** (RabbitMQ는 같은 SPI로 Phase 2 확장) |
| XML/CSV | Jackson XML + commons-csv |
| 비동기 | `@Async` + `ThreadPoolTaskExecutor` (core=4, max=16, queue=100) |

### 패키지 구조

```
com.eai
├── EaiApplication
├── config/              # AsyncConfig, DemoSeed
├── domain/              # JPA 엔티티 + 리포지토리
├── catalog/             # CatalogService (CRUD)
├── adapter/
│   ├── spi/             # Adapter, AdapterFactory, DataRecord, ...
│   ├── rest/            # RestAdapter (java.net.http)
│   ├── jdbc/            # JdbcAdapter
│   ├── file/            # FileAdapter (CSV/JSON/XML)
│   └── mq/              # KafkaAdapter
├── mapping/             # MappingEngine, MappingRule, TransformRegistry
├── execution/           # ExecutionEngine (@Async)
├── trigger/
│   ├── cron/            # CronTriggerRegistry
│   ├── event/           # KafkaListenerRegistry
│   ├── file/            # FileWatcherService (WatchService)
│   ├── manual/          # ManualRunController (REST)
│   └── TriggerRegistrar # 동적 재등록 허브
└── web/                 # Dashboard/Interface/Adapter/Run Controller + Thymeleaf
```

### 도메인 모델 (JPA)

- `interface_def` – 인터페이스 헤더 (name, active, current_version_id)
- `interface_version` – 버전별 어댑터·매핑·트리거 스냅샷 (JSON 컬럼 사용)
- `adapter_config` – 어댑터 인스턴스 + `config_json`
- `execution_run` – 실행 이력 (status, 카운트, 소요시간)
- `execution_log` – 실행 중 상세 로그 (phase, level, message)

JSON 컬럼에 어댑터 설정·매핑 규칙·트리거 설정을 저장해 스키마 폭발을 회피.

### 어댑터 SPI

```java
public interface Adapter {
  AdapterType type();
  Capabilities capabilities();
  BoundAdapter bind(String configJson);

  interface BoundAdapter extends AutoCloseable {
    Stream<DataRecord> read(AdapterContext ctx);
    WriteResult write(Stream<DataRecord> records, AdapterContext ctx);
  }
}
```

`Stream<DataRecord>` 기반의 자연스러운 backpressure. 매 실행마다 `bind()`로 새 인스턴스 생성 (상태·리소스 격리).

### 실행 엔진 흐름

모든 트리거가 `ExecutionEngine.start(interfaceId, TriggerType, meta)` 단일 엔트리로 수렴 → `@Async` 풀에서 `execute()` 실행:

1. 최신 `InterfaceVersion` 로드
2. source/target `AdapterFactory.require(...).bind(configJson)`
3. `ExecutionRun` RUNNING 저장
4. `source.read()` → `mappingEngine.apply()` → `target.write()` (lazy stream 파이프라인)
5. `ExecutionRun` 업데이트, `ExecutionLog` 기록, Micrometer 카운터 증가

### 매핑 규칙 포맷

```json
[
  {"source":"$.user.email","target":"$.customer.email","transform":"lowercase"},
  {"target":"$.source","transform":"constant","args":["eai-platform"]},
  {"source":"$.firstName","target":"$.fullName","transform":"concat","args":["$.lastName", " "]}
]
```

내장 트랜스폼: `identity, uppercase, lowercase, trim, toString, toNumber, concat, substring, dateFormat, constant, default`

### 트리거 동적 등록

`TriggerRegistrar`가 부팅 시 `@PostConstruct`와 `InterfaceChangedEvent`를 통해 Cron/Kafka/File 리스너를 동적으로 붙이고 떼어낸다. 앱 재시작 불필요.

---

## 3. 구현 상태 (세션 종료 시점)

모든 Phase 완료. 앱 부팅·엔드투엔드·Cron·FileWatcher·Kafka 리스너 graceful 동작까지 검증.

### Phase 0: 스켈레톤 ✅ **완료 — 빌드 성공**
`gradle build -x test` → `BUILD SUCCESSFUL in 38s`

### Phase 1: REST → File 엔드투엔드 ✅
- `SPRING_PROFILES_ACTIVE=demo gradle bootRun` → 2.98초 내 부팅
- `POST /api/interfaces/{id}/run` → **runId 즉시 반환, SUCCESS**
- httpbin.org/json → 매핑 `$.slideshow.title → $.title` → `./out/demo.json` 에 `[{"title":"Sample Slide Show"}]`
- Micrometer `eai.runs.total=1`, `eai.runs.duration=1.27s`

### Phase 2: File + FileWatcher ✅
- `./in/drop-test.json` 드롭 → ~12초 내 macOS WatchService 감지
- 매핑 3개 적용 (`orderId → id`, `customer uppercase`, `amount identity`)
- `./out/watcher-result.json` 생성 (입력 파일 보존됨)
- **중간 발견 버그 2건 즉시 수정:**
  1. `ExecutionEngine` 가 `trigger_meta` 를 `AdapterContext` 로 전달하지 않던 문제
  2. `FileAdapter` 가 read/write 모두 `triggerMeta.filePath` 를 써서 타겟이 입력을 덮어쓰던 문제 — `resolveReadPath/resolveWritePath` 로 분리

### Phase 3: JDBC + Cron ✅
- JDBC 어댑터 `SELECT 1 AS id, 'Alice' AS name UNION ...` 쿼리 (테이블 없이)
- Cron `*/5 * * * * *` 로 5초마다 자동 실행
- 3행을 읽어 `./out/cron-result.json` 에 기록
- 동적 재등록 확인 (인터페이스 저장 시 `CronTriggerRegistry` 가 새 스케줄 등록 로그)

### Phase 4: Kafka EVENT ✅ (broker 없이 graceful)
- Kafka EVENT 트리거 등록 성공
- broker 부재 상태에서 WARN 로깅만 반복, **앱 크래시 없음**
- 실제 메시지 소비는 local Kafka broker + `docker-compose up` 으로 재검증 필요

### 최종 메트릭
`eai.runs.total=21`, `eai.runs.failed=12` (초기 JDBC 설정 오타 기간 동안 누적된 실패), 성공 9건.

### 구축된 파일 (41개)

**Build / 설정**
- `build.gradle.kts`, `settings.gradle.kts`
- `src/main/resources/application.yml`

**애플리케이션 엔트리포인트**
- `src/main/java/com/eai/EaiApplication.java`

**도메인 엔티티 + 리포지토리 (11개)**
- `domain/AdapterType.java`, `AdapterDirection.java`, `RunStatus.java`, `TriggerType.java`
- `domain/AdapterConfigEntity.java`, `InterfaceDef.java`, `InterfaceVersion.java`, `ExecutionRun.java`, `ExecutionLog.java`
- `domain/AdapterConfigRepository.java`, `InterfaceDefRepository.java`, `InterfaceVersionRepository.java`, `ExecutionRunRepository.java`, `ExecutionLogRepository.java`

**어댑터 SPI + 4개 어댑터**
- `adapter/spi/Adapter.java`, `AdapterFactory.java`, `DataRecord.java`, `AdapterContext.java`, `WriteResult.java`, `Capabilities.java`
- `adapter/rest/RestAdapter.java` (java.net.http 기반)
- `adapter/jdbc/JdbcAdapter.java` (스트리밍 ResultSet, 파라미터 writeSql)
- `adapter/file/FileAdapter.java` (CSV/JSON/XML 읽기·쓰기)
- `adapter/mq/KafkaAdapter.java` (Producer 쓰기, triggerMeta 읽기)

**매핑**
- `mapping/MappingRule.java`, `TransformFn.java`, `TransformContext.java`, `TransformRegistry.java`, `MappingEngine.java`

**실행 엔진**
- `execution/ExecutionEngine.java` (@Async, Micrometer)

**트리거 4종**
- `trigger/TriggerConfig.java`, `TriggerRegistrar.java`
- `trigger/cron/CronTriggerRegistry.java`
- `trigger/event/KafkaListenerRegistry.java`
- `trigger/file/FileWatcherService.java`
- `trigger/manual/ManualRunController.java`

**카탈로그 + 웹 컨트롤러**
- `catalog/CatalogService.java`
- `web/DashboardController.java`, `InterfaceController.java`, `AdapterController.java`, `RunController.java`

**설정 / demo seed**
- `config/AsyncConfig.java`, `config/DemoSeed.java`

**Thymeleaf 템플릿 + 정적 리소스**
- `templates/layout.html`, `dashboard.html`, `error.html`
- `templates/interfaces/list.html`, `edit.html`
- `templates/adapters/list.html`, `edit.html`
- `templates/runs/list.html`, `detail.html`
- `static/css/app.css`
- `static/js/mapping-canvas.js`

---

## 4. 실행 방법

```bash
cd /Users/seongnamkung/vibe/EAI

# 일반 부팅
./gradlew bootRun
# 또는 demo 데이터와 함께
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun

# 콘솔:   http://localhost:8080
# H2:    http://localhost:8080/h2-console
#   JDBC URL: jdbc:h2:file:./data/eai;AUTO_SERVER=TRUE;MODE=PostgreSQL
#   User: sa  Password: (비어있음)
```

---

## 5. 검증 계획

| 시나리오 | 방법 |
| --- | --- |
| Manual REST → File | 콘솔에서 httpbin.org 소스 + `./out` 타겟 → "Run now" |
| Cron DB → REST | H2 `customers` 시드, cron `*/30 * * * * *` |
| File-watcher → JDBC | `./in/` 감시, CSV 드롭 |
| Kafka → File | 로컬 Kafka, console-producer 메시지 |
| Error path | 타겟을 잘못된 URL로 → FAILED + ERROR 로그 |
| Actuator | `/actuator/health`, `/actuator/metrics/eai.runs.total` |

---

## 6. 다음 단계

Phase 0~4 완료, README 작성 완료. 남은 업그레이드 후보:

1. **로컬 Kafka 브로커로 이벤트 소비 실증** (docker compose)
2. **드래그앤드롭 매핑 캔버스 UX 업그레이드** — 현재는 리스트 기반 편집기, 향후 jsPlumb + SVG 선으로 소스·타겟 필드 시각 연결
3. **대시보드 위젯 강화** — 24h 성공/실패 차트, 어댑터별 처리량
4. **인증/권한** — Spring Security 도입
5. **RabbitMQ / SFTP 어댑터** — 기존 SPI 에 추가 확장
6. **Flyway 마이그레이션 도입** — 현재 `ddl-auto=update` 를 production-safe로 대체

---

## 파일 목록

- `SESSION.md` — 이 문서 (사람이 읽는 요약)
- `transcript.jsonl` — Claude Code 세션 원본 전체 (모든 메시지·툴 호출·결과 포함)
