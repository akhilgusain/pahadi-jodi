# 06 — Low-Level Design, Folder Structure & Coding Standards

> Assumed conventions (SHARED CONVENTIONS were not visible to this author): base package `com.pahadijodi.<service>`, library prefix `pj-`, Kafka topics `pj.<domain>.<event>.v1`, IDs are UUIDv7, all timestamps UTC `Instant`, money in minor units (paise) + ISO-4217 currency.

---

## A. Monorepo Folder Structure

```
pahadi-jodi/
├── .github/
│   ├── workflows/
│   │   ├── ci-service.yml            # reusable: build/test/scan/push per service
│   │   ├── ci-web.yml
│   │   ├── ci-mobile.yml
│   │   ├── terraform-plan-apply.yml
│   │   ├── security-scan.yml         # Trivy, Gitleaks, OWASP DC, CodeQL (nightly)
│   │   └── release-please.yml
│   ├── CODEOWNERS
│   └── pull_request_template.md
├── services/
│   ├── api-gateway/                  # Spring Cloud Gateway (reactive)
│   ├── identity-service/
│   ├── user-service/
│   ├── profile-service/
│   ├── matchmaking-service/
│   ├── search-service/
│   ├── chat-service/
│   ├── notification-service/
│   ├── subscription-service/
│   ├── payment-service/
│   ├── ai-recommendation-service/
│   ├── media-service/
│   ├── moderation-service/
│   ├── horoscope-service/
│   ├── analytics-service/
│   ├── admin-service/
│   └── fraud-detection-service/
│       └── (each service has the identical layout, see section B)
├── libs/
│   ├── pj-common-events/             # Avro/JSON-schema event contracts + envelope
│   │   └── src/main/{java,avro}/...  # EventEnvelope, topics, generated records
│   ├── pj-security-starter/          # JWT resource server, ABAC evaluator, PII masking
│   ├── pj-observability-starter/     # OTel, Micrometer tags, MDC trace filter, log masking
│   ├── pj-outbox-starter/            # Outbox entity, relay (poller + Debezium mode), inbox dedupe
│   └── pj-test-support/              # Testcontainers base classes, fixtures
├── apps/
│   ├── web/                          # React 19 + Vite (see section D)
│   ├── admin-web/                    # Admin console (same stack, separate bundle & auth realm)
│   └── mobile/                       # Expo (see section D)
├── infra/
│   ├── terraform/
│   │   ├── modules/{vpc,eks,rds,elasticache,msk,opensearch,s3-cdn,waf,iam-irsa,observability}/
│   │   └── envs/{dev,staging,prod}/{main.tf,backend.tf,terraform.tfvars}
│   ├── helm/
│   │   ├── charts/pj-service/        # one generic chart, per-service values
│   │   └── values/{dev,staging,prod}/<service>.yaml
│   ├── argocd/{projects,applicationsets,rollouts}/
│   ├── k8s/{network-policies,pod-security,external-secrets}/
│   └── observability/{prometheus-rules,grafana-dashboards,otel-collector,logstash-pipelines}/
├── docs/                             # 01..07 design docs, ADRs in docs/adr/NNNN-title.md
├── tools/{scripts,openapi-lint,schema-registry-compat}/
├── build.gradle.kts / settings.gradle.kts   # Gradle multi-project, version catalog libs.versions.toml
├── pnpm-workspace.yaml
└── Makefile                          # make up | test | lint | scan
```

---

## B. Hexagonal Layout for a Spring Boot Service (example: `profile-service`)

```
services/profile-service/src/main/java/com/pahadijodi/profile/
├── ProfileServiceApplication.java
├── domain/                           # PURE Java. No Spring, JPA, Jackson, Kafka.
│   ├── model/        Profile, ProfileId, NativePlace, Community, Horoscope, ...
│   ├── event/        ProfileCreated, ProfilePublished, ProfileUpdated, ...
│   ├── service/      domain services (e.g. ProfileCompletenessCalculator)
│   └── exception/    DomainException subclasses
├── application/
│   ├── port/
│   │   ├── in/       command & query use-case interfaces (CreateProfileUseCase ...)
│   │   └── out/      ProfileRepository, EventPublisher, MediaClient, Clock ...
│   ├── command/      CreateProfileCommand + handler (transactional)
│   ├── query/        read-side handlers (use read model, not aggregate)
│   └── projection/   ProfileReadModelProjector
├── adapter/
│   ├── in/
│   │   ├── web/      REST controllers, request/response DTOs, mappers, ProblemDetail advice
│   │   └── messaging/ Kafka listeners (idempotent)
│   └── out/
│       ├── persistence/ JPA entities, Spring Data repos, MapStruct mappers, outbox adapter
│       ├── messaging/   OutboxEventPublisher
│       └── client/      Feign/WebClient adapters with Resilience4j
└── config/               Spring @Configuration, security, Kafka, Jackson, Clock bean
src/main/resources/{application.yml, db/migration/V*.sql, logback-spring.xml}
src/test/java/...{unit, integration, architecture, contract}
```

### ArchUnit rules (enforced in CI)

```java
package com.pahadijodi.profile.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.pahadijodi.profile", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

  @ArchTest
  static final ArchRule layers = layeredArchitecture().consideringOnlyDependenciesInLayers()
      .layer("Domain").definedBy("..domain..")
      .layer("Application").definedBy("..application..")
      .layer("AdapterIn").definedBy("..adapter.in..")
      .layer("AdapterOut").definedBy("..adapter.out..")
      .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "AdapterIn", "AdapterOut")
      .whereLayer("Application").mayOnlyBeAccessedByLayers("AdapterIn", "AdapterOut")
      .whereLayer("AdapterIn").mayNotBeAccessedByAnyLayer()
      .whereLayer("AdapterOut").mayNotBeAccessedByAnyLayer();

  @ArchTest
  static final ArchRule domainIsFrameworkFree = noClasses().that().resideInAPackage("..domain..")
      .should().dependOnClassesThat().resideInAnyPackage(
          "org.springframework..", "jakarta.persistence..", "com.fasterxml..", "org.apache.kafka..");

  @ArchTest
  static final ArchRule adaptersDontTalkToEachOther = noClasses().that().resideInAPackage("..adapter.in..")
      .should().dependOnClassesThat().resideInAPackage("..adapter.out..");

  @ArchTest
  static final ArchRule noFieldInjection = noFields().should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired");

  @ArchTest
  static final ArchRule controllersOnlyInWeb = classes().that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
      .should().resideInAPackage("..adapter.in.web..");
}
```

---

## C. Backend LLD (Java 21 / Spring Boot 3)

### C1. Profile aggregate, value objects, domain events

```java
package com.pahadijodi.profile.domain.model;

import com.pahadijodi.profile.domain.event.*;
import com.pahadijodi.profile.domain.exception.*;
import java.time.*;
import java.util.*;

public final class Profile {                         // Aggregate root
  private final ProfileId id;
  private final UserId ownerId;
  private PersonalInfo personal;
  private Community community;
  private NativePlace nativePlace;
  private FamilyInfo family;
  private Education education;
  private Occupation occupation;
  private PartnerPreference preference;
  private ProfileStatus status;
  private long version;                              // optimistic lock
  private final List<DomainEvent> events = new ArrayList<>();

  private Profile(ProfileId id, UserId ownerId, PersonalInfo p, Community c, NativePlace n, Instant now) {
    this.id = id; this.ownerId = ownerId; this.personal = p; this.community = c;
    this.nativePlace = n; this.status = ProfileStatus.DRAFT;
    events.add(new ProfileCreated(id, ownerId, c.region(), n.district(), now));
  }

  public static Profile create(UserId owner, PersonalInfo p, Community c, NativePlace n, Clock clock) {
    if (p.dateOfBirth().plusYears(18).isAfter(LocalDate.now(clock)))
      throw new DomainException("PROFILE_UNDERAGE", "Must be 18+ to register");
    return new Profile(ProfileId.newId(), owner, p, c, n, clock.instant());
  }

  public void updateNativePlace(NativePlace n, Clock clock) {
    ensureEditable();
    this.nativePlace = n;
    events.add(new ProfileUpdated(id, Set.of("nativePlace"), clock.instant()));
  }

  public void publish(Clock clock) {
    ensureEditable();
    if (completeness() < 70) throw new DomainException("PROFILE_INCOMPLETE", "Need >=70% completeness");
    this.status = ProfileStatus.PENDING_MODERATION;
    events.add(new ProfileSubmittedForReview(id, ownerId, clock.instant()));
  }

  public void approve(Clock clock) {
    if (status != ProfileStatus.PENDING_MODERATION) throw new DomainException("BAD_STATE", "Not pending");
    this.status = ProfileStatus.ACTIVE;
    events.add(new ProfilePublished(id, ownerId, snapshot(), clock.instant()));
  }

  public int completeness() {
    int score = 0;
    score += personal != null ? 20 : 0;  score += community != null ? 15 : 0;
    score += nativePlace != null ? 15 : 0; score += family != null ? 15 : 0;
    score += education != null ? 15 : 0; score += occupation != null ? 10 : 0;
    score += preference != null ? 10 : 0;
    return score;
  }

  private void ensureEditable() {
    if (status == ProfileStatus.SUSPENDED || status == ProfileStatus.DELETED)
      throw new DomainException("BAD_STATE", "Profile not editable");
  }
  private ProfileSnapshot snapshot() { return new ProfileSnapshot(id, personal, community, nativePlace, education, occupation); }

  public List<DomainEvent> pullEvents() { var copy = List.copyOf(events); events.clear(); return copy; }
  public ProfileId id() { return id; }  public UserId ownerId() { return ownerId; }
  public ProfileStatus status() { return status; }  public long version() { return version; }
}

// ---- Value objects (records are immutable + self-validating) ----
public record ProfileId(UUID value) { public static ProfileId newId() { return new ProfileId(UuidCreator.getTimeOrderedEpoch()); } }
public record UserId(UUID value) {}
public record NativePlace(District district, String tehsil, String village, Region region) {
  public NativePlace {
    Objects.requireNonNull(district);
    if (village != null && village.length() > 120) throw new DomainException("BAD_VILLAGE", "too long");
    region = district.region();                      // derived: GARHWAL | KUMAON | PLAINS
  }
}
public enum Region { GARHWAL, KUMAON, PLAINS }
public enum District {
  UTTARKASHI(Region.GARHWAL), CHAMOLI(Region.GARHWAL), RUDRAPRAYAG(Region.GARHWAL), TEHRI(Region.GARHWAL),
  DEHRADUN(Region.GARHWAL), PAURI_GARHWAL(Region.GARHWAL), HARIDWAR(Region.PLAINS),
  PITHORAGARH(Region.KUMAON), BAGESHWAR(Region.KUMAON), ALMORA(Region.KUMAON), CHAMPAWAT(Region.KUMAON),
  NAINITAL(Region.KUMAON), UDHAM_SINGH_NAGAR(Region.PLAINS);
  private final Region region; District(Region r) { this.region = r; } public Region region() { return region; }
}
public record Community(String group /*GARHWALI|KUMAONI|JAUNSARI|BHOTIYA|OTHER*/, String caste, String subCaste, String gotra) {}
public record PersonalInfo(String firstName, String lastName, LocalDate dateOfBirth, Gender gender,
                           int heightCm, MaritalStatus maritalStatus, Set<String> motherTongues) {}
public record Money(long minorUnits, String currency) {}

// ---- Domain events (sealed hierarchy) ----
public sealed interface DomainEvent permits ProfileCreated, ProfileUpdated, ProfileSubmittedForReview, ProfilePublished {
  ProfileId profileId(); Instant occurredAt();
}
public record ProfileCreated(ProfileId profileId, UserId ownerId, Region region, District district, Instant occurredAt) implements DomainEvent {}
public record ProfileUpdated(ProfileId profileId, Set<String> changedFields, Instant occurredAt) implements DomainEvent {}
public record ProfileSubmittedForReview(ProfileId profileId, UserId ownerId, Instant occurredAt) implements DomainEvent {}
public record ProfilePublished(ProfileId profileId, UserId ownerId, ProfileSnapshot snapshot, Instant occurredAt) implements DomainEvent {}
```

### C2. Ports & adapters + command handler

```java
// application/port/in
public interface CreateProfileUseCase { ProfileId handle(CreateProfileCommand cmd); }
public record CreateProfileCommand(UserId owner, PersonalInfo personal, Community community, NativePlace nativePlace, String idempotencyKey) {}

// application/port/out
public interface ProfileRepository {
  Optional<Profile> findById(ProfileId id);
  Optional<Profile> findByOwner(UserId owner);
  Profile save(Profile profile);                     // throws OptimisticLockConflict
}
public interface DomainEventPublisher { void publish(List<DomainEvent> events); }

// application/command
@Service
@RequiredArgsConstructor
class CreateProfileHandler implements CreateProfileUseCase {
  private final ProfileRepository repo;
  private final DomainEventPublisher publisher;      // writes to outbox in SAME tx
  private final Clock clock;

  @Override @Transactional
  public ProfileId handle(CreateProfileCommand c) {
    repo.findByOwner(c.owner()).ifPresent(p -> { throw new DomainException("PROFILE_EXISTS", "One profile per user"); });
    Profile profile = Profile.create(c.owner(), c.personal(), c.community(), c.nativePlace(), clock);
    repo.save(profile);
    publisher.publish(profile.pullEvents());
    return profile.id();
  }
}

// adapter/out/persistence
@Entity @Table(name = "profile")
class ProfileJpaEntity {
  @Id UUID id; @Column(name="owner_id", nullable=false, unique=true) UUID ownerId;
  @Version long version;
  @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") PersonalJson personal;
  String district; String region; String village; String communityGroup; String caste;
  @Enumerated(EnumType.STRING) ProfileStatus status;
  Instant createdAt; Instant updatedAt;
}
interface ProfileJpaRepo extends JpaRepository<ProfileJpaEntity, UUID> { Optional<ProfileJpaEntity> findByOwnerId(UUID o); }

@Repository @RequiredArgsConstructor
class ProfilePersistenceAdapter implements ProfileRepository {
  private final ProfileJpaRepo jpa; private final ProfilePersistenceMapper mapper;
  public Optional<Profile> findById(ProfileId id) { return jpa.findById(id.value()).map(mapper::toDomain); }
  public Optional<Profile> findByOwner(UserId o) { return jpa.findByOwnerId(o.value()).map(mapper::toDomain); }
  public Profile save(Profile p) {
    try { return mapper.toDomain(jpa.saveAndFlush(mapper.toEntity(p))); }
    catch (ObjectOptimisticLockingFailureException e) { throw new OptimisticLockConflict(p.id()); }
  }
}

// adapter/in/web
@RestController @RequestMapping("/api/v1/profiles") @RequiredArgsConstructor
class ProfileController {
  private final CreateProfileUseCase create; private final ProfileWebMapper mapper;
  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  ProfileCreatedResponse create(@Valid @RequestBody CreateProfileRequest req,
                                @RequestHeader("Idempotency-Key") String key, @AuthenticationPrincipal Jwt jwt) {
    var id = create.handle(mapper.toCommand(req, UUID.fromString(jwt.getSubject()), key));
    return new ProfileCreatedResponse(id.value());
  }
}
```

### C3. CQRS read-model projector

Write side = PostgreSQL aggregate. Read side = denormalised `profile_card` table (and ES index via search-service). Projector is idempotent and ordered per `profileId` key.

```java
@Component @RequiredArgsConstructor @Slf4j
class ProfileReadModelProjector {
  private final JdbcTemplate jdbc; private final ProcessedEventStore processed;

  @KafkaListener(topics = "pj.profile.events.v1", groupId = "profile-readmodel", concurrency = "6")
  @Transactional
  public void on(ConsumerRecord<String, EventEnvelope> rec) {
    EventEnvelope e = rec.value();
    if (!processed.markIfNew("profile-readmodel", e.eventId())) return;   // dedupe
    switch (e.type()) {
      case "ProfilePublished" -> upsertCard(e.payload(ProfilePublishedV1.class), e.sequence());
      case "ProfileUpdated"   -> patchCard(e.payload(ProfileUpdatedV1.class), e.sequence());
      default -> log.debug("ignored {}", e.type());
    }
  }

  private void upsertCard(ProfilePublishedV1 p, long seq) {
    jdbc.update("""
      INSERT INTO profile_card(profile_id, display_name, age, district, region, village, community, photo_key, seq)
      VALUES (?,?,?,?,?,?,?,?,?)
      ON CONFLICT (profile_id) DO UPDATE SET display_name=EXCLUDED.display_name, age=EXCLUDED.age,
        district=EXCLUDED.district, region=EXCLUDED.region, village=EXCLUDED.village,
        community=EXCLUDED.community, photo_key=EXCLUDED.photo_key, seq=EXCLUDED.seq
      WHERE profile_card.seq < EXCLUDED.seq""",                           // ignore stale/out-of-order
      p.profileId(), p.displayName(), p.age(), p.district(), p.region(), p.village(), p.community(), p.photoKey(), seq);
  }
}
```

### C4. Transactional outbox (entity + publisher + relay)

```java
// libs/pj-outbox-starter
@Entity @Table(name = "outbox_event", indexes = @Index(name = "ix_outbox_pending", columnList = "status,id"))
public class OutboxEvent {
  @Id private UUID id;                               // == eventId (UUIDv7, time-ordered)
  @Column(nullable=false) private String aggregateType;
  @Column(nullable=false) private String aggregateId;     // Kafka key => per-aggregate ordering
  @Column(nullable=false) private String eventType;
  @Column(nullable=false) private String topic;
  @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb", nullable=false) private String payload;
  @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private String headers; // traceparent etc.
  @Enumerated(EnumType.STRING) private Status status = Status.PENDING;
  private Instant createdAt; private Instant publishedAt; private int attempts;
  public enum Status { PENDING, PUBLISHED, DEAD }
  // getters/factory omitted
}

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
  @Query(value = """
      SELECT * FROM outbox_event WHERE status='PENDING' ORDER BY id LIMIT :n
      FOR UPDATE SKIP LOCKED""", nativeQuery = true)
  List<OutboxEvent> lockBatch(@Param("n") int n);
}

@Component @RequiredArgsConstructor
class OutboxEventPublisher implements DomainEventPublisher {   // called inside business tx
  private final OutboxRepository repo; private final ObjectMapper om; private final Clock clock;
  private final Tracer tracer;
  @Override @Transactional(propagation = Propagation.MANDATORY)  // fail if no tx => guarantees atomicity
  public void publish(List<DomainEvent> events) {
    for (DomainEvent e : events) repo.save(OutboxEvent.of(e, om, clock, currentTraceparent()));
  }
}

@Component @RequiredArgsConstructor @Slf4j
class OutboxRelay {                                   // poller; Debezium CDC is the prod-scale alternative
  private final OutboxRepository repo; private final KafkaTemplate<String, String> kafka;
  @Scheduled(fixedDelayString = "${pj.outbox.poll-ms:200}")
  @SchedulerLock(name = "outboxRelay", lockAtMostFor = "30s")       // ShedLock => single active relay per service
  @Transactional
  public void relay() {
    for (OutboxEvent e : repo.lockBatch(200)) {
      try {
        var rec = new ProducerRecord<>(e.getTopic(), e.getAggregateId(), e.getPayload());
        rec.headers().add("event-id", e.getId().toString().getBytes(UTF_8));
        rec.headers().add("event-type", e.getEventType().getBytes(UTF_8));
        kafka.send(rec).get(5, TimeUnit.SECONDS);       // sync ack (acks=all, idempotent producer)
        e.markPublished();
      } catch (Exception ex) {
        e.incrementAttempts(); if (e.getAttempts() > 10) e.markDead();
        log.warn("outbox publish failed id={} attempt={}", e.getId(), e.getAttempts(), ex);
      }
    }
  }
}
```

### C5. Idempotent Kafka consumer (inbox pattern)

```java
@Repository @RequiredArgsConstructor
class JdbcProcessedEventStore implements ProcessedEventStore {
  private final JdbcTemplate jdbc;
  /** @return true if first time seen. PK(consumer, event_id) makes this race-safe. */
  public boolean markIfNew(String consumer, UUID eventId) {
    return jdbc.update("INSERT INTO processed_event(consumer, event_id, processed_at) VALUES (?,?,now()) ON CONFLICT DO NOTHING",
        consumer, eventId) == 1;
  }
}

@Configuration
class KafkaConsumerConfig {
  @Bean DefaultErrorHandler errorHandler(KafkaTemplate<Object,Object> t) {
    var recoverer = new DeadLetterPublishingRecoverer(t,
        (rec, ex) -> new TopicPartition(rec.topic() + ".DLT", rec.partition()));
    var eh = new DefaultErrorHandler(recoverer, new ExponentialBackOffWithMaxRetries(5) {{
      setInitialInterval(500); setMultiplier(2.0); setMaxInterval(10_000); }});
    eh.addNotRetryableExceptions(DeserializationException.class, ValidationException.class);
    return eh;
  }
}

@Component @RequiredArgsConstructor
class SubscriptionActivatedConsumer {
  private final ProcessedEventStore processed; private final NotificationService notifications;
  @KafkaListener(topics = "pj.subscription.events.v1", groupId = "notification-subscription")
  @Transactional                                       // inbox insert + side effect commit atomically
  public void on(ConsumerRecord<String, EventEnvelope> r, Acknowledgment ack) {
    var e = r.value();
    if (processed.markIfNew("notification-subscription", e.eventId()) && "SubscriptionActivated".equals(e.type()))
      notifications.sendWelcomePremium(e.payload(SubscriptionActivatedV1.class));
    ack.acknowledge();                                 // manual ack AFTER tx; AckMode.MANUAL_IMMEDIATE
  }
}
```

### C6. MapStruct mapper

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR,
        uses = {IdMapper.class}, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ProfileWebMapper {
  @Mapping(target = "owner", expression = "java(new UserId(ownerId))")
  @Mapping(source = "req.personal", target = "personal")
  @Mapping(source = "req.nativePlace", target = "nativePlace")
  CreateProfileCommand toCommand(CreateProfileRequest req, UUID ownerId, String idempotencyKey);

  @Mapping(source = "id.value", target = "id")
  @Mapping(target = "age", expression = "java(java.time.Period.between(p.personal().dateOfBirth(), java.time.LocalDate.now()).getYears())")
  ProfileResponse toResponse(Profile p);
}
```

### C7. Spring Security: JWT resource server + ABAC

```java
@Configuration @EnableWebSecurity @EnableMethodSecurity
class SecurityConfig {
  @Bean SecurityFilterChain chain(HttpSecurity http, JwtAuthenticationConverter conv) throws Exception {
    return http
      .csrf(c -> c.disable())                          // stateless bearer API; cookies not used for auth
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(a -> a
          .requestMatchers("/actuator/health/**").permitAll()
          .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhooks/**").permitAll()   // HMAC-verified in controller
          .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
          .anyRequest().authenticated())
      .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(conv)))
      .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'"))
                      .httpStrictTransportSecurity(s -> s.includeSubDomains(true).maxAgeInSeconds(31536000)))
      .build();
  }

  @Bean JwtDecoder jwtDecoder(@Value("${pj.jwt.jwks-uri}") String jwks) {
    var d = NimbusJwtDecoder.withJwkSetUri(jwks).jwsAlgorithm(SignatureAlgorithm.RS256).build();
    d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer("https://id.pahadijodi.in"),
        new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains("pj-api")),
        new JwtClaimValidator<String>("token_use", "access"::equals)));
    return d;
  }

  @Bean JwtAuthenticationConverter jwtAuthConverter() {
    var gac = new JwtGrantedAuthoritiesConverter();
    gac.setAuthoritiesClaimName("roles"); gac.setAuthorityPrefix("ROLE_");
    var c = new JwtAuthenticationConverter(); c.setJwtGrantedAuthoritiesConverter(gac); return c;
  }

  @Bean static MethodSecurityExpressionHandler methodSecurity(PermissionEvaluator pe) {
    var h = new DefaultMethodSecurityExpressionHandler(); h.setPermissionEvaluator(pe); return h;
  }
}

/** ABAC: decisions use subject, resource & environment attributes. Usage:
 *  @PreAuthorize("hasPermission(#profileId, 'Profile', 'VIEW_CONTACT')") */
@Component @RequiredArgsConstructor
class AbacPermissionEvaluator implements PermissionEvaluator {
  private final ProfileAccessAttributes attrs;         // loads resource attributes (owner, visibility, blocks)
  private final EntitlementClient entitlements;

  @Override public boolean hasPermission(Authentication a, Object id, Object type, Object perm) {
    if (!(a.getPrincipal() instanceof Jwt jwt)) return false;
    var subject = UUID.fromString(jwt.getSubject());
    var res = attrs.load(UUID.fromString(id.toString()));
    return switch (perm.toString()) {
      case "VIEW_CONTACT" ->
          res.status() == ProfileStatus.ACTIVE
          && !res.blockedBy().contains(subject) && !res.hasBlocked(subject)        // relationship attr
          && (res.ownerId().equals(subject)
              || (entitlements.has(subject, "CONTACT_VIEW") && res.mutualInterest(subject)))  // plan + consent attr
          && !"ip-risk-high".equals(jwt.getClaimAsString("risk"));                  // environment attr
      case "EDIT" -> res.ownerId().equals(subject) && res.status() != ProfileStatus.DELETED;
      case "VIEW"  -> res.status() == ProfileStatus.ACTIVE && !res.blockedBy().contains(subject)
                      && res.visibilityAllows(subject, jwt.getClaimAsString("plan"));
      default -> false;
    };
  }
  @Override public boolean hasPermission(Authentication a, Serializable id, String type, Object perm) { return hasPermission(a, (Object) id, type, perm); }
}
```

### C8. Redis rate limiter (sliding window via Lua, atomic)

```java
@Component @RequiredArgsConstructor
class RedisRateLimiter {
  private final StringRedisTemplate redis;
  private static final RedisScript<Long> SCRIPT = RedisScript.of("""
      local key=KEYS[1]; local now=tonumber(ARGV[1]); local win=tonumber(ARGV[2]);
      local limit=tonumber(ARGV[3]); local member=ARGV[4]
      redis.call('ZREMRANGEBYSCORE', key, 0, now-win)
      local c = redis.call('ZCARD', key)
      if c >= limit then return 0 end
      redis.call('ZADD', key, now, member); redis.call('PEXPIRE', key, win)
      return limit - c""", Long.class);

  /** @return remaining tokens, 0 = rejected */
  public long tryAcquire(String bucket, String subject, int limit, Duration window) {
    long now = System.currentTimeMillis();
    Long r = redis.execute(SCRIPT, List.of("rl:{" + bucket + ":" + subject + "}"),   // hash-tag => cluster-safe
        String.valueOf(now), String.valueOf(window.toMillis()), String.valueOf(limit), now + "-" + ThreadLocalRandom.current().nextInt());
    return r == null ? 0 : r;
  }
}

@Component @RequiredArgsConstructor
class RateLimitFilter extends OncePerRequestFilter {
  private final RedisRateLimiter limiter;
  @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
    String subject = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication()).map(Authentication::getName).orElse(req.getRemoteAddr());
    boolean otp = req.getRequestURI().startsWith("/api/v1/auth/otp");
    long left = limiter.tryAcquire(otp ? "otp" : "api", subject, otp ? 5 : 120, otp ? Duration.ofMinutes(10) : Duration.ofMinutes(1));
    if (left == 0) { res.setStatus(429); res.setHeader("Retry-After", "60"); res.setContentType("application/problem+json");
      res.getWriter().write("{\"type\":\"https://pahadijodi.in/errors/rate-limited\",\"title\":\"Too many requests\",\"status\":429}"); return; }
    res.setHeader("X-RateLimit-Remaining", String.valueOf(left));
    chain.doFilter(req, res);
  }
}
```
Fail-open vs fail-closed: API limiter fails **open** on Redis outage (with local Bucket4j fallback); OTP limiter fails **closed**.

### C9. Compatibility scoring — Strategy pattern

```java
public interface CompatibilityScorer {
  String name();
  double weight();                                    // weights sum to 1.0, configurable per experiment
  ScoreResult score(MatchContext ctx);                // 0..100 + explanation
}
public record ScoreResult(double score, String reasonKey) {}
public record MatchContext(ProfileView seeker, ProfileView candidate, PartnerPreference seekerPref, PartnerPreference candidatePref) {}

@Component class AgePreferenceScorer implements CompatibilityScorer {
  public String name() { return "age"; } public double weight() { return 0.15; }
  public ScoreResult score(MatchContext c) {
    var pref = c.seekerPref(); int age = c.candidate().age();
    if (age >= pref.minAge() && age <= pref.maxAge()) return new ScoreResult(100, "age.in_range");
    int gap = Math.min(Math.abs(age - pref.minAge()), Math.abs(age - pref.maxAge()));
    return new ScoreResult(Math.max(0, 100 - gap * 20), "age.near_range");
  }
}
@Component class RegionRootsScorer implements CompatibilityScorer {   // Pahadi-specific
  public String name() { return "roots"; } public double weight() { return 0.20; }
  public ScoreResult score(MatchContext c) {
    var a = c.seeker().nativePlace(); var b = c.candidate().nativePlace();
    if (a.district() == b.district()) return new ScoreResult(100, "roots.same_district");
    if (a.region() == b.region())     return new ScoreResult(80,  "roots.same_region");
    return new ScoreResult(c.seekerPref().openToOtherRegion() ? 55 : 20, "roots.different_region");
  }
}
@Component class LifestyleScorer implements CompatibilityScorer { /* diet, smoking, drinking Jaccard */ 
  public String name() { return "lifestyle"; } public double weight() { return 0.15; }
  public ScoreResult score(MatchContext c) { double j = jaccard(c.seeker().lifestyleTags(), c.candidate().lifestyleTags()); return new ScoreResult(j * 100, "lifestyle.overlap"); }
  private static double jaccard(Set<String> a, Set<String> b) { if (a.isEmpty() && b.isEmpty()) return 0.5;
    var i = new HashSet<>(a); i.retainAll(b); var u = new HashSet<>(a); u.addAll(b); return (double) i.size() / u.size(); }
}
// + EducationScorer(0.15), CommunityScorer(0.15), LanguageScorer(0.10), HoroscopeGunaScorer(0.10: Ashtakoot 36 -> /36*100)

@Service
public class CompatibilityEngine {
  private final List<CompatibilityScorer> scorers;
  public CompatibilityEngine(List<CompatibilityScorer> scorers) {
    double sum = scorers.stream().mapToDouble(CompatibilityScorer::weight).sum();
    if (Math.abs(sum - 1.0) > 1e-6) throw new IllegalStateException("weights must sum to 1.0, got " + sum);
    this.scorers = scorers;
  }
  public Compatibility compute(MatchContext ctx) {
    // hard filters (deal-breakers) short-circuit
    if (!ctx.seekerPref().acceptsMaritalStatus(ctx.candidate().maritalStatus())) return Compatibility.excluded("marital_status");
    double total = 0; Map<String, ScoreResult> parts = new LinkedHashMap<>();
    for (var s : scorers) { var r = s.score(ctx); parts.put(s.name(), r); total += r.score() * s.weight(); }
    // bidirectional: geometric mean of A->B and B->A prevents one-sided matches
    return new Compatibility(Math.round(total), parts);
  }
}
```

### C10. Elasticsearch query builder

```java
@Component @RequiredArgsConstructor
class ProfileSearchQueryBuilder {
  public co.elastic.clients.elasticsearch._types.query_dsl.Query build(SearchCriteria c, UUID seeker, Set<UUID> excluded) {
    var bool = new BoolQuery.Builder();
    // ---- filters (cacheable, no scoring) ----
    bool.filter(f -> f.term(t -> t.field("status").value("ACTIVE")));
    bool.filter(f -> f.term(t -> t.field("gender").value(c.seekingGender().name())));
    bool.filter(f -> f.range(r -> r.number(n -> n.field("age").gte((double) c.minAge()).lte((double) c.maxAge()))));
    if (c.minHeightCm() != null) bool.filter(f -> f.range(r -> r.number(n -> n.field("heightCm").gte((double) c.minHeightCm()))));
    if (!c.districts().isEmpty()) bool.filter(f -> f.terms(t -> t.field("nativeDistrict").terms(v -> v.value(c.districts().stream().map(FieldValue::of).toList()))));
    if (c.region() != null) bool.filter(f -> f.term(t -> t.field("nativeRegion").value(c.region().name())));
    if (!c.communities().isEmpty()) bool.filter(f -> f.terms(t -> t.field("communityGroup").terms(v -> v.value(c.communities().stream().map(FieldValue::of).toList()))));
    if (c.incomeMinLpa() != null) bool.filter(f -> f.range(r -> r.number(n -> n.field("annualIncomeLpa").gte((double) c.incomeMinLpa()))));
    if (c.manglik() != null) bool.filter(f -> f.term(t -> t.field("horoscope.manglik").value(c.manglik().name())));
    // ---- must_not: blocked/ignored/self ----
    var ids = new HashSet<>(excluded); ids.add(seeker);
    bool.mustNot(m -> m.ids(i -> i.values(ids.stream().map(UUID::toString).toList())));
    // ---- should: scoring/boosts ----
    if (c.village() != null) {
      bool.should(s -> s.match(m -> m.field("nativeVillage").query(c.village()).fuzziness("AUTO").boost(3.0f)));   // handles Devanagari/transliteration spellings
    }
    c.keyword().ifPresent(k -> bool.must(m -> m.multiMatch(mm -> mm.query(k).fields("bio^1", "occupationTitle^2", "education.degree^2").type(TextQueryType.BestFields))));
    bool.should(s -> s.term(t -> t.field("verified").value(true).boost(1.5f)));
    // ---- function score: recency + completeness + boost tier ----
    return Query.of(q -> q.functionScore(fs -> fs.query(qq -> qq.bool(bool.build()))
        .functions(fn -> fn.gauss(g -> g.date(d -> d.field("lastActiveAt").placement(p -> p.origin("now").scale(JsonData.of("14d")).decay(0.5)))))
        .functions(fn -> fn.fieldValueFactor(v -> v.field("completeness").factor(0.02).missing(0.5).modifier(FieldValueFactorModifier.Log1p)))
        .functions(fn -> fn.filter(ff -> ff.term(t -> t.field("boostTier").value("PREMIUM_PLUS"))).weight(1.3))
        .boostMode(FunctionBoostMode.Multiply).scoreMode(FunctionScoreMode.Sum)));
  }
}
```
Pagination uses `search_after` + PIT (never deep `from`); index alias `profiles_v{n}` for zero-downtime reindex.

### C11. Razorpay webhook controller (signature verify + idempotency)

```java
@RestController @RequestMapping("/api/v1/payments/webhooks") @RequiredArgsConstructor @Slf4j
class RazorpayWebhookController {
  private final RazorpayProperties props; private final WebhookEventStore store; private final PaymentEventHandler handler;

  @PostMapping("/razorpay")
  ResponseEntity<Void> receive(@RequestBody byte[] rawBody,                      // RAW bytes: never re-serialise before HMAC
                               @RequestHeader("X-Razorpay-Signature") String signature,
                               @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
    String expected = hmacSha256Hex(props.webhookSecret(), rawBody);
    if (!MessageDigest.isEqual(expected.getBytes(UTF_8), signature.getBytes(UTF_8))) {   // constant-time compare
      log.warn("razorpay webhook bad signature"); return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    if (eventId != null && !store.firstSeen("razorpay", eventId)) return ResponseEntity.ok().build();   // replay-safe
    handler.handle(parse(rawBody));                                                // enqueues work; returns fast (<2s)
    return ResponseEntity.ok().build();
  }

  static String hmacSha256Hex(String secret, byte[] body) {
    try { var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
          return HexFormat.of().formatHex(mac.doFinal(body)); }
    catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
  }
}

@Service @RequiredArgsConstructor
class PaymentEventHandler {
  private final PaymentRepository payments; private final DomainEventPublisher publisher; private final Clock clock;
  @Transactional
  void handle(RazorpayEvent ev) {
    switch (ev.event()) {
      case "payment.captured" -> payments.findByProviderOrderId(ev.orderId()).ifPresent(p -> {
          if (p.markCaptured(ev.paymentId(), ev.amount(), clock)) publisher.publish(p.pullEvents()); });   // state machine guards double-capture
      case "payment.failed"   -> payments.findByProviderOrderId(ev.orderId()).ifPresent(p -> { p.markFailed(ev.reason(), clock); publisher.publish(p.pullEvents()); });
      case "refund.processed" -> /* similar */ {}
      default -> {}
    }
  }
}
```
Amount in webhook is **always** compared with the server-side order amount; client-reported success is never trusted.

### C12. Entitlement check aspect

```java
@Target(METHOD) @Retention(RUNTIME)
public @interface RequiresEntitlement { String value(); boolean consume() default false; }   // e.g. "CHAT_UNLOCK", "PROFILE_VIEW"

@Aspect @Component @RequiredArgsConstructor
class EntitlementAspect {
  private final EntitlementService entitlements;       // Redis-cached plan + quota counters
  @Around("@annotation(req)")
  public Object check(ProceedingJoinPoint pjp, RequiresEntitlement req) throws Throwable {
    UUID user = CurrentUser.id();
    var decision = req.consume() ? entitlements.tryConsume(user, req.value()) : entitlements.check(user, req.value());
    if (!decision.allowed())
      throw new EntitlementException(req.value(), decision.requiredPlan(), decision.reason());   // -> 402/403 ProblemDetail w/ upgrade hint
    try { return pjp.proceed(); }
    catch (Throwable t) { if (req.consume()) entitlements.refund(user, req.value()); throw t; }  // don't burn quota on failure
  }
}

@Service @RequiredArgsConstructor
class EntitlementService {
  private final StringRedisTemplate redis; private final PlanCatalog plans; private final SubscriptionClient subs;
  Decision tryConsume(UUID user, String feature) {
    Plan plan = currentPlan(user);                     // cached 5m, invalidated by SubscriptionActivated/Expired events
    Limit limit = plans.limit(plan, feature);
    if (limit.unlimited()) return Decision.allow();
    String key = "ent:%s:%s:%s".formatted(user, feature, YearMonth.now(ZoneOffset.UTC));
    Long used = redis.opsForValue().increment(key);
    if (used == 1) redis.expire(key, Duration.ofDays(35));
    return used <= limit.max() ? Decision.allow() : Decision.deny(plans.minimumPlanFor(feature), "QUOTA_EXHAUSTED");
  }
}

// usage
@PostMapping("/{id}/contact") @RequiresEntitlement(value = "CONTACT_VIEW", consume = true)
ContactDto viewContact(@PathVariable UUID id) { ... }
```

### C13. Flyway migration

`V1_0_3__profile_core.sql` (forward-only, backward-compatible: expand → migrate → contract)

```sql
CREATE TABLE profile (
  id             UUID PRIMARY KEY,
  owner_id       UUID NOT NULL UNIQUE,
  status         VARCHAR(24) NOT NULL CHECK (status IN ('DRAFT','PENDING_MODERATION','ACTIVE','SUSPENDED','DELETED')),
  personal       JSONB NOT NULL,
  community_group VARCHAR(24), caste VARCHAR(64), sub_caste VARCHAR(64), gotra VARCHAR(64),
  region         VARCHAR(12), district VARCHAR(24), tehsil VARCHAR(64), village VARCHAR(120),
  version        BIGINT NOT NULL DEFAULT 0,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at     TIMESTAMPTZ
);
CREATE INDEX ix_profile_district_status ON profile(district, status) WHERE deleted_at IS NULL;

CREATE TABLE outbox_event (
  id UUID PRIMARY KEY, aggregate_type VARCHAR(48) NOT NULL, aggregate_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL, topic VARCHAR(96) NOT NULL, payload JSONB NOT NULL, headers JSONB,
  status VARCHAR(12) NOT NULL DEFAULT 'PENDING', attempts INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), published_at TIMESTAMPTZ
);
CREATE INDEX ix_outbox_pending ON outbox_event(id) WHERE status = 'PENDING';

CREATE TABLE processed_event (
  consumer VARCHAR(64) NOT NULL, event_id UUID NOT NULL, processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (consumer, event_id)
);
```
Rules: one concern per migration; never edit applied migrations; `spring.flyway.validate-on-migrate=true`; destructive DDL needs a two-release gap; large backfills run as Kubernetes Jobs not Flyway.

### C14. TestContainers integration test

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProfileFlowIT {
  @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");
  @Container @ServiceConnection static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
  @Container @ServiceConnection(name = "redis") static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @Autowired TestRestTemplate http; @Autowired OutboxRepository outbox; @Autowired JwtTestFactory jwt;

  @Test void createProfile_persistsAggregate_andEmitsOutboxEvent() {
    var headers = new HttpHeaders(); headers.setBearerAuth(jwt.userToken(UUID.randomUUID())); headers.set("Idempotency-Key", UUID.randomUUID().toString());
    var body = new CreateProfileRequest(/* adult, Pauri Garhwal, village Khirsu */);
    var res = http.exchange("/api/v1/profiles", POST, new HttpEntity<>(body, headers), ProfileCreatedResponse.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    await().atMost(5, SECONDS).untilAsserted(() ->
        assertThat(outbox.findAll()).anyMatch(e -> e.getEventType().equals("ProfileCreated") && e.getStatus() == OutboxEvent.Status.PUBLISHED));
  }

  @Test void createProfile_underage_returns422ProblemDetail() { /* assert application/problem+json, code PROFILE_UNDERAGE */ }
  @Test void sameIdempotencyKey_twice_returnsSameProfileId() { /* ... */ }
}
```
Also: Pact consumer-driven contract tests per API, `@Tag("slow")` for ES/Kafka suites, singleton containers (`withReuse(true)`) to keep CI < 10 min.

---

## D. Frontend & Mobile LLD

### D1. Web — feature-sliced structure

```
apps/web/src/
├── app/            store.ts, queryClient.ts, router.tsx, providers.tsx, theme/{index.ts,tokens.ts}
├── shared/         api/(axios.ts, interceptors.ts), ui/, hooks/, utils/, i18n/{index.ts,en.json,hi.json}
├── features/
│   ├── auth/       {api,components,hooks,slice.ts,schemas.ts}
│   ├── profile/    {api,components/steps/*,hooks,schemas.ts,ProfileWizard.tsx}
│   ├── search/     filters/, MapFilter/ (interactive Uttarakhand SVG map), saved-searches/
│   ├── matches/    daily/, nearby/, compatibility/
│   ├── chat/       {socket.ts,useChatSocket.ts,components,store}
│   ├── call/       WebRTC hooks & UI
│   ├── subscription/ checkout/ (Razorpay/Stripe)
│   └── settings/
├── pages/          route-level compositions (lazy-loaded)
├── sw/             service-worker.ts (Workbox injectManifest)
└── main.tsx
```
Rule: features import from `shared`, never from other features (enforced via `eslint-plugin-boundaries`). Server state → React Query; client/UI state (auth session, chat presence, drafts) → RTK.

### D2. Store & query setup

```ts
// app/store.ts
import { configureStore } from '@reduxjs/toolkit';
import authReducer from '@/features/auth/slice';
import chatReducer from '@/features/chat/store';
import uiReducer from '@/shared/ui/uiSlice';

export const store = configureStore({
  reducer: { auth: authReducer, chat: chatReducer, ui: uiReducer },
  middleware: (gdm) => gdm({ serializableCheck: { ignoredActions: ['chat/socketConnected'] } }),
  devTools: import.meta.env.DEV,
});
export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

// app/queryClient.ts
import { QueryClient } from '@tanstack/react-query';
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 60_000, gcTime: 10 * 60_000, retry: (n, e: any) => e?.status >= 500 && n < 2, refetchOnWindowFocus: false },
    mutations: { retry: 0 },
  },
});

// shared/api/axios.ts  — token in memory, refresh token in httpOnly cookie
export const api = axios.create({ baseURL: import.meta.env.VITE_API_URL, withCredentials: true });
api.interceptors.request.use((cfg) => { const t = store.getState().auth.accessToken; if (t) cfg.headers.Authorization = `Bearer ${t}`; cfg.headers['X-Request-Id'] = crypto.randomUUID(); return cfg; });
let refreshing: Promise<string> | null = null;
api.interceptors.response.use(undefined, async (err) => {
  if (err.response?.status === 401 && !err.config._retry) {
    err.config._retry = true;
    refreshing ??= refreshToken().finally(() => (refreshing = null));   // single-flight refresh
    const token = await refreshing; err.config.headers.Authorization = `Bearer ${token}`; return api(err.config);
  }
  return Promise.reject(err);
});
```

### D3. RHF + Zod multi-step profile form

```tsx
// features/profile/schemas.ts
import { z } from 'zod';
export const DISTRICTS = ['DEHRADUN','PAURI_GARHWAL','TEHRI','CHAMOLI','RUDRAPRAYAG','UTTARKASHI','ALMORA','NAINITAL','BAGESHWAR','PITHORAGARH','CHAMPAWAT','UDHAM_SINGH_NAGAR','HARIDWAR'] as const;

export const basicSchema = z.object({
  firstName: z.string().trim().min(2).max(60),
  lastName: z.string().trim().min(1).max(60),
  dateOfBirth: z.coerce.date().refine(d => differenceInYears(new Date(), d) >= 18, 'Must be 18+'),
  gender: z.enum(['MALE','FEMALE','OTHER']),
  heightCm: z.number().int().min(120).max(230),
  maritalStatus: z.enum(['NEVER_MARRIED','DIVORCED','WIDOWED','SEPARATED']),
});
export const rootsSchema = z.object({
  district: z.enum(DISTRICTS), tehsil: z.string().max(64).optional(),
  village: z.string().trim().max(120).optional(),
  communityGroup: z.enum(['GARHWALI','KUMAONI','JAUNSARI','BHOTIYA','OTHER']),
  caste: z.string().max(64).optional(), gotra: z.string().max(64).optional(),
  motherTongues: z.array(z.string()).min(1),
});
export const careerSchema = z.object({ education: z.string().min(2), occupation: z.string().min(2), annualIncomeLpa: z.number().min(0).max(1000).optional() });
export const profileSchema = basicSchema.merge(rootsSchema).merge(careerSchema);
export type ProfileForm = z.infer<typeof profileSchema>;

// features/profile/ProfileWizard.tsx
const steps = [
  { key: 'basic', schema: basicSchema, Component: BasicStep },
  { key: 'roots', schema: rootsSchema, Component: RootsStep },
  { key: 'career', schema: careerSchema, Component: CareerStep },
] as const;

export function ProfileWizard() {
  const [i, setI] = useState(0);
  const { t } = useTranslation();
  const methods = useForm<ProfileForm>({ resolver: zodResolver(profileSchema), mode: 'onTouched', defaultValues: loadDraft() });
  const create = useCreateProfile();                                  // React Query mutation
  const step = steps[i];

  useEffect(() => { const sub = methods.watch(v => saveDraft(v)); return () => sub.unsubscribe(); }, [methods]);

  const next = async () => {
    const fields = Object.keys(step.schema.shape) as (keyof ProfileForm)[];
    if (await methods.trigger(fields)) setI(i + 1);                   // validate only current step
  };
  const submit = methods.handleSubmit((v) => create.mutate(v, { onSuccess: () => clearDraft() }));

  return (
    <FormProvider {...methods}>
      <Stepper activeStep={i}>{steps.map(s => <Step key={s.key}><StepLabel>{t(`profile.step.${s.key}`)}</StepLabel></Step>)}</Stepper>
      <step.Component />
      <Stack direction="row" justifyContent="space-between">
        <Button disabled={i === 0} onClick={() => setI(i - 1)}>{t('common.back')}</Button>
        {i < steps.length - 1 ? <Button onClick={next}>{t('common.next')}</Button>
                              : <LoadingButton loading={create.isPending} onClick={submit}>{t('common.submit')}</LoadingButton>}
      </Stack>
    </FormProvider>
  );
}
```

### D4. Socket.IO hook

```ts
// features/chat/useChatSocket.ts
export function useChatSocket() {
  const token = useAppSelector(s => s.auth.accessToken);
  const dispatch = useAppDispatch(); const qc = useQueryClient();
  const socketRef = useRef<Socket>();

  useEffect(() => {
    if (!token) return;
    const s = io(import.meta.env.VITE_CHAT_URL, { path: '/ws', transports: ['websocket'], auth: (cb) => cb({ token: store.getState().auth.accessToken }),
      reconnectionDelayMax: 10_000, reconnectionAttempts: Infinity });
    socketRef.current = s;
    s.on('message:new', (m: ChatMessage) => { qc.setQueryData(['chat', m.conversationId], (old: ChatMessage[] = []) => dedupeById([...old, m])); s.emit('message:ack', { id: m.id }); });
    s.on('typing', ({ conversationId, userId, on }) => dispatch(setTyping({ conversationId, userId, on })));
    s.on('receipt:read', ({ conversationId, upTo }) => dispatch(markRead({ conversationId, upTo })));
    s.on('connect_error', (e) => { if (e.message === 'unauthorized') dispatch(refreshSession()); });
    return () => { s.disconnect(); };
  }, [token]);

  return useMemo(() => ({
    send: (conversationId: string, body: string, clientId = crypto.randomUUID()) =>
      socketRef.current?.timeout(5000).emit('message:send', { conversationId, body, clientId }),   // clientId = idempotency key; optimistic UI
    typing: throttle((conversationId: string, on: boolean) => socketRef.current?.emit('typing', { conversationId, on }), 2000),
  }), []);
}
```
Server side: Socket.IO/WebSocket nodes behind NLB with Redis pub/sub (or Kafka) fan-out; sticky not required because of Redis adapter.

### D5. PWA / Service worker

```ts
// vite.config.ts
import { VitePWA } from 'vite-plugin-pwa';
export default defineConfig({
  plugins: [react(), VitePWA({
    strategies: 'injectManifest', srcDir: 'src/sw', filename: 'service-worker.ts', registerType: 'prompt',
    manifest: { name: 'Pahadi Jodi', short_name: 'PahadiJodi', start_url: '/', display: 'standalone', theme_color: '#1B5E20', background_color: '#ffffff',
                icons: [{ src: '/icons/192.png', sizes: '192x192', type: 'image/png' }, { src: '/icons/512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' }] },
  })],
});

// sw/service-worker.ts
import { precacheAndRoute, cleanupOutdatedCaches } from 'workbox-precaching';
import { registerRoute } from 'workbox-routing';
import { StaleWhileRevalidate, CacheFirst, NetworkOnly } from 'workbox-strategies';
import { ExpirationPlugin } from 'workbox-expiration';
import { BackgroundSyncPlugin } from 'workbox-background-sync';
declare const self: ServiceWorkerGlobalScope;
precacheAndRoute(self.__WB_MANIFEST); cleanupOutdatedCaches();
registerRoute(({ url }) => url.pathname.startsWith('/cdn/photos/'), new CacheFirst({ cacheName: 'photos', plugins: [new ExpirationPlugin({ maxEntries: 200, maxAgeSeconds: 7 * 864e2 })] }));
registerRoute(({ url }) => url.pathname.startsWith('/api/v1/meta/'), new StaleWhileRevalidate({ cacheName: 'meta' }));   // districts, i18n lists
registerRoute(({ url }) => url.pathname.startsWith('/api/'), new NetworkOnly());                                          // never cache PII/API responses
registerRoute(({ url, request }) => url.pathname === '/api/v1/interests' && request.method === 'POST',
  new NetworkOnly({ plugins: [new BackgroundSyncPlugin('interests-queue', { maxRetentionTime: 24 * 60 })] }), 'POST');
self.addEventListener('push', (e) => { const d = e.data?.json(); e.waitUntil(self.registration.showNotification(d.title, { body: d.body, data: { url: d.url }, icon: '/icons/192.png' })); });
self.addEventListener('notificationclick', (e) => { e.notification.close(); e.waitUntil(clients.openWindow(e.notification.data.url)); });
```

### D6. i18n (en/hi) and dark-mode theme

```ts
// shared/i18n/index.ts
import i18n from 'i18next'; import { initReactI18next } from 'react-i18next'; import LanguageDetector from 'i18next-browser-languagedetector';
import en from './en.json'; import hi from './hi.json';
i18n.use(LanguageDetector).use(initReactI18next).init({
  resources: { en: { translation: en }, hi: { translation: hi } },       // Garhwali/Kumaoni (gbm/kfy) added as lazy-loaded namespaces
  fallbackLng: 'en', supportedLngs: ['en','hi'], interpolation: { escapeValue: false },
  detection: { order: ['localStorage','navigator'], caches: ['localStorage'] },
});
// hi.json excerpt:  { "profile": { "step": { "basic": "बुनियादी जानकारी", "roots": "पहाड़ी जड़ें (गाँव/ज़िला)", "career": "शिक्षा व व्यवसाय" } },
//                     "common": { "next": "आगे", "back": "पीछे", "submit": "जमा करें" } }

// app/theme/index.ts
export const buildTheme = (mode: 'light' | 'dark') => createTheme({
  cssVariables: true,
  palette: { mode,
    primary: { main: mode === 'light' ? '#1B5E20' : '#81C784' },        // deva-bhoomi green
    secondary: { main: mode === 'light' ? '#B71C1C' : '#EF9A9A' },      // pahadi-ghagra red accent
    background: mode === 'dark' ? { default: '#0F1512', paper: '#17201B' } : undefined },
  typography: { fontFamily: '"Inter","Noto Sans Devanagari",system-ui,sans-serif' },
  shape: { borderRadius: 12 },
});
export function ThemeProviderWithMode({ children }: PropsWithChildren) {
  const pref = useMediaQuery('(prefers-color-scheme: dark)');
  const stored = useAppSelector(s => s.ui.themeMode);                    // 'system'|'light'|'dark'
  const mode = stored === 'system' ? (pref ? 'dark' : 'light') : stored;
  const theme = useMemo(() => buildTheme(mode), [mode]);
  return <ThemeProvider theme={theme}><CssBaseline />{children}</ThemeProvider>;
}
```

### D7. Mobile (Expo) structure

```
apps/mobile/
├── app/                      # expo-router (file-based)
│   ├── _layout.tsx  (providers, auth gate, biometric lock)
│   ├── (auth)/{login,otp,register}.tsx
│   ├── (tabs)/{home,search,matches,chat,profile}.tsx
│   ├── chat/[conversationId].tsx
│   └── profile/[id].tsx
├── src/{features,shared,db,sync,notifications,security}/
├── app.config.ts · eas.json · google-services.json (via EAS secrets)
```

**Offline sync strategy** — WatermelonDB (SQLite) as local read cache + an outbound **mutation queue**:
- Reads: React Query hydrated from WatermelonDB (`persistQueryClient`), matches/chat history/shortlists available offline.
- Writes (send message, express interest, shortlist, notes): written to `outbox` table with `clientId` (UUID) → UI optimistic → `SyncEngine` flushes FIFO when `NetInfo` reports connectivity, with exponential backoff; server dedupes by `clientId` (same idempotency key semantic as web).
- Pull: delta sync `GET /sync?since=<cursor>` per collection (messages, interests); tombstones for deletes; server wins on conflict except drafts (last-write-wins by `updatedAt` + server vector).
- PII: DB encrypted with SQLCipher key stored in Keystore/Keychain via `expo-secure-store`; wiped on logout/remote-wipe event.

```ts
// src/sync/SyncEngine.ts
export class SyncEngine {
  private running = false;
  constructor(private db: Database, private api: AxiosInstance) {
    NetInfo.addEventListener(s => { if (s.isConnected && s.isInternetReachable) this.flush(); });
  }
  async enqueue(kind: 'message'|'interest'|'shortlist', payload: object) {
    await this.db.write(() => this.db.get<OutboxItem>('outbox').create(r => { r.kind = kind; r.payload = JSON.stringify(payload); r.clientId = uuid(); r.attempts = 0; }));
    this.flush();
  }
  async flush() {
    if (this.running) return; this.running = true;
    try {
      const items = await this.db.get<OutboxItem>('outbox').query(Q.sortBy('created_at')).fetch();
      for (const it of items) {
        try { await this.api.post(`/sync/${it.kind}`, JSON.parse(it.payload), { headers: { 'Idempotency-Key': it.clientId } });
              await this.db.write(() => it.destroyPermanently()); }
        catch (e: any) {
          if (e.response?.status >= 400 && e.response?.status < 500 && e.response.status !== 429) { await this.db.write(() => it.markAsDeleted()); continue; } // poison
          await this.db.write(() => it.update(r => { r.attempts += 1; })); await sleep(Math.min(2 ** it.attempts * 1000, 60_000)); break;
        }
      }
    } finally { this.running = false; }
  }
}
```

**Deep linking** (`app.config.ts`):
```ts
export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config, scheme: 'pahadijodi',
  ios: { bundleIdentifier: 'in.pahadijodi.app', associatedDomains: ['applinks:app.pahadijodi.in'] },
  android: { package: 'in.pahadijodi.app', googleServicesFile: process.env.GOOGLE_SERVICES_JSON,
    intentFilters: [{ action: 'VIEW', autoVerify: true, category: ['BROWSABLE','DEFAULT'],
      data: [{ scheme: 'https', host: 'app.pahadijodi.in', pathPrefix: '/p' }, { scheme: 'https', host: 'app.pahadijodi.in', pathPrefix: '/chat' }] }] },
  plugins: ['expo-router','expo-secure-store','expo-local-authentication','@react-native-firebase/app','expo-notifications'],
});
// Links: https://app.pahadijodi.in/p/{profileId}  → app/profile/[id].tsx ; pahadijodi://chat/{id}
// Host serves /.well-known/assetlinks.json and apple-app-site-association.
```

**Biometric auth**:
```ts
// src/security/BiometricGate.tsx
import * as LocalAuthentication from 'expo-local-authentication'; import * as SecureStore from 'expo-secure-store';
export async function unlockWithBiometrics(): Promise<boolean> {
  const hw = await LocalAuthentication.hasHardwareAsync(); const enrolled = await LocalAuthentication.isEnrolledAsync();
  if (!hw || !enrolled) return false;
  const r = await LocalAuthentication.authenticateAsync({ promptMessage: 'Unlock Pahadi Jodi', cancelLabel: 'Use PIN', disableDeviceFallback: false });
  return r.success;
}
export async function storeRefreshToken(t: string) { await SecureStore.setItemAsync('rt', t, { requireAuthentication: true, keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY }); }
// _layout.tsx: on AppState 'active' after >60s in background -> require unlockWithBiometrics() before rendering protected routes.
```

**FCM push**:
```ts
// src/notifications/register.ts
import messaging from '@react-native-firebase/messaging'; import * as Notifications from 'expo-notifications';
export async function registerPush(api: AxiosInstance) {
  const auth = await messaging().requestPermission();
  if (auth !== messaging.AuthorizationStatus.AUTHORIZED && auth !== messaging.AuthorizationStatus.PROVISIONAL) return;
  const token = await messaging().getToken();
  await api.put('/api/v1/devices/push-token', { token, platform: Platform.OS, appVersion: Application.nativeApplicationVersion });
  messaging().onTokenRefresh(t => api.put('/api/v1/devices/push-token', { token: t, platform: Platform.OS }));
  messaging().onNotificationOpenedApp(m => router.push(m.data!.deepLink as string));
  const initial = await messaging().getInitialNotification(); if (initial) router.push(initial.data!.deepLink as string);
}
messaging().setBackgroundMessageHandler(async (m) => { /* data-only: bump badge, prefetch conversation delta */ });
```
Notification payloads carry **no PII** (e.g. "You have a new interest") — details fetched after unlock.

---

## E. Coding Standards

### E1. Java
- Java 21, Gradle, `-Xlint:all -Werror`; format with Spotless (google-java-format AOSP); no wildcard imports; constructor injection only (Lombok `@RequiredArgsConstructor`); no `@Data` on entities.
- Domain: immutable records for value objects, sealed types for events/state, no setters on aggregates, `Clock` injected (never `Instant.now()`).
- `Optional` only as return type; no `null` returns from collections; `@Nullable` documented.
- Virtual threads enabled (`spring.threads.virtual.enabled=true`) for I/O services; avoid `synchronized` pinning — use `ReentrantLock`.
- Transactions only at application layer; read-only queries `@Transactional(readOnly = true)`; no remote calls inside a DB transaction (except outbox write).
- Exceptions: domain → `DomainException(code, message)`; mapped to RFC 7807 in one `@RestControllerAdvice`; never leak stack traces or SQL.
- Money: `long` minor units; time: UTC `Instant`; IDs: UUIDv7.
- Resilience4j on every outbound call: timeout < caller's, retry only idempotent, circuit breaker, bulkhead.

### E2. TypeScript
- `strict: true`, `noUncheckedIndexedAccess`, no `any` (lint error), no default exports except pages, ESLint (typescript-eslint, react-hooks, jsx-a11y, boundaries), Prettier.
- Zod schemas are the single source of types for forms and API responses (`z.infer`); API responses are parsed at the boundary.
- Components < 200 lines, hooks for logic, no business logic in JSX; memoise only on measured need.
- Accessibility WCAG 2.1 AA; all strings via i18n; no hard-coded colours (theme tokens).
- Bundle budget: initial JS ≤ 250 KB gz, route-level code split, Lighthouse PWA/Perf ≥ 90 in CI.

### E3. Git & commits
- Trunk-based: short-lived branches `feat/PJ-123-village-search`, `fix/`, `chore/`; squash-merge to `main`; `main` always deployable; release via tags + Release Please; feature flags (Unleash) for incomplete work.
- Conventional Commits: `type(scope): subject` — `feat(search): add village fuzzy match`; `BREAKING CHANGE:` footer triggers major.
- Protected `main`: 1+ CODEOWNER approval (2 for security/payment paths), green CI, signed commits, linear history.

### E4. PR checklist (`pull_request_template.md`)
- [ ] Linked ticket & clear description (why, not only what)
- [ ] Unit + integration tests added; coverage gate passes
- [ ] No PII/secrets in code, logs, or fixtures
- [ ] DB migration backward-compatible (expand/contract) & reviewed by DBA for indexes/locks
- [ ] API change: OpenAPI updated, backward compatible or versioned; contract tests pass
- [ ] Event change: schema compat check (BACKWARD) passed
- [ ] Authorization (RBAC/ABAC) considered for every new endpoint
- [ ] Metrics / logs / trace spans added; alert/runbook updated if SLO-impacting
- [ ] Feature flag & rollback plan
- [ ] i18n strings added (en + hi); a11y checked; screenshots for UI

### E5. API design
- REST, resource nouns, `/api/v1` prefix, plural, kebab-case paths, camelCase JSON; `PATCH` merge-patch for partial updates.
- Idempotency-Key required on all non-idempotent POSTs that create money/entitlement/interest.
- Pagination: cursor-based (`?cursor=&limit=` max 100), response `{items, nextCursor}`; sorting whitelist.
- Errors: RFC 7807 `application/problem+json` with stable `code`, `traceId`, field `errors[]`.
- Versioning: URL major version; additive changes only within a version; deprecation via `Sunset` header ≥ 6 months.
- OpenAPI 3.1 generated from code (springdoc) and linted by Spectral in CI; ETag/If-None-Match on cacheable GETs.

### E6. Error handling
```java
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
  @ExceptionHandler(DomainException.class)
  ProblemDetail domain(DomainException e) { var p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    p.setProperty("code", e.code()); p.setProperty("traceId", Span.current().getSpanContext().getTraceId()); return p; }
  @ExceptionHandler(EntitlementException.class)
  ProblemDetail ent(EntitlementException e) { var p = ProblemDetail.forStatus(HttpStatus.PAYMENT_REQUIRED); p.setProperty("code","ENTITLEMENT_REQUIRED"); p.setProperty("requiredPlan", e.requiredPlan()); return p; }
  @ExceptionHandler(OptimisticLockConflict.class) ProblemDetail conflict(OptimisticLockConflict e) { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Resource modified, retry"); }
  @ExceptionHandler(Exception.class)
  ProblemDetail fallback(Exception e) { log.error("unhandled", e); return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error"); }   // generic message only
}
```
Frontend: global error boundary + React Query `onError` → toast; map `code` → i18n message; 401 → single-flight refresh; 402 → upgrade modal.

### E7. Logging with PII masking
- JSON logs (Logstash encoder), mandatory fields: `timestamp, level, service, traceId, spanId, userId(hash), requestId`.
- Never log: passwords, OTPs, tokens, Aadhaar/PAN, full phone/email, DOB, horoscope, message bodies, payment card data.
- Masking converter in `pj-observability-starter`:
```java
public class PiiMaskingConverter extends MessageConverter {
  private static final List<Pattern> P = List.of(
    Pattern.compile("\\b[2-9]\\d{3}\\s?\\d{4}\\s?\\d{4}\\b"),                 // Aadhaar
    Pattern.compile("\\b[A-Z]{5}\\d{4}[A-Z]\\b"),                              // PAN
    Pattern.compile("(?<=\\+?91)?[6-9]\\d{9}"),                                // mobile
    Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+"),                            // email
    Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._-]+"));
  @Override public String convert(ILoggingEvent e) { String m = super.convert(e); for (var p : P) m = p.matcher(m).replaceAll(r -> mask(r.group())); return m; }
  private static String mask(String s) { return s.length() <= 4 ? "****" : "****" + s.substring(s.length() - 2); }
}
```
- DTOs holding PII override `toString()` to redact; `@Sensitive` Jackson serializer for audit export; log retention: 30d hot, 1y cold; audit logs immutable (S3 Object Lock).

### E8. Testing pyramid & coverage gates

| Layer | Tooling | Share | Gate |
|---|---|---|---|
| Unit (domain, scorers, mappers) | JUnit 5, AssertJ, Mockito, jqwik (property) | ~70% | JaCoCo line ≥ 80%, branch ≥ 70%; domain pkg ≥ 90% |
| Integration | Testcontainers (PG, Kafka, Redis, ES), WireMock | ~20% | all repo/consumer/outbox paths covered |
| Contract | Pact (HTTP), schema-registry compat (events) | – | must pass to deploy |
| Architecture | ArchUnit | – | zero violations |
| E2E | Playwright (web), Detox/Maestro (mobile) | ~5% | critical journeys: signup→profile→search→interest→chat→pay |
| Non-functional | k6 (load: 1M-user profile), OWASP ZAP baseline, chaos (Litmus) | nightly | p95 SLOs met |
| Frontend unit | Vitest + Testing Library + MSW | – | ≥ 75% lines on features |
| Mutation | PIT on domain & payment modules | weekly | ≥ 70% mutation score |

New code coverage ≥ 85% enforced by Sonar quality gate ("Sonar way" + custom).

### E9. Definition of Done
Code merged via PR · tests at all relevant layers · quality gate green · docs/OpenAPI/ADR updated · dashboards+alerts+runbook for new failure modes · security review for auth/payment/PII changes · feature-flagged & deployed to staging with smoke test · migration tested on prod-sized snapshot · rollout plan (canary %) and rollback verified · product acceptance by PM.

### E10. Static analysis & supply-chain scanning
- **Java:** Checkstyle (Google + custom), SpotBugs + FindSecBugs, PMD, Error Prone, Spotless; Sonar quality gate: 0 new bugs/vulns/hotspots unreviewed, duplication < 3%, A maintainability.
- **TS:** ESLint (+ `eslint-plugin-security`), `tsc --noEmit`, Prettier, `knip` (dead code).
- **Dependencies:** OWASP Dependency-Check + Trivy fs (fail on CVSS ≥ 7 with fix available), Dependabot/Renovate weekly, `pnpm audit`, SBOM (CycloneDX) per build, license allowlist.
- **Secrets:** Gitleaks pre-commit + CI (full history nightly); GitHub secret scanning + push protection; secrets only in AWS Secrets Manager via External Secrets Operator; no `.env` committed.
- **Images/IaC:** Trivy image scan, Cosign signing + admission policy (Kyverno verify), Checkov/tfsec on Terraform, kube-linter / Polaris on Helm output, distroless non-root images, read-only rootfs.
- **DAST:** ZAP baseline on staging per release; quarterly external pentest; bug bounty post-launch.
