---
trigger: model_decision
description: "Clean Architecture layer boundaries, dependency inversion, and package structure rules for Kronyka."
---

# Clean Architecture Guidelines for Kronyka

## 1. Architectural Philosophy
Kronyka follows strict Clean Architecture (Hexagonal / Ports & Adapters) principles. The inner business core must remain independent of external frameworks, libraries, databases, and UI/HTTP protocols.

## 2. Package Organization

```
example.kronyka/
├── domain/
│   ├── model/           # Pure entities, value objects, enums, domain exceptions
│   ├── repository/      # Repository interfaces (outbound ports)
│   └── service/         # Domain use case services & business workflow engines
├── presentation/
│   ├── controller/      # Spring WebMVC REST Controllers (inbound adapters)
│   ├── dto/             # Request & Response DTOs, Bean Validation
│   └── advice/          # GlobalExceptionHandler, error response contracts
└── infrastructure/
    ├── persistence/     # Exposed SQL DSL Table definitions & repository adapters
    ├── security/        # Spring Security, JWT token provider, filters
    └── config/          # Spring configuration beans, Exposed configuration
```

## 3. Layer Separation Invariants

### 3.1. Domain Layer (`domain`)
- **Zero Framework Contamination**: MUST NOT import Spring annotations (e.g. `@Service`, `@Component`, `@Autowired`, `@RestController`, `@Transactional`), Exposed (`org.jetbrains.exposed.*`), or JDBC (`java.sql.*`).
- **Purity**: Domain entities and services are pure Kotlin classes.
- **Dependency Inversion**: Domain layer defines repository interfaces (e.g., `AuctionRepository`, `UserRepository`, `VickreyBidRepository`).
- Domain logic must be 100% testable via plain JUnit 5 unit tests without starting Spring or an in-memory database.

### 3.2. Presentation Layer (`presentation`)
- Interacts with HTTP clients.
- Translates JSON payloads to validated DTOs using `jakarta.validation.constraints`.
- Calls domain services to execute business actions.
- Maps domain entity outputs to clean Response DTOs.
- Never passes raw Exposed rows or SQL constructs to callers.

### 3.3. Infrastructure Layer (`infrastructure`)
- Implements domain repository interfaces (`domain/repository/*`) using Exposed SQL DSL in `infrastructure/persistence/*`.
- Manages Spring bean definitions (`@Service`, `@Repository`, `@Configuration`).
- Implements security filters and JWT validation in `infrastructure/security/*`.
- Encapsulates database transactions (`transaction { ... }` or Spring transaction boundaries).
