# Pahadi Jodi: Uttarakhand Matrimonial Platform

Cloud-native matrimonial platform for the Uttarakhand (Pahadi) community and diaspora.
Design target: 1M+ registered users, ~3k peak RPS, ap-south-1 primary with DR region.

## Run it locally

Requirements: Java 21, Maven 3.9+, Node 20+ (Docker optional).

### Option A: zero infrastructure (H2 in memory, no Kafka/Redis)
```bash
mvn -q install                                   # build + test everything
SPRING_PROFILES_ACTIVE=h2 java -jar services/identity-service/target/identity-service-0.1.0-SNAPSHOT.jar
SPRING_PROFILES_ACTIVE=h2 java -jar services/profile-service/target/profile-service-0.1.0-SNAPSHOT.jar
java -jar services/api-gateway/target/api-gateway-0.1.0-SNAPSHOT.jar
cd apps/web && npm install && npm run dev        # http://localhost:5173
```
Run each java command in its own terminal. Data is lost on restart. The gateway's rate limiter
logs Redis errors and lets requests through when Redis is down.

### Option B: real infrastructure (Postgres, Redis, Kafka, Elasticsearch)
```bash
cp .env.example .env      # local defaults work as-is
make up                   # docker compose
make run-identity         # :8081, then in other terminals:
make run-profile          # :8082
make run-gateway          # :8080
make web                  # :5173
```
Events (e.g. `pj.profile.updated.v1`) are written to the outbox table and relayed to Kafka.

## What is implemented vs designed

| Piece | Status |
|---|---|
| `libs/pj-common`: problem+json errors, event envelope, transactional outbox + polling relay | implemented, built |
| `identity-service`: email register/login, Argon2id, RS256 JWT + JWKS, rotating refresh tokens with reuse detection | implemented, tested |
| `profile-service`: Uttarakhand profile (13 districts, Garhwal/Kumaon, community, gotra, village), completeness score, outbox events | implemented, tested |
| `api-gateway`: JWT validation, routing, Redis rate limits (per IP on auth, per user elsewhere) | implemented, tested |
| `apps/web`: login/register, profile form, dark mode, en/hi, Redux + React Query + RHF + Zod | typechecks and builds |
| `apps/mobile`: Expo login, secure token storage, biometric unlock | written, **not installed or run** |
| Other 14 services, OTP/social/2FA login, search, chat, payments, AI | designed in `docs/`, not yet coded |

Secrets: nothing is hardcoded. See `.env.example` for every variable to fill in later. Without
`PJ_JWT_PRIVATE_KEY_PEM`, identity-service generates a throwaway signing key at startup.

## Document map

| # | Deliverable | File |
|---|---|---|
| 1 | System design, HLD, diagrams, security, observability, scalability, cost | `docs/01-hld-system-design.md` |
| 2 | Services 1-6 (gateway, identity, user, profile, matchmaking, search) | `docs/02-services-core.md` |
| 3 | Services 7-11 (chat, notification, subscription, payment, AI recommendation) | `docs/03-services-engagement.md` |
| 4 | Services 12-17 (media, moderation, horoscope, analytics, admin, fraud) | `docs/04-services-trust-platform.md` |
| 5 | API contracts, Kafka event contracts, sequence diagrams | `docs/05-api-events-sequences.md` |
| 6 | LLD, folder structure, coding standards | `docs/06-lld-structure-standards.md` |
| 7 | Interview questions | `docs/07-interview-questions.md` |
| 8 | Deployment strategy | `docs/08-devops-deployment-strategy.md` |
| - | Terraform, Helm, ArgoCD, Kubernetes base | `infra/` |
| - | CI/CD pipelines | `.github/workflows/` |

## Known inconsistencies in the design docs
The docs were drafted in parallel and differ in places: Kubernetes namespaces (HLD says `pj-edge`/`pj-engage`/`pj-platform`,
infra files use `pj-gateway`/`pj-apps`/`pj-data`), Java package (`com.pahadijodi` in doc 06 vs `in.pahadijodi` in the code),
and some Kafka topic names. The running code follows `in.pahadijodi` and `pj.<domain>.<event>.v1`.
