# 07 — Interview Questions (grounded in the Pahadi Jodi platform)

60 questions with concise model answers. Levels: **Junior** (1–15), **Mid** (16–30), **Senior** (31–47), **Architect** (48–60).

---

## Junior (Q1–Q15)

**1. Why did we choose microservices instead of a monolith for this platform?**
Different scaling and failure profiles: search and chat are read/connection heavy, payments need strict consistency and PCI-style isolation, AI is bursty and costly. Independent deploys let teams ship without coordinating. Trade-off: operational complexity — mitigated by a shared Helm chart, starters (`pj-*`) and platform tooling. A modular monolith is a valid starting point for a small team.

**2. What is the role of the API Gateway?**
Single entry: TLS termination (behind WAF/ALB), JWT validation, rate limiting, routing, request-ID/trace injection, CORS, and coarse authorization. It holds no business logic. Services still re-validate JWTs (zero trust).

**3. What is the difference between authentication and authorization here?**
Authentication (Identity Service): who you are — OTP, email, Google/Apple/Facebook, 2FA → JWT. Authorization: what you may do — RBAC for roles (USER, MODERATOR, ADMIN), ABAC for contextual rules (block lists, plan entitlements, mutual interest before showing contact).

**4. Why a short-lived access token plus a refresh token?**
Access token (~10 min) limits blast radius if stolen and keeps services stateless. Refresh token (httpOnly cookie on web, Keychain on mobile) is rotated on each use with reuse detection; revoked per device from Device Management.

**5. What is REST idempotency and where do we need it?**
Repeating a request yields the same effect. GET/PUT/DELETE are naturally idempotent; POSTs creating profiles, payments, or interests need an `Idempotency-Key` stored server-side so network retries from mobile don't duplicate.

**6. What does Flyway do and what are the rules?**
Versioned SQL migrations applied in order, checksum-validated. Never edit an applied migration; add a new one. Keep migrations backward compatible (expand → migrate → contract) so old pods keep working during rolling deploys.

**7. Why use MapStruct instead of manual mapping or reflection mappers?**
Compile-time generated code: fast, type-safe, no reflection; `unmappedTargetPolicy = ERROR` catches missing fields at build time. Keeps entities/domain/DTOs separate.

**8. Why do we use DTOs instead of exposing JPA entities?**
Entities leak internal fields (PII, version), couple API to schema, trigger lazy-loading issues, and enable mass-assignment attacks. DTOs are explicit contracts.

**9. What is Redis used for in this system?**
Caching (profile cards, entitlements, meta lists), rate limiting (Lua sliding window), OTP storage with TTL, session/refresh-token deny lists, presence in chat, Socket.IO adapter pub/sub, distributed locks (sparingly).

**10. How do React Query and Redux Toolkit divide responsibilities in the web app?**
React Query = server state (profiles, matches, caching, retries, invalidation). RTK = client state (auth session, theme, chat presence/typing, drafts). Avoid duplicating server data in Redux.

**11. Why React Hook Form + Zod?**
RHF gives uncontrolled inputs (fewer re-renders on long profile forms); Zod provides a single schema for validation and TypeScript types, reusable per-step in the multi-step wizard.

**12. What is a PWA and why does it matter for our users?**
Installable web app with offline shell, push notifications and background sync — valuable for users in hill regions with patchy connectivity. We never cache `/api` responses containing PII.

**13. How do we support Hindi and regional languages?**
i18next with `en`/`hi` bundles, lazy-loaded namespaces for Garhwali/Kumaoni, Devanagari-capable fonts, no hard-coded strings, locale-aware dates/numbers. Backend returns stable error `code`s that the client localizes.

**14. What is the difference between a Kubernetes Deployment, Service and Ingress?**
Deployment manages replica pods/rolling updates; Service gives stable virtual IP/DNS and load-balancing; Ingress (ALB controller) routes external HTTP(S) to Services.

**15. Why are health probes important?**
Liveness restarts hung pods; readiness removes pods from Service endpoints until dependencies (DB, Kafka) are OK; startup probe protects slow JVM boots. Spring Actuator `health/liveness` and `health/readiness` groups.

---

## Mid-level (Q16–Q30)

**16. Explain Hexagonal Architecture as applied to `profile-service`.**
Domain (pure Java) at the centre; application layer defines ports (`ProfileRepository`, `DomainEventPublisher`); adapters implement them (JPA, Kafka/outbox, REST). Dependencies point inward, enforced by ArchUnit. Benefits: testable domain without Spring, swappable infra.

**17. What are the aggregates and bounded contexts, and why is `Profile` not one giant entity?**
Contexts: Identity, Profile, Matching, Chat, Billing, Moderation… Aggregates are consistency boundaries. Profile aggregate holds personal/community/native-place/preferences; photos belong to Media, entitlements to Subscription. Cross-aggregate references by ID, eventual consistency via events.

**18. Why CQRS for search/matching, and what are the costs?**
Writes need normalized transactional integrity (PostgreSQL); reads need denormalized, filterable, ranked data (Elasticsearch, `profile_card`). Cost: eventual consistency (seconds), projection code, reindex tooling. We reserve CQRS for hot read paths and use plain CRUD elsewhere (e.g., Admin).

**19. What problem does the transactional outbox solve?**
The dual-write problem: writing DB + publishing to Kafka can't be atomic. We insert the event into `outbox_event` in the same DB transaction; a relay (poller with `FOR UPDATE SKIP LOCKED` or Debezium CDC) publishes at-least-once. Consumers dedupe.

**20. How do you achieve effectively-once processing with Kafka?**
Producer: idempotent (`enable.idempotence`, `acks=all`). Delivery is at-least-once; consumer uses inbox table `processed_event(consumer, event_id)` PK inside the same transaction as the side effect, then commits offsets. Kafka transactions only for Kafka-to-Kafka (e.g., Streams) flows.

**21. How do you guarantee ordering of events per profile?**
Use `profileId` as the message key → same partition → ordered. Consumers also carry a `sequence`/version and ignore stale updates (`WHERE seq < EXCLUDED.seq`). Global ordering is neither needed nor scalable.

**22. How do you choose the partition count and consumer concurrency?**
Partitions ≥ peak consumer parallelism with headroom (e.g., 24 for `profile.events`), considering target throughput / per-partition throughput. Over-partitioning increases broker overhead; increasing later breaks key→partition mapping (re-keying implications), so plan ahead.

**23. What do you do with poison messages?**
Non-retryable exceptions (deserialization/validation) go straight to `<topic>.DLT`; retryable use exponential backoff then DLT. A DLT dashboard + replay tool (with idempotent consumers) lets operators fix and re-drive. Alert on DLT depth.

**24. How does event schema evolution work?**
Event envelope `{eventId,type,version,occurredAt,producer,traceId,payload}`; schemas in Schema Registry with BACKWARD compatibility (add optional fields, never rename/remove). Breaking change → new topic version `…v2` with dual-publish during migration. CI blocks incompatible schemas.

**25. Describe the Elasticsearch index design for profile search.**
One document per active profile: keyword fields for filters (district, region, community, caste, status), numeric (age, height, income), text with Devanagari/ICU analyzers for village/bio, `geo_point` for nearby, `completeness`, `lastActiveAt`, `boostTier`. Filters in `filter` context (cached); scoring via `function_score` (recency, completeness, premium boost). Aliases for zero-downtime reindex.

**26. How do you handle village-name spelling variants (e.g., "Khirsu", "खिरसू", "Kheersu")?**
Normalization analyzer: ICU folding + transliteration filter, synonyms/gazetteer list of Uttarakhand villages with canonical IDs, fuzzy match with `fuzziness: AUTO` as a *should* boost, and an autocomplete (edge-ngram / completion suggester) in the UI so users pick a canonical village ID — most precise.

**27. How do you paginate deep search results?**
`search_after` with a Point-in-Time and a deterministic tiebreaker (`_shard_doc`/profileId); never deep `from/size` (cost and 10k limit). Cursor is opaque to clients.

**28. Explain the JWT validation configuration on a resource server.**
JWKS URI with key rotation (kid), pin RS256, validate `iss`, `aud=pj-api`, `exp/nbf` with small skew, `token_use=access`; map `roles` claim to authorities. Token never trusted for data that changes quickly (plan) — look up entitlements (cached) instead or use short TTL.

**29. Where is rate limiting applied and with what algorithm?**
Edge: WAF rate rules (IP). Gateway: per-user/per-IP sliding window in Redis via Lua (atomic). Sensitive endpoints stricter (OTP 5/10min, login). OTP limiter fails closed, general limiter fails open with local Bucket4j fallback.

**30. How would you test the outbox flow end-to-end?**
Testcontainers (PostgreSQL + Kafka): call the API, assert the aggregate row and an outbox row in one transaction, wait for status `PUBLISHED`, consume from the topic with a test consumer and assert the envelope; add a failure test (kill broker, confirm retries and eventual delivery) and duplicate-delivery test for consumers.

---

## Senior (Q31–Q47)

**31. Design the checkout and subscription activation flow. Where can it fail and how is each failure handled?**
Client → `payment-service` creates order (server-side amount from plan catalog, Idempotency-Key) → Razorpay/Stripe checkout → provider webhook (HMAC verified, raw body, constant-time compare, event-ID dedupe) → payment state machine `CREATED→AUTHORIZED→CAPTURED` → outbox `PaymentCaptured` → `subscription-service` activates (idempotent on `paymentId`) → `SubscriptionActivated` → entitlements cache invalidated, notification sent. Failures: client closes tab (webhook still completes), webhook delayed (poll provider as reconciliation job), webhook duplicate (dedupe), activation fails (consumer retries → DLT; daily reconciliation compares provider settlement vs. ledger), amount mismatch (flag fraud, no activation).

**32. What is a Saga and which sagas exist in this system?**
A sequence of local transactions with compensations. Examples: *Purchase saga* (payment → subscription → notification; compensation: refund if activation permanently fails); *Account deletion saga* (identity → profile → media → chat → search → analytics purge; each step idempotent, DPDP erasure tracked with a saga log and SLA); *Verification saga* (media upload → OCR/face-match → moderation → badge). Choreography for simple flows, orchestration (state machine in the owning service) when ≥ 4 steps or compensations matter.

**33. How do you make refunds and auto-renewal safe?**
Refunds are commands on the payment aggregate (state machine prevents over-refund; sum(refunds) ≤ captured), executed through provider with an idempotency key, confirmed by webhook, and each emits events to the ledger/subscription service. Auto-renewal: scheduler (ShedLock) selects due subscriptions → creates provider charge with deterministic idempotency key `sub:{id}:{period}`; dunning with retries over 7 days, grace-period entitlements, then downgrade event.

**34. How does reconciliation work?**
Nightly job pulls provider settlement reports (Razorpay settlements/Stripe balance transactions), matches by providerPaymentId & amount against our ledger, produces categories (matched, missing-in-ours, missing-in-provider, amount-diff), auto-heals safe cases (missed webhook → replay), and sends the rest to a finance queue in Admin. Ledger is append-only double-entry.

**35. Walk through how entitlements are enforced and cached without being a bottleneck.**
`@RequiresEntitlement` aspect → `EntitlementService`. Plan resolved from Redis (TTL 5 min) and invalidated by subscription events; quotas via atomic `INCR` with monthly TTL keys. Failure to consume rolls back the counter. For hard billing-critical gates (video calling) the check also re-verifies against subscription-service on cache miss. Entitlement decision logged for audit.

**36. How do you prevent a free user from enumerating the user base via search or profile IDs?**
UUIDv7 IDs (non-sequential, but still not secret → authorization checks always), per-plan view quotas, rate limiting, result caps, no total counts beyond a threshold, bot detection (device fingerprint, behavior anomaly from fraud service), obfuscated contact data until mutual interest + entitlement, and watermarked images.

**37. Design real-time chat for 1M users (≈100k concurrent connections).**
Chat Service nodes terminate WebSocket/Socket.IO behind NLB; ~10–20k connections per pod. Redis adapter (or Kafka) fan-out across nodes; presence in Redis with TTL heartbeat. Messages persisted to PostgreSQL (partitioned by conversation_id hash / month) or Cassandra-like store later; sent via client-generated `clientId` for dedupe and optimistic UI; ack/receipt events; offline users get FCM/APNs push (without PII). Backpressure via per-connection rate limits; autoscale on connections via KEDA/custom metric. Moderation pipeline consumes `MessageSent` asynchronously (toxicity) and can retract.

**38. How do read receipts/typing indicators avoid overloading the system?**
Typing events are ephemeral — Redis pub/sub only, throttled client-side (2s), never persisted. Read receipts are batched ("read up to messageId N") and persisted as a single cursor per participant, not per message.

**39. WebRTC calling: what is our architecture?**
1:1 calls are P2P media with signalling via chat-service WebSocket; STUN/TURN (coturn on EC2/ECS with static IPs, TLS 443 fallback) for NAT traversal — about 15–20% of calls need TURN, which dominates cost. Short-lived TURN credentials (HMAC REST API, 10 min). Entitlement check (Premium+) before issuing credentials; consent flow before recording (both parties accept; recording via server-side SFU/mediasoup or client-side upload only with consent; record consent events audited). Call quality metrics (RTT, jitter, packet loss) sent to analytics.

**40. Explain how the compatibility score is computed and made explainable.**
Strategy pattern: weighted scorers (age 15%, roots/region 20%, community 15%, education 15%, lifestyle 15%, language 10%, horoscope guna milan 10%), deal-breakers as hard filters, bidirectional (geometric mean of A→B and B→A), per-dimension reason keys returned to UI ("Same district: Pauri Garhwal"). Weights are configurable and A/B tested; ML model (AI service) can re-rank the top-N candidates, with the rule score as a fallback and baseline.

**41. How would you evaluate whether the recommendation engine is actually better?**
Offline: NDCG@k/precision@k on historical mutual-interest outcomes, with time-based splits. Online: A/B test with guardrails; primary metric = mutual interest rate and conversation-start rate (not clicks), secondary = response rate, premium conversion; guardrails = report rate, latency, diversity. Beware selection bias and popularity bias (everyone recommended the same 5% of profiles) — apply exposure caps and diversity re-ranking.

**42. How do you design the daily recommendations job at scale?**
Candidate generation: ES filter query + vector similarity (embeddings in OpenSearch kNN / pgvector) → 200 candidates; ranking: model/scorers → top 10; scheduled per time zone (IST and diaspora zones) in batches via Kafka partitions of `userId`, results stored in Redis/DB with TTL; precomputed for active users only (last-active < 30d) to control cost.

**43. LLM usage: how do you control cost, latency and safety?**
Tiered model routing (small model for classification/toxicity, larger for profile rewriting), prompt caching and response caching keyed by normalized input hash, token budgets per plan/user/day, async for non-interactive tasks, batching, max_tokens caps, circuit breaker with graceful degradation (static suggestions). Safety: PII redaction before sending (no Aadhaar/phone/exact address), structured outputs with schema validation, prompt-injection defenses (treat profile text as data, delimiters, no tool access), output moderation, human review for risky outputs, per-request audit log, Azure OpenAI in India region for data residency, no training on our data contractually.

**44. How do you detect fake profiles and romance scams?**
Signals: face match (selfie liveness vs. profile photos vs. ID photo), duplicate faces/perceptual hashes across accounts, device/IP clusters, velocity (many interests in minutes), text patterns (money requests, off-platform contact, crypto), reverse-image hits, phone-number reputation, and behavior graph (many users reporting the same account). A scoring service emits `FraudRiskScored`; policy engine maps score bands to actions (allow / step-up verification / shadow-limit / block). Human review queue for gray zone; appeals flow; feedback loop retrains the model.

**45. How does the face-verification flow handle privacy and spoofing?**
Liveness (active challenge or passive via provider such as AWS Rekognition Face Liveness) prevents photo/video replay; comparison against ID photo with threshold tuning (FAR/FRR tradeoff), biometric templates not stored — only a result and confidence; images in a restricted S3 bucket with KMS, short retention, access via audit-logged admin workbench; explicit consent recorded; Aadhaar via licensed e-KYC/Digilocker only, storing only a reference token/masked last 4 digits (UIDAI rules).

**46. Cache invalidation strategy for profile cards.**
Cache-aside with TTL (10 min) + event-driven eviction on `ProfileUpdated` (consumer deletes key `pc:{id}`), stampede protection via single-flight/`SETNX` lock or probabilistic early refresh, negative caching for 404 (short TTL), versioned keys on schema change. Hot keys (popular profiles) mitigated with local Caffeine L1 (30 s).

**47. How would you troubleshoot "search is slow only in the evening" in production?**
Check Grafana: ES query latency percentiles, thread-pool rejections, GC, cache hit-rate; slow log for expensive queries (wildcards, unbounded fuzzy, large aggregations, `from` deep paging); correlate with traffic mix (saved-search alerts running at 7pm → batch job competing with interactive traffic). Fixes: route batch to separate coordinating nodes/replica preference, add `request_cache`, precompute facets, rate-limit alert jobs, scale data/replica nodes before peak (scheduled scaling).

---

## Architect (Q48–Q60)

**48. Present the end-to-end architecture and justify the main trade-offs.**
Edge: CloudFront + WAF → ALB → API Gateway on EKS. Services in hexagonal/DDD layout communicate synchronously (REST/gRPC with timeouts) only when a user waits, otherwise via Kafka (MSK) events with outbox. Data: PostgreSQL (Aurora/RDS) per service (database-per-service), Redis (ElastiCache), OpenSearch/Elasticsearch for search, S3 for media. Trade-offs: eventual consistency vs. autonomy; operational cost of ~17 services vs. team independence; choose pragmatic merges (e.g., User+Profile can share a deployable at launch, split later) and start with a modular codebase to avoid premature distribution.

**49. How would you scale from 1M registered users to 10M?**
Measure first: 1M registered ≈ 150–250k MAU, ~30–50k DAU, ~5–10k peak concurrent. Scale steps: PostgreSQL read replicas → partition large tables (messages, notifications, audit) → per-service DB isolation → shard chat by conversation ID → ES hot/warm tiers, more shards by profile hash and routing by region where useful → Kafka partition expansion plan → multi-AZ pods autoscaled (HPA/KEDA) → CDN for all media with image transforms → async everything non-interactive → read-heavy matching precompute. Introduce cell-based architecture / multi-region (ap-south-1 primary, ap-south-2 DR; diaspora latency via CloudFront + regional read replicas) at 10M.

**50. Define SLOs, error budgets and how they drive engineering.**
Examples: login availability 99.95%, search p95 < 400 ms (99.9% success), chat message delivery p95 < 1 s, payment webhook processing p99 < 5 s, checkout success ≥ 99.5% excluding user error. Error-budget burn alerts (multi-window multi-burn rate) page on-call; budget exhaustion freezes risky releases. SLIs from Prometheus histograms/OTel.

**51. Multi-tenancy of data & DPDP Act compliance — what must the architecture guarantee?**
Consent management (purpose-bound, withdrawable, versioned notices), data minimization, children's data prohibited (18+ gates), right to access/correction/erasure (deletion saga across services, with legal retention exceptions e.g. payment records 8 years), breach notification workflow (72 h to board), data localization for sensitive fields (ap-south-1), field-level encryption for Aadhaar/PAN/horoscope (envelope encryption with KMS CMKs, per-tenant data keys), access logs for admins, DPO tooling, grievance officer. Horoscope/family/caste are sensitive in practice: stricter access, no use in analytics exports without anonymisation. Caste/community data is used only with explicit user consent and for user-chosen filters — never for pricing or discriminatory exclusion beyond user preference.

**52. Security architecture: walk through threat modeling for the platform.**
STRIDE per trust boundary. Top threats: account takeover (OTP brute force/SIM-swap → rate limits, device binding, 2FA/biometrics, anomaly detection), scraping (rate limits, bot management, watermark), IDOR/BOLA (ABAC check per resource, ArchUnit/test for annotation coverage), payment tampering (server-side amounts, webhook HMAC), SSRF via media URLs (egress allowlist, no fetch-by-URL), malicious uploads (AV scan, content-type sniffing, re-encode images, presigned POST policies with size limits), prompt injection into AI features, insider abuse (RBAC, JIT access, audit logs, 4-eyes for bans/refunds), supply chain (SBOM, signed images). Mapped to OWASP Top 10 and API Top 10 with automated test coverage.

**53. How do you handle secrets and keys?**
No secrets in Git/images. AWS Secrets Manager/KMS → External Secrets Operator → K8s Secrets (encrypted with KMS envelope); IRSA gives pods scoped IAM roles; DB creds via IAM auth or rotated by Secrets Manager; JWT signing keys in KMS/HSM-backed with JWKS rotation every 90 days with overlap; TLS via ACM/cert-manager, mTLS between services with service mesh (Istio/Linkerd) or SPIFFE.

**54. Compare Kubernetes deployment strategies and which you'd use.**
Rolling (default, cheap), blue/green (instant rollback, double capacity), canary via Argo Rollouts (progressive 5→25→50→100% with automated analysis on SLI metrics). Use canary for gateway, search, payment, chat; rolling for low-risk consumers. DB changes follow expand/contract so any version pair is compatible; feature flags separate deploy from release. WebSocket draining: preStop hook + `terminationGracePeriodSeconds` to let clients reconnect smoothly (avoid reconnection storms with jittered backoff).

**55. How do you structure Terraform for multiple environments and safe operation?**
Reusable modules (vpc, eks, rds, msk, opensearch, elasticache, s3-cdn, waf, iam-irsa), thin per-env roots with pinned module versions; remote state in S3 with DynamoDB locking/KMS, separate state per layer (network, data, platform, apps) to limit blast radius; plan on PR (comment), apply via protected pipeline with OIDC role (no long-lived keys), `prevent_destroy` on data stores, drift detection nightly, policy-as-code (OPA/Checkov) gates, tagging for cost allocation.

**56. Observability: how do you design it end to end?**
Logs (JSON, PII-masked) → Fluent Bit → Logstash/Elasticsearch (Kibana) with ILM; metrics → Prometheus (RED/USE + business KPIs like signups, interests, payment success) → Grafana, Alertmanager→PagerDuty; traces via OpenTelemetry SDK/agent → Collector → Tempo/Jaeger with W3C `traceparent` propagated through HTTP, Kafka headers (and outbox `headers` column!) and WebSocket messages. Correlate by `traceId` in logs. Sampling: head 10% + tail-based sampling for errors/slow. Synthetic checks for login/search/pay; runbooks linked from alerts.

**57. Disaster recovery: RPO/RTO targets and design.**
Targets: RPO ≤ 5 min, RTO ≤ 1 h for tier-1 (identity, profile, payment); RPO ≤ 1 h for analytics. Multi-AZ everywhere; Aurora Global/cross-region snapshots + PITR; S3 CRR; MSK MirrorMaker 2 to DR region; ES snapshots to S3 (search can be rebuilt from DB via reindex job as the ultimate fallback); Terraform to recreate infra; quarterly game days with restore tests. Payment ledger reconciled after failover using provider as the source of truth.

**58. How do you reindex Elasticsearch with zero downtime and handle mapping changes?**
Create `profiles_v{n+1}` with the new mapping; start dual-write (projector writes to both) or capture a Kafka offset checkpoint; backfill via snapshot of source DB (batched, throttled, `_bulk`, `refresh_interval=-1`, replicas=0 during load); validate counts and sample queries/relevance regression suite; atomically switch the alias `profiles_read`→v(n+1) (single `_aliases` call); keep the old index for rollback, then delete. Out-of-order updates are guarded by external versioning (`version_type=external` using profile seq).

**59. Cost estimation: where does the money go and how do you control it?**
At ~1M registered / ~250k MAU (ap-south-1, indicative): EKS compute (~20–40 nodes mixed On-Demand + Spot) ≈ $6–9k/mo; Aurora PostgreSQL multi-AZ + replicas ≈ $2.5–4k; OpenSearch (3 masters + 6 data) ≈ $3–5k; MSK (3–6 brokers) ≈ $1.5–2.5k; ElastiCache ≈ $0.8–1.5k; S3+CloudFront media ≈ $1.5–4k; TURN/data transfer ≈ $0.5–1.5k; observability ≈ $1–2k; LLM/AI ≈ $2–6k (strongly usage-dependent); WAF/Shield/security ≈ $0.5–1k; SMS/WhatsApp/email ≈ $2–6k (OTP & notifications dominate — use WhatsApp/Push over SMS). Total ≈ $25–45k/month. Controls: Savings Plans/RIs for baseline, Spot for stateless, Graviton, right-sizing via VPA, ILM tiering on logs/ES, image variants & CDN cache ratio, LLM routing/caching/budgets, OTP via DLT-registered SMS with fallback only when push fails, FinOps tagging and per-service cost dashboards.

**60. If you had to cut scope for an MVP in 12 weeks with a team of ~12, what would you build, what would you defer, and how would you ensure the architecture doesn't need a rewrite?**
MVP: identity (mobile OTP + Google), profile (with Pahadi roots), media (photos + moderation basic), search (ES with core filters), interests + basic chat, notifications (push/email/SMS), subscription + Razorpay, admin (moderation & verification queue). Defer: video calling, AI recommendations/assistant (start with rule-based compatibility), horoscope matching beyond basic guna, WhatsApp, Stripe/international, analytics (use product analytics SaaS), fraud ML (start with rules). Deploy as 6–7 services (merge user+profile, moderation+admin, matchmaking+search initially) on EKS or even ECS. Preserve evolvability through: hexagonal boundaries, events with outbox from day one, versioned contracts, database-per-service schema separation (even if one cluster), and ADRs so services can be split along existing seams without a rewrite.

---

### Scoring guide for interviewers
- **Junior:** correct definitions, can read/modify code in the described layers.
- **Mid:** explains *why* and can implement patterns (outbox, idempotency, ES queries) and test them.
- **Senior:** reasons about failure modes, trade-offs, operability and cost; designs sub-systems end-to-end.
- **Architect:** frames business constraints (DPDP, cost, team size), sets SLOs/roadmap, and makes defensible decisions with an evolution path.
