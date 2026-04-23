# NOAATS-EAI — Enterprise Application Integration Platform

> 이기종 시스템 간 데이터 연계를 웹 UI에서 설정·실행·모니터링하는 Spring Boot 기반 EAI 플랫폼

---

## 기획 배경

기업 환경에서 시스템 간 데이터 연계는 반복적으로 발생하지만, 매번 개발자가 직접 연동 코드를 작성해야 하는 비효율이 존재합니다.  
NOAATS-EAI는 **어댑터·인터페이스·트리거를 웹 UI에서 조합**하여, 코드 변경 없이 시스템 간 데이터 흐름을 정의하고 운영할 수 있는 플랫폼입니다.

---

## 핵심 기능

### 어댑터 (Adapter)
다양한 프로토콜·저장소를 단일 SPI(`Adapter` 인터페이스)로 추상화합니다.

| 타입 | 설명 |
|------|------|
| **REST** | HTTP GET/POST, JSON 응답 자동 파싱 |
| **JDBC** | 스트리밍 ResultSet 읽기, SQL INSERT/UPDATE 쓰기 |
| **File** | CSV / JSON / XML 읽기·쓰기 (확장자 자동 감지) |
| **Kafka** | Confluent Cloud 포함 Kafka 토픽 발행·구독 |

### 트리거 (Trigger)
인터페이스 실행 시점을 네 가지 방식으로 정의합니다.

| 타입 | 설명 |
|------|------|
| **MANUAL** | 웹 UI에서 즉시 실행 |
| **CRON** | Spring 스케줄러 기반 주기 실행 |
| **EVENT** | Kafka 토픽 메시지 수신 시 자동 실행 |
| **FILE** | 지정 폴더에 파일 생성 감지 시 실행 |

### 매핑 엔진 (Mapping Engine)
JSONPath 기반 필드 매핑 규칙을 웹 UI에서 정의하며, 변환 함수를 체이닝할 수 있습니다.

```json
[
  { "source": "$.user.firstName", "target": "$.name", "transform": "concat", "args": ["$.user.lastName", " "] },
  { "source": "$.user.email",     "target": "$.email", "transform": "lowercase" },
  { "target": "$.origin",         "transform": "constant", "args": ["eai-platform"] }
]
```

내장 변환 함수: `identity` · `uppercase` · `lowercase` · `trim` · `toString` · `toNumber` · `concat` · `substring` · `dateFormat` · `constant` · `default`  
`TransformFn` 인터페이스를 구현한 `@Component`를 추가하면 자동 등록됩니다.

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

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.3, Spring Data JPA, Spring Kafka |
| View | Thymeleaf, Vanilla JS |
| Database | H2 (파일 기반, AUTO_SERVER) |
| Messaging | Apache Kafka (Confluent Cloud) |
| Monitoring | Spring Actuator, Micrometer |
| Build | Gradle (Kotlin DSL) |
| Deploy | Docker, Render |

---

## 화면 구성

| 메뉴 | 설명 |
|------|------|
| **Dashboard** | 기간별 실행 통계, 처리량 차트, 최근 실행 목록 |
| **Systems** | 연계 대상 시스템 등록·관리 |
| **Adapters** | 소스·타겟 어댑터 설정 (타입별 Config JSON) |
| **Interfaces** | 소스→타겟 연결, 매핑 규칙, 트리거 정의 |
| **Runs** | 전체 실행 이력 및 상세 로그 확인 |
| **Settings** | 플랫폼 전역 설정 |

---

## 실행 방법

### 로컬 실행

```bash
# Java 21 필요
./gradlew bootRun
```

- 웹 콘솔: http://localhost:8080
- H2 콘솔: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:file:./data/eai;AUTO_SERVER=TRUE;MODE=PostgreSQL`
  - Username: `sa` / Password: (없음)
- 헬스 체크: http://localhost:8080/actuator/health

### 빠른 사용 흐름

1. **Systems** → 시스템 등록
2. **Adapters** → 소스·타겟 어댑터 등록
3. **Interfaces** → 소스·타겟 연결, 트리거·매핑 설정
4. **Run Now** → 즉시 실행
5. **Runs** → 실행 결과 및 로그 확인

### 트리거 설정 JSON 예시

```json
// MANUAL
{"type": "MANUAL"}

// CRON (매 5분)
{"type": "CRON", "cron": "0 */5 * * * *"}

// Kafka EVENT
{"type": "EVENT", "topic": "orders", "groupId": "eai-group"}

// FILE 감지
{"type": "FILE", "watchPath": "./in"}
```

---

## 배포 (Render)

Docker 기반으로 Render에 배포합니다.

```yaml
# render.yaml
services:
  - type: web
    name: spring-app
    env: docker
    dockerfilePath: ./Dockerfile
    plan: free
```

Kafka 연동 시 Render 대시보드 **Environment** 탭에 아래 환경변수를 설정합니다.

| Key | 설명 |
|-----|------|
| `KAFKA_API_KEY` | Confluent Cloud API Key |
| `KAFKA_API_SECRET` | Confluent Cloud API Secret |

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
│   ├── rest/            # RestAdapter
│   ├── jdbc/            # JdbcAdapter
│   ├── file/            # FileAdapter
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
