package in.pahadijodi.matchmaking.service;

import in.pahadijodi.common.outbox.OutboxService;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.matchmaking.domain.Interest;
import in.pahadijodi.matchmaking.domain.Interest.Status;
import in.pahadijodi.matchmaking.domain.MatchRecord;
import in.pahadijodi.matchmaking.domain.ProfileFacet;
import in.pahadijodi.matchmaking.domain.Repositories.*;
import in.pahadijodi.matchmaking.scoring.CompatibilityEngine;
import in.pahadijodi.matchmaking.service.Views.ProfileCard;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterestService {
    public record InterestView(UUID id, Status status, String message, Instant createdAt, Instant respondedAt, ProfileCard other) {}
    public record MatchView(UUID id, Instant createdAt, ProfileCard other) {}

    private final InterestRepository interests;
    private final MatchRecordRepository matches;
    private final ProfileFacetRepository facets;
    private final MatchService matchService;
    private final CompatibilityEngine engine;
    private final OutboxService outbox;

    public InterestService(InterestRepository interests, MatchRecordRepository matches, ProfileFacetRepository facets,
                           MatchService matchService, CompatibilityEngine engine, OutboxService outbox) {
        this.interests = interests; this.matches = matches; this.facets = facets;
        this.matchService = matchService; this.engine = engine; this.outbox = outbox;
    }

    /** Sends an interest. If the other person already sent one to you, this completes the match instead. */
    @Transactional
    public InterestView send(UUID userId, UUID toProfileId, String message) {
        ProfileFacet me = matchService.requireMe(userId);
        ProfileFacet to = facets.findById(toProfileId)
                .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profile not found"));
        var rejection = engine.rejectStructural(me, to);
        if (rejection.isPresent()) throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "NOT_ELIGIBLE", rejection.get());
        if (matchService.blockedUsers(me).contains(to.getUserId())) {
            throw new PjException(HttpStatus.FORBIDDEN, "NOT_ALLOWED", "You cannot send an interest to this member");
        }

        var reverse = interests.findByFromUserIdAndToUserId(to.getUserId(), userId);
        if (reverse.isPresent() && reverse.get().getStatus() == Status.SENT) {
            return view(accept(reverse.get()), to);                                   // mutual interest
        }
        var existing = interests.findByFromUserIdAndToUserId(userId, to.getUserId());
        Interest i;
        if (existing.isPresent()) {
            i = existing.get();
            switch (i.getStatus()) {
                case SENT -> throw new PjException(HttpStatus.CONFLICT, "ALREADY_SENT", "Interest already sent");
                case ACCEPTED -> throw new PjException(HttpStatus.CONFLICT, "ALREADY_MATCHED", "You are already matched");
                case DECLINED -> {
                    if (i.getRespondedAt() != null && i.getRespondedAt().isAfter(Instant.now().minus(MatchService.DECLINE_COOL_OFF))) {
                        throw new PjException(HttpStatus.CONFLICT, "COOLING_OFF", "You can send another interest after 90 days");
                    }
                    i.resend(message);
                }
                default -> i.resend(message);                                         // WITHDRAWN
            }
        } else {
            i = interests.save(new Interest(userId, to.getUserId(), me.getProfileId(), to.getProfileId(), message));
        }
        outbox.publish("pj.interest.sent.v1", "InterestSent", to.getUserId().toString(), payload(i));
        return view(i, to);
    }

    @Transactional
    public InterestView accept(UUID userId, UUID interestId) {
        Interest i = receivedPending(userId, interestId);
        return view(accept(i), facets.findById(i.getFromProfileId()).orElse(null));
    }

    private Interest accept(Interest i) {
        i.respond(Status.ACCEPTED);
        outbox.publish("pj.interest.accepted.v1", "InterestAccepted", i.getFromUserId().toString(), payload(i));
        MatchRecord m = MatchRecord.of(i.getFromUserId(), i.getFromProfileId(), i.getToUserId(), i.getToProfileId());
        if (matches.findByUserAAndUserB(m.getUserA(), m.getUserB()).isEmpty()) {
            matches.save(m);
            outbox.publish("pj.match.created.v1", "MatchCreated", m.getUserA().toString(),
                    Map.of("matchId", m.getId().toString(), "userAId", m.getUserA().toString(), "userBId", m.getUserB().toString(),
                            "profileAId", m.getProfileA().toString(), "profileBId", m.getProfileB().toString()));
        }
        return i;
    }

    @Transactional
    public InterestView decline(UUID userId, UUID interestId) {
        Interest i = receivedPending(userId, interestId);
        i.respond(Status.DECLINED);
        outbox.publish("pj.interest.declined.v1", "InterestDeclined", i.getFromUserId().toString(), payload(i));
        return view(i, facets.findById(i.getFromProfileId()).orElse(null));
    }

    @Transactional
    public InterestView withdraw(UUID userId, UUID interestId) {
        Interest i = interests.findById(interestId).filter(x -> x.getFromUserId().equals(userId))
                .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "INTEREST_NOT_FOUND", "Interest not found"));
        if (i.getStatus() != Status.SENT) throw new PjException(HttpStatus.CONFLICT, "NOT_PENDING", "Only pending interests can be withdrawn");
        i.respond(Status.WITHDRAWN);
        return view(i, facets.findById(i.getToProfileId()).orElse(null));
    }

    private Interest receivedPending(UUID userId, UUID id) {
        Interest i = interests.findById(id).filter(x -> x.getToUserId().equals(userId))
                .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "INTEREST_NOT_FOUND", "Interest not found"));
        if (i.getStatus() != Status.SENT) throw new PjException(HttpStatus.CONFLICT, "NOT_PENDING", "Interest already answered");
        return i;
    }

    @Transactional(readOnly = true)
    public List<InterestView> sent(UUID userId) {
        return interests.findByFromUserIdOrderByCreatedAtDesc(userId).stream()
                .map(i -> view(i, facets.findById(i.getToProfileId()).orElse(null))).toList();
    }

    @Transactional(readOnly = true)
    public List<InterestView> received(UUID userId) {
        return interests.findByToUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(i -> i.getStatus() != Status.WITHDRAWN)
                .map(i -> view(i, facets.findById(i.getFromProfileId()).orElse(null))).toList();
    }

    @Transactional(readOnly = true)
    public List<MatchView> myMatches(UUID userId) {
        return matches.forUser(userId).stream().map(m -> {
            UUID other = m.getUserA().equals(userId) ? m.getProfileB() : m.getProfileA();
            return new MatchView(m.getId(), m.getCreatedAt(), facets.findById(other).map(ProfileCard::of).orElse(null));
        }).toList();
    }

    private static Map<String, Object> payload(Interest i) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("interestId", i.getId().toString());
        p.put("fromUserId", i.getFromUserId().toString());
        p.put("toUserId", i.getToUserId().toString());
        p.put("fromProfileId", i.getFromProfileId().toString());
        p.put("toProfileId", i.getToProfileId().toString());
        return p;
    }

    private static InterestView view(Interest i, ProfileFacet other) {
        return new InterestView(i.getId(), i.getStatus(), i.getMessage(), i.getCreatedAt(), i.getRespondedAt(),
                other == null ? null : ProfileCard.of(other));
    }
}
