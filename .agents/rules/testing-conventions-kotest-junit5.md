---
trigger: model_decision
description: "Testing conventions, runner allocation, and assertion standards across Clean Architecture layers with Kotest and JUnit 5."
---

# Testing Conventions & Standards (Kotest & JUnit 5)

## 1. Multi-Layer Testing Architecture

Kronyka strictly partitions test runners and assertion libraries across Clean Architecture boundaries:

| Layer | Test Scope | Runner & Framework | Spec / Method Style | Assertion Library | Mocking / DB Strategy |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Domain** (`domain/`) | Pure Unit Test | **Kotest** | `BehaviorSpec` (BDD use cases)<br>`FunSpec` (pure math & hashing) | **Kotest Assertions** (`shouldBe`, `shouldThrow`) | **In-Memory Fake Repositories** (`mutableMapOf`). Zero framework mocks. |
| **Presentation** (`presentation/`) | Web Slice Test | **JUnit 5** + Spring Test | JUnit 5 `@Test`, `@Nested` | **Kotest Assertions** (`shouldBe`, `shouldNotBeNull`) | **MockMvc** + **MockK** (`@MockkBean` for Domain Services). |
| **Infrastructure** (`infrastructure/`) | Persistence & Integration | **JUnit 5** + Spring / Exposed | JUnit 5 `@Test`, `@BeforeEach` | **Kotest Assertions** (`shouldBe`, `shouldHaveSize`) | **H2 Database** / **Testcontainers PostgreSQL** with rollback transactions. |

---

## 2. Layer-Specific Testing Rules

### 2.1. Domain Layer (`domain/`)
- **Pure Kotest Execution**: Domain tests MUST NOT boot Spring context (`@SpringBootTest`, `@WebMvcTest` are strictly prohibited).
- **Spec Style Allocation**:
  - `BehaviorSpec`: Use for auction lifecycle orchestrators (`VickreyAuctionService`, `DutchAuctionService`) using BDD `Given` -> `When` -> `Then` hierarchy.
  - `FunSpec` / `StringSpec`: Use for stateless mathematical engines and hashing (`matchesBlindHash`, step-decay formula).
- **Test Doubles**: Implement in-memory fake repositories (`FakeAuctionRepository`, `FakeVickreyBidRepository`) backed by `mutableMapOf`. Avoid heavy mocking frameworks in domain unit tests.

### 2.2. Presentation Layer (`presentation/`)
- **JUnit 5 Runner Mandatory**: DO NOT use Kotest Spec runners (`BehaviorSpec`, etc.) for Spring Web slices. Always use JUnit 5 test classes.
- **Spring Web Slice Configuration**: Use `@WebMvcTest(controllers = [...])` with `MockMvc`.
- **Annotation Import Discipline**:
  - ✅ `org.junit.jupiter.api.Test` (JUnit 5 Jupiter)
  - ❌ NEVER import `org.junit.Test` (legacy JUnit 4).
  - ❌ NEVER import `io.kotest.core.spec.style.AnnotationSpec.Test`.
- **Mocking Strategy**: Mock domain service ports using MockK (`@MockkBean`).

### 2.3. Infrastructure Layer (`infrastructure/`)
- **JUnit 5 Runner Mandatory**: Persistence tests MUST use JUnit 5 (`@Test`) to seamlessly cooperate with Spring Data / Exposed transaction boundaries and rollback mechanisms.
- **Exposed DSL Verification**: Test real SQL execution (`selectAll().where`, `insert`, `upsert` on conflict) against in-memory H2 or Testcontainers PostgreSQL.
- **Transaction Rollback**: Wrap repository test operations in transaction blocks to maintain database cleanliness across test runs.

---

## 3. Universal Assertion Standard: Kotest Assertions Everywhere

Across ALL test files in ALL layers, **Kotest Assertions** are the standard:

- Use infix and fluent extensions:
  - `actual shouldBe expected` (instead of JUnit `assertEquals(expected, actual)`)
  - `actual.shouldNotBeNull()` (enables Kotlin compiler smart-casting)
  - `shouldThrow<ExpectedException> { ... }`
  - `list shouldHaveSize n`
  - `hash shouldHaveLength 64`
- Use `assertSoftly` for inspecting multiple fields of a response or entity without short-circuiting on the first failure.
