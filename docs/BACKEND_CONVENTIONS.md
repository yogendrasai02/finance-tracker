# Backend Coding Conventions

This document defines the coding standards for the Java Spring Boot backend.
All AI agents and developers must strictly adhere to these practices.

---

## 1. Java Language & Imports

### 1.1 Imports
- Wildcard imports are forbidden.
- Never use `import java.util.*;` or `import org.springframework.web.bind.annotation.*;`.
- Every import statement must name the imported class explicitly.
- Keep imports ordered: standard Java libraries first, third-party libraries second, internal project packages third.

### 1.2 Modern Java Baseline
- Target Java 25.
- Use `record` for immutable data transfer objects (DTOs), API responses, and query projections.
- Use pattern matching (`switch`, `instanceof`) and modern switch expressions where applicable.
- Mark fields and local variables as `final` when they are not mutated.

---

## 2. Lombok Usage & Dependency Injection

### 2.1 Dependency Injection on Spring Beans
- Do not use `@RequiredArgsConstructor` for Spring dependency injection on `@Service`, `@RestController`, `@Component`, or `@Repository` classes.
- Write explicit constructors with `final` fields for all injected dependencies:
```java
@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }
}
```
- Explicit constructors keep dependencies visible in plain code and simplify plain unit testing.

### 2.2 Logging
- Always use `@Slf4j` from Lombok instead of writing `private static final Logger log = LoggerFactory.getLogger(...)`.

### 2.3 DTOs, POJOs, and Test Fixtures
- Use Lombok freely on non-entity classes to reduce boilerplate.
- Use `@Data`, `@AllArgsConstructor`, `@NoArgsConstructor`, and `@Builder` on mutable POJOs, internal transfer models, and test fixtures.

### 2.4 JPA Entities
- Never place `@Data` or `@EqualsAndHashCode` directly on `@Entity` classes.
- Default Lombok `equals()` and `hashCode()` inspect all fields, which forces lazy associations to load and breaks `HashSet` / `HashMap` collections.
- Use `@Getter`, `@Setter`, and `@NoArgsConstructor(access = AccessLevel.PROTECTED)`.
- If implementing `equals()` and `hashCode()`, include only the database ID or immutable business key with `onlyExplicitlyIncluded = true`:
```java
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Account {

    @Id
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID id;

    // fields and relations
}
```

---

## 3. Package Structure: Package-by-Feature

The backend uses a package-by-feature layout to keep high cohesion within business domains.

```text
com.financetracker
├── account
│   ├── Account.java
│   ├── AccountController.java
│   ├── AccountDto.java
│   ├── AccountRepository.java
│   └── AccountService.java
├── category
├── transaction
├── statement
└── common
    ├── config
    ├── error
    └── security
```

### 3.1 Cross-Feature Interactions
When an operation spans multiple domain features, follow these four rules:

1. **Service-to-Service Calls:** Feature A can call the public service of Feature B. Feature A must never call Feature B's repository directly.
2. **Package-Private Encapsulation:** Repositories, internal mappers, and internal entities should remain package-private whenever possible.
3. **Dedicated Orchestration Services:** When an operation coordinates multiple features (such as statement import coordinating accounts, raw files, and transactions), create an orchestrator in the workflow package (e.g., `StatementImportService`).
4. **No Circular Dependencies:** Package dependencies must flow in one direction only. If Feature A and Feature B need each other, decouple them using Spring Application Events (`ApplicationEventPublisher` and `@EventListener`), or extract the shared concept into `common`.

### 3.2 Shared Code
Place true cross-cutting components in `com.financetracker.common`:
- Tenant context (`CurrentTenantContext`).
- Global error handling and RFC 7807 problem details.
- Common security helpers and shared base types.

---

## 4. Service Layer Design

### 4.1 Direct Concrete Classes by Default
- Create direct `@Service` classes without an interface (e.g., `AccountService`).
- Do not create 1:1 interfaces with `Impl` classes when only one implementation exists.
- Spring Boot uses CGLIB class-based proxies by default, and Mockito easily mocks concrete classes.

### 4.2 Interfaces When Polymorphism Exists
- Introduce interfaces only when multiple implementations exist.
- Example: `StatementParser` interface with `HdfcStatementParser` and `SbiStatementParser` implementations.

---

## 5. Controller & REST API Layer

### 5.1 Endpoints and URLs
- Use RESTful naming conventions: plural nouns and kebab-case paths (`/api/v1/accounts`, `/api/v1/import-batches`).
- Group endpoints by version prefix (`/api/v1`).

### 5.2 Return Types
- Return `ResponseEntity<T>` when explicitly controlling status codes or headers (e.g., `ResponseEntity.created(location).body(dto)` or `ResponseEntity.noContent().build()`).
- Return the DTO directly with `@ResponseStatus` for standard `200 OK` operations.
- Never return JPA entities from controller methods. Always map to response DTO records.

### 5.3 Input Validation
- Validate all incoming request payloads at the boundary using Jakarta Validation annotations (`@Valid`, `@NotNull`, `@NotBlank`, `@PositiveOrZero`, `@Size`).
- Place validation annotations directly on record components or DTO fields.

### 5.4 Centralized Error Handling
- Use a single `@RestControllerAdvice` class to handle exceptions globally.
- Format all error responses as RFC 7807 Problem Details (`ProblemDetail`).
- Do not write manual try-catch blocks in controller methods to return ad-hoc error maps.

---

## 6. Database & Persistence Layer

### 6.1 Schema-First Discipline
- Flyway migrations own the database schema (`db/migration/V*`).
- JPA entities map to the database schema.
- Always set `spring.jpa.hibernate.ddl-auto: validate` in application configuration.
- Hibernate must never alter or create schema objects.

### 6.2 Transaction Demarcation
- Place `@Transactional` on public service methods.
- Mark read-only queries with `@Transactional(readOnly = true)` to optimize dirty checking.
- Do not place `@Transactional` on controller methods or repository methods.

### 6.3 Relationship Fetching
- Always specify `FetchType.LAZY` on `@ManyToOne` and `@OneToMany` relationships.
- Avoid N+1 queries by using `JOIN FETCH`, `@EntityGraph`, or DTO projections in repository queries.

### 6.4 Row-Level Security Awareness
- Every connection executing domain queries under the `ft_app` role must set `SET LOCAL app.user_id = ?`.
- Repositories and connection pools must enforce tenant context before querying domain tables.

---

## 7. Financial Domain & Data Types

### 7.1 Monetary Amounts
- Always represent money as signed integer paise using `long` or `Long` (`amountPaise`).
- Never use `float`, `double`, or unscaled `BigDecimal` for monetary amounts.
- Follow the sign convention defined in [DATA_MODEL.md](docs/DATA_MODEL.md).

### 7.2 Temporal Data
- Use `Instant` for UTC audit timestamps (`created_at`, `updated_at`).
- Use `LocalDate` for statement transaction dates (`txn_date`).
- Never use `java.util.Date`, `java.util.Calendar`, or naive `LocalDateTime` for financial timestamps.

---

## 8. Testing Standards

### 8.1 Assertion Library
- Use AssertJ (`assertThat(...)`) as the exclusive assertion library.
- Do not use JUnit assertions (`assertEquals`, `assertTrue`).

### 8.2 Spring Boot Mocking Annotations
- Use `@MockitoBean` (standard in Spring Boot 3.4+ and 4.x) instead of the deprecated `@MockBean`.
- Use `@MockitoSpyBean` instead of `@SpyBean`.

### 8.3 Test Slices & Boundaries
- **Unit Tests:** Use JUnit 5 and Mockito for service and domain logic without loading the Spring ApplicationContext.
- **Web Slice Tests:** Use `@WebMvcTest` and `MockMvc` for HTTP routing, request validation, and serialization.
- **Database / Integration Tests:** Use Testcontainers with the exact `postgres:18-alpine` Docker image and Flyway migrations applied. Never use H2.

### 8.4 Test Structure and Naming
- Name test methods clearly using `should<DoSomething>When<Condition>()`.
- Examples:
  - `shouldCreateAccountWhenPayloadIsValid()`
  - `shouldReturn400WhenAmountPaiseIsNegative()`
  - `shouldRejectDuplicateImportHash()`
- Structure test methods using clear Given / When / Then blocks.
