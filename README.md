# Java REST API Log Analyzer & SLA Ticket Simulator

> **Production-style Java 17 CLI application** that analyzes REST API logs, detects HTTP 4xx/5xx errors and SQL exceptions, performs root cause classification, and simulates a full SLA-driven incident ticket lifecycle.

---

## Architecture

```
java-rest-log-analyzer-ticket-simulator/
├── pom.xml                                      # Maven build (Java 17, Jackson, SLF4J, JUnit 5)
├── sample_logs/
│   ├── api_logs.json                            # JSON log sample (17 entries, all error types)
│   ├── application.log                          # Plain-text log sample (20 entries)
│   ├── db_errors.json                           # DB-error focused JSON sample
│   └── mixed_logs.json                          # Mixed JSON sample
└── src/main/java/com/loganalyzer/
    ├── models/
    │   ├── LogEntry.java          # JSON-deserializable log entry model
    │   ├── Ticket.java            # Full lifecycle ticket with SLA calculation
    │   ├── ErrorType.java         # 24 error types with root cause descriptions
    │   ├── Priority.java          # Priority levels (Low/Medium/High/Critical + SLA hours)
    │   └── TicketStatus.java      # Status FSM with valid transition enforcement
    ├── utils/
    │   ├── LogParser.java         # Auto-detect JSON/plain-text; regex + Jackson parsing
    │   └── RootCauseClassifier.java  # Rule-based HTTP & DB error classification
    ├── services/
    │   ├── LogAnalyzerService.java        # Aggregation, grouping, report generation
    │   ├── TicketService.java             # CRUD, status transitions, queries
    │   └── IncidentManagementService.java # Orchestrates log → ticket → SLA pipeline
    ├── sla/
    │   └── SlaComplianceEngine.java  # Per-priority compliance metrics & breach detection
    └── cli/
        └── MainCLI.java              # Interactive menu-driven CLI (4 modules)
```

### Component Flow

```
Log File (JSON / plain-text)
        │
        ▼
   LogParser  ──────────────────────►  List<LogEntry>
        │                                    │
        ▼                                    ▼
RootCauseClassifier              LogAnalyzerService
  (classifies ErrorType)           (aggregates, reports)
        │                                    │
        └──────────────┬─────────────────────┘
                       ▼
        IncidentManagementService
          (auto-generates tickets)
                       │
                       ▼
              TicketService
         (lifecycle management)
                       │
                       ▼
          SlaComplianceEngine
         (breach detection, metrics)
```

---

## How to Run

### Prerequisites
- Java 17+
- Maven 3.8+

### Build

```bash
# Clone the repository (already done if you're reading this locally)
git clone https://github.com/nishantkr0904/java-rest-log-analyzer-ticket-simulator.git
cd java-rest-log-analyzer-ticket-simulator

# Build fat JAR
mvn clean package -q

# Run the CLI
java -jar target/java-rest-log-analyzer-ticket-simulator-1.0.0-jar-with-dependencies.jar
```

### Quick Demo Flow

```
1. Main Menu → [1] Log Analyzer → [1] Analyze log file
   → Press Enter to use default: sample_logs/api_logs.json

2. Log Analyzer → [4] Error frequency by type
   → See all detected error types and occurrence counts

3. Log Analyzer → [7] Generate full incident report
   → Full summary with root cause classification

4. Main Menu → [4] Incident Management → [1] Auto-generate tickets
   → System creates one ticket per unique error type detected

5. Main Menu → [3] SLA Compliance → [1] Run full SLA evaluation
   → Per-priority compliance metrics and breach summary

6. Main Menu → [2] Ticket Management → [2] List all tickets
   → View all tickets with SLA status
```

---

## Sample Input Logs

### JSON Format (`sample_logs/api_logs.json`)

```json
[
  {
    "timestamp": "2024-01-15 08:22:10",
    "level": "ERROR",
    "message": "NullPointerException in PaymentProcessor.charge",
    "statusCode": 500,
    "method": "POST",
    "endpoint": "/api/payments/charge",
    "service": "payment-service",
    "exception": "NullPointerException",
    "durationMs": 312
  },
  {
    "timestamp": "2024-01-15 08:25:33",
    "level": "ERROR",
    "message": "Database connection timeout - HikariPool exhausted",
    "statusCode": 500,
    "endpoint": "/api/products",
    "exception": "SQLException",
    "sqlState": "08001"
  }
]
```

### Plain-Text Format (`sample_logs/application.log`)

```
[2024-01-16 10:10:34] ERROR POST /api/auth/login    401 - Unauthorized: invalid JWT token
[2024-01-16 10:50:27] FATAL GET  /api/dashboard     500 - SQLException: connection pool exhausted
[2024-01-16 11:00:43] ERROR GET  /api/reports       504 - Gateway timeout: service did not respond
```

---

## Sample Output Report

```
════════════════════════════════════════════════════════════════════════
  INCIDENT ANALYSIS REPORT
  Generated: 2024-01-15 09:31:00
  Source File: api_logs.json
════════════════════════════════════════════════════════════════════════

  OVERVIEW
  ──────────────────────────────────────────────────────────────────────
  Total Log Entries              : 17
  Total Errors Detected          : 14
  HTTP Client Errors (4xx)       : 6
  HTTP Server Errors (5xx)       : 6
  Database / SQL Errors          : 4
  Other Errors                   : 0

  ERROR FREQUENCY BY TYPE
  ──────────────────────────────────────────────────────────────────────
  HTTP 500 Internal Server Error           :   3 occurrence(s)  [Root Cause: Unexpected server-side failure]
  SQL Deadlock Detected                    :   1 occurrence(s)  [Root Cause: Concurrent database transaction deadlock]
  Database Connection Failure              :   1 occurrence(s)  [Root Cause: Unable to establish database connection]
  HTTP 401 Unauthorized                    :   1 occurrence(s)  [Root Cause: Authentication token missing or invalid]
  ...

  ROOT CAUSE CLASSIFICATION SUMMARY
  ──────────────────────────────────────────────────────────────────────
  ► 4xx errors indicate client-side issues: bad requests, missing auth, or invalid resources.
  ► 5xx errors indicate server-side failures requiring immediate investigation.
  ► Database errors detected — check connection pools, credentials, and query syntax.
  ► 5 CRITICAL error(s) detected — auto-ticket generation recommended.
```

---

## SLA Calculation

| Priority | SLA Window | Use Case |
|----------|-----------|---------|
| **Critical** | 2 hours | Service-down incidents, DB connection failures |
| **High** | 8 hours | Auth failures, 5xx errors, rate limiting |
| **Medium** | 24 hours | 4xx conflicts, constraint violations |
| **Low** | 72 hours | 404s, bad requests, method not allowed |

**Breach detection** compares the ticket's `resolvedAt` timestamp (or `LocalDateTime.now()` for open tickets) against `createdAt + slaHours`. Metrics include:
- Overall compliance rate (%)
- Per-priority breach counts
- Average time to resolution (minutes)
- Critical breach alerts with breach duration

---

## Root Cause Classification

The `RootCauseClassifier` applies a **3-tier rule engine**:

1. **Database-first check** — SQL/DB exception class names and message keywords take precedence
   - `Connection refused`, `HikariPool timeout` → `DB_CONNECTION_FAILURE / DB_CONNECTION_TIMEOUT`
   - `deadlock`, `lock wait timeout` → `SQL_DEADLOCK`
   - `SQLSyntaxErrorException`, SQL state `42000` → `SQL_SYNTAX_ERROR`
   - `ConstraintViolationException` → `SQL_CONSTRAINT_VIOLATION`

2. **HTTP status code mapping** — exact match for all standard 4xx/5xx codes

3. **Message keyword fallback** — for entries without status codes

Each `ErrorType` carries a `defaultPriority`, `displayName`, and `rootCauseDescription` used to auto-populate tickets.

---

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Java 17 |
| Build | Maven 3.8+ |
| JSON Parsing | Jackson 2.15 |
| Logging | SLF4J + Logback |
| Testing | JUnit 5.10 |
| Distribution | Fat JAR via maven-assembly-plugin |

---

## Project Highlights (Resume Claims Realized)

- ✅ **Production-style modular architecture** — separate packages for `models`, `services`, `utils`, `sla`, `cli`
- ✅ **REST API log analysis** — parses JSON and plain-text logs; detects all HTTP 4xx/5xx and DB errors
- ✅ **Root cause classification** — deterministic 3-tier rule engine mapping errors to actionable causes
- ✅ **Automated ticket generation** — deduplicates and creates incident tickets from log error types
- ✅ **SLA-driven lifecycle** — per-priority SLA windows, breach detection, compliance rate reporting
- ✅ **Interactive CLI** — full menu-driven interface with input validation
- ✅ **Separation of concerns** — service-layer abstraction, model DTOs, utility helpers
- ✅ **Structured logging** — SLF4J + Logback with rolling file appender
- ✅ **Java SE 17** — switch expressions, records-ready, modern idioms

---

## Author

**Nishant Kumar** — [GitHub](https://github.com/nishantkr0904)
