package de.tum.cit.aet.hephaestus.agent.mentor.weeklydigest;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountMembershipQuery.MembershipView;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataWriteFence;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.mentor.MentorConversation;
import de.tum.cit.aet.hephaestus.mentor.MentorConversationService;
import de.tum.cit.aet.hephaestus.mentor.MentorEntryDraft;
import de.tum.cit.aet.hephaestus.mentor.MentorEntryService;
import de.tum.cit.aet.hephaestus.practices.weeklydigest.WeeklyDigestFacts;
import de.tum.cit.aet.hephaestus.practices.weeklydigest.WeeklyDigestPayload;
import de.tum.cit.aet.hephaestus.practices.weeklydigest.WeeklyDigestWindow;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the weekly digests that are due: on Monday from {@code DUE} to {@code TOO_LATE} in the developer's own time
 * zone, one per workspace where the digest is available and the past local week was not quiet. After that the week is
 * skipped: a late recap is worth less than a quiet miss, and the feedback stays in the app.
 *
 * <p>Each digest is written in its own short transaction and is idempotent by its request id
 * {@code weekly-digest:{workspaceId}:{accountId}:{weekStart}}, so a crash or a second run writes nothing twice. The
 * transaction holds the account's person-data write fence first: an erasure waits for the write, and no digest is
 * written after an erasure was requested.
 */
@Component
@ConditionalOnServerRole
@WorkspaceAgnostic("Weekly digests are composed per opted-in account across its workspaces")
public class WeeklyDigestComposer {

    private static final Logger log = LoggerFactory.getLogger(WeeklyDigestComposer.class);

    static final LocalTime DUE = LocalTime.of(9, 0);
    static final LocalTime TOO_LATE = LocalTime.of(21, 0);

    private final WeeklyDigestSubscriptions subscriptions;
    private final WeeklyDigestAvailability availability;
    private final AccountDirectory accounts;
    private final WeeklyDigestFacts facts;
    private final MentorConversationService conversations;
    private final MentorEntryService entries;
    private final PersonDataWriteFence writeFence;
    private final List<WeeklyDigestTransport> transports;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public WeeklyDigestComposer(
            WeeklyDigestSubscriptions subscriptions,
            WeeklyDigestAvailability availability,
            AccountDirectory accounts,
            WeeklyDigestFacts facts,
            MentorConversationService conversations,
            MentorEntryService entries,
            PersonDataWriteFence writeFence,
            List<WeeklyDigestTransport> transports,
            ApplicationEventPublisher events,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.subscriptions = subscriptions;
        this.availability = availability;
        this.accounts = accounts;
        this.facts = facts;
        this.conversations = conversations;
        this.entries = entries;
        this.writeFence = writeFence;
        this.transports = transports.stream()
                .sorted(Comparator.comparing(WeeklyDigestTransport::pointer))
                .toList();
        this.events = events;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** The request id of one account's digest of one week: the key that makes writing it idempotent. */
    public static String requestId(long workspaceId, long accountId, WeeklyDigestWindow window) {
        return "weekly-digest:" + workspaceId + ":" + accountId + ":" + window.weekStart();
    }

    /** The fixed line the entry stores; see the package documentation for why it names no practice and no count. */
    static String text(WeeklyDigestWindow window) {
        return "Weekly digest for " + window.daysWithYear() + ". Read inputs/context/weekly_digest.json for it.";
    }

    /** Writes every digest that is due now; returns how many it wrote. */
    public int composeDue() {
        Instant now = clock.instant();
        int written = 0;
        for (long accountId : subscriptions.subscribedAccountIds()) {
            ZoneId zone = accounts.timeZone(accountId).orElse(AccountDirectory.DEFAULT_TIME_ZONE);
            if (!isDue(now, zone)) {
                continue;
            }
            WeeklyDigestWindow window = WeeklyDigestWindow.previousWeek(now, zone);
            for (MembershipView membership : availability.memberships(accountId)) {
                try {
                    if (compose(membership.workspaceId(), accountId, window)) {
                        written++;
                    }
                } catch (RuntimeException e) {
                    // Never rethrown: one developer's digest must not hold up the others. The next run tries again.
                    log.warn(
                            "Weekly digest not written: workspaceId={}, accountId={}, weekStart={}",
                            membership.workspaceId(),
                            accountId,
                            window.weekStart(),
                            e);
                }
            }
        }
        return written;
    }

    /** Monday, from {@code DUE} up to {@code TOO_LATE}, in the developer's time zone. */
    static boolean isDue(Instant now, ZoneId zone) {
        ZonedDateTime local = now.atZone(zone);
        LocalTime time = local.toLocalTime();
        return local.getDayOfWeek() == DayOfWeek.MONDAY && !time.isBefore(DUE) && time.isBefore(TOO_LATE);
    }

    /**
     * Writes one account's digest of the week in one workspace, unless it exists, the digest is not available there,
     * or the week was quiet.
     *
     * @return whether this call wrote it
     */
    private boolean compose(long workspaceId, long accountId, WeeklyDigestWindow window) {
        String requestId = requestId(workspaceId, accountId, window);
        // Skips the work of a digest already written; the idempotent append decides when two runs overlap.
        if (entries.exists(workspaceId, requestId)
                || !availability.available(workspaceId, accountId)
                || !facts.anythingIn(workspaceId, accountId, window)) {
            return false;
        }
        return Boolean.TRUE.equals(transaction.execute(status -> write(workspaceId, accountId, window, requestId)));
    }

    private boolean write(long workspaceId, long accountId, WeeklyDigestWindow window, String requestId) {
        if (!writeFence.holdForAccountWrite(accountId)) {
            return false;
        }
        Optional<WeeklyDigestPayload> payload = facts.compose(workspaceId, accountId, window);
        if (payload.isEmpty()) {
            return false;
        }
        MentorConversation main = conversations.findOrCreateMain(workspaceId, accountId);
        MentorEntryService.Appended appended = entries.appendOnce(
                workspaceId,
                main.getId(),
                MentorEntryDraft.digest(text(window), payload.get().toJson(objectMapper), requestId));
        if (!appended.inserted()) {
            return false;
        }
        events.publishEvent(new WeeklyDigestWritten(
                appended.entry().getId(),
                workspaceId,
                accountId,
                window.days(),
                pointer(workspaceId, accountId),
                clock.instant()));
        return true;
    }

    /** See {@link WeeklyDigestPointer}. */
    private WeeklyDigestPointer pointer(long workspaceId, long accountId) {
        return transports.stream()
                .filter(transport -> transport.reaches(workspaceId, accountId))
                .map(WeeklyDigestTransport::pointer)
                .findFirst()
                .orElse(WeeklyDigestPointer.NONE);
    }
}
