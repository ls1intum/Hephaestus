package de.tum.cit.aet.hephaestus.practices.research;

import de.tum.cit.aet.hephaestus.core.auth.spi.ResearchDisclosureAudit;
import de.tum.cit.aet.hephaestus.core.auth.spi.ResearchParticipationQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.ResearchParticipationQuery.GrantStanding;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.research.ResearchDisclosureSubject.RemovalReason;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Every write of the research disclosure aggregate, each in one short transaction. None of them reads
 * practice data or serialises a file, and none spans an HTTP transfer.
 */
@Component
@ConditionalOnServerRole
class ResearchDisclosureRecorder {

    private final ResearchDisclosureRepository disclosures;
    private final ResearchDisclosureSubjectRepository subjects;
    private final ResearchParticipationQuery consent;
    private final ResearchDisclosureAudit audit;

    ResearchDisclosureRecorder(
            ResearchDisclosureRepository disclosures,
            ResearchDisclosureSubjectRepository subjects,
            ResearchParticipationQuery consent,
            ResearchDisclosureAudit audit) {
        this.disclosures = disclosures;
        this.subjects = subjects;
        this.consent = consent;
        this.audit = audit;
    }

    record Release(
            UUID datasetId,
            long workspaceId,
            Instant windowFrom,
            Instant windowTo,
            Instant useUntil,
            String contentSha256,
            ResearchDataset.Counts counts) {}

    /**
     * The authorisation cut. The participants' account rows are share-locked first, as every consent
     * write and purge locks them, so a withdrawal either committed before this read and refuses the
     * release, or waits until the receipt has committed and becomes a downstream removal. Each
     * participant's current grant must be the very decision the snapshot read, not merely another yes.
     */
    @Transactional(timeout = 15)
    public ResearchDisclosure authorize(
            Release release, ResearchSnapshot snapshot, Map<Long, String> participantKeys, long adminId, Instant now) {
        if (!consent.currentQuestion().equals(Optional.of(snapshot.question()))) {
            throw changed();
        }
        Map<Long, Long> current = consent.lockCurrentGrants(participantKeys.keySet());
        for (Long account : participantKeys.keySet()) {
            if (!Objects.equals(snapshot.grants().get(account), current.get(account))) {
                throw changed();
            }
        }
        var disclosure = disclosures.save(ResearchDisclosure.builder()
                .id(release.datasetId())
                .workspaceId(release.workspaceId())
                .purpose(ResearchDisclosure.PURPOSE)
                .schemaName(ResearchDataset.SCHEMA)
                .schemaVersion(ResearchDataset.VERSION)
                .windowFrom(release.windowFrom())
                .windowTo(release.windowTo())
                .cutoffAt(snapshot.cutoff())
                .noticeVersion(snapshot.question().noticeVersion())
                .researchOrganization(snapshot.question().organization())
                .createdByAccountId(adminId)
                .createdAt(now)
                .contentSha256(release.contentSha256())
                .participantCount(release.counts().participants())
                .resultCount(release.counts().results())
                .problemCount(release.counts().problems())
                .feedbackCount(release.counts().feedback())
                .feedbackExcludedCount(release.counts().feedbackExcludedIncompleteBasis())
                .useUntil(release.useUntil())
                .build());
        List<ResearchDisclosureSubject> rows = new ArrayList<>();
        participantKeys.forEach((account, key) -> rows.add(new ResearchDisclosureSubject(
                release.datasetId(),
                release.workspaceId(),
                account,
                key,
                Objects.requireNonNull(current.get(account)))));
        subjects.saveAll(rows);
        subjects.flush();
        boolean audited = audit.recordRelease(
                adminId,
                release.workspaceId(),
                release.datasetId(),
                ResearchDataset.VERSION,
                new ResearchDisclosureAudit.Counts(
                        release.counts().participants(),
                        release.counts().results(),
                        release.counts().feedback()));
        if (!audited) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "The release could not be audited. Nothing was released.");
        }
        return disclosure;
    }

    record Obligation(String participantKey, RemovalReason reason, Instant requiredAt) {}

    record OpenDisclosure(
            UUID datasetId,
            long workspaceId,
            Instant createdAt,
            Instant useUntil,
            boolean useExpired,
            List<Obligation> removals) {}

    /**
     * Compares every still-linked participant's original grant with the ledger today, records any new
     * obligation, and lists every open disclosure, including those with no removal owed. An obligation,
     * once recorded, stays: a later regrant does not cancel a removal already owed for data released under
     * the earlier grant.
     */
    @Transactional(timeout = 30)
    public List<OpenDisclosure> detectRemovals(Instant now) {
        List<ResearchDisclosure> open = disclosures.findOpen();
        if (open.isEmpty()) return List.of();
        Map<UUID, List<ResearchDisclosureSubject>> byDisclosure =
                subjects
                        .findForDisclosures(
                                open.stream().map(ResearchDisclosure::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(ResearchDisclosureSubject::getDisclosureId));
        List<OpenDisclosure> manifest = new ArrayList<>();
        for (ResearchDisclosure disclosure : open) {
            List<ResearchDisclosureSubject> rows = byDisclosure.getOrDefault(disclosure.getId(), List.of());
            // One ledger read per disclosure: an account appears at most once in each, under one grant.
            Map<Long, Long> linked = new HashMap<>();
            for (var row : rows) {
                if (row.getRemovalReason() == null && row.getAccountId() != null && row.getGrantDecisionId() != null) {
                    linked.put(row.getAccountId(), row.getGrantDecisionId());
                }
            }
            Map<Long, GrantStanding> standings = consent.grantStandings(linked);
            List<Obligation> owed = new ArrayList<>();
            for (var row : rows) {
                RemovalReason reason = row.getAccountId() == null ? null : reason(standings.get(row.getAccountId()));
                if (reason != null) row.requireRemoval(reason, now);
                if (row.isRemovalOutstanding()) {
                    owed.add(new Obligation(
                            row.getParticipantKey(),
                            Objects.requireNonNull(row.getRemovalReason()),
                            Objects.requireNonNull(row.getRemovalRequiredAt())));
                }
            }
            boolean expired = disclosure.isUseExpired(now);
            manifest.add(new OpenDisclosure(
                    disclosure.getId(),
                    disclosure.getWorkspaceId(),
                    disclosure.getCreatedAt(),
                    disclosure.getUseUntil(),
                    expired,
                    List.copyOf(owed)));
        }
        return List.copyOf(manifest);
    }

    /** The operator's attestation that these participants were removed from the retained copy. */
    @Transactional(timeout = 15)
    public int recordRemoved(UUID datasetId, Collection<String> participantKeys, long adminId, Instant now) {
        ResearchDisclosure disclosure = openForUpdate(datasetId);
        Set<String> requested = Set.copyOf(participantKeys);
        Map<String, ResearchDisclosureSubject> rows =
                subjects.findForDisclosure(disclosure.getWorkspaceId(), datasetId).stream()
                        .collect(Collectors.toMap(ResearchDisclosureSubject::getParticipantKey, row -> row));
        for (String key : requested) {
            ResearchDisclosureSubject row = rows.get(key);
            if (row == null || !row.isRemovalOutstanding()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Only participants with an outstanding removal can be marked removed.");
            }
        }
        requested.forEach(key -> Objects.requireNonNull(rows.get(key)).recordRemoved(now));
        subjects.flush();
        requireAudited(audit.recordRemoval(adminId, disclosure.getWorkspaceId(), datasetId, requested.size(), null));
        return requested.size();
    }

    /**
     * The operator's attestation that the whole retained copy was deleted or anonymised. Only now is the
     * protected mapping deleted; the header stays as a receipt without subject identity.
     */
    @Transactional(timeout = 15)
    public void close(UUID datasetId, ResearchDisclosure.Closure closure, long adminId, Instant now) {
        ResearchDisclosure disclosure = openForUpdate(datasetId);
        disclosure.close(closure, now);
        int removed = subjects.deleteForDisclosure(disclosure.getWorkspaceId(), datasetId);
        requireAudited(audit.recordRemoval(adminId, disclosure.getWorkspaceId(), datasetId, removed, closure.name()));
    }

    private ResearchDisclosure openForUpdate(UUID datasetId) {
        ResearchDisclosure disclosure = disclosures
                .findByIdForUpdate(datasetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "The dataset was not found."));
        if (disclosure.isClosed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The dataset is already closed.");
        }
        return disclosure;
    }

    private static void requireAudited(boolean audited) {
        if (!audited) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "The receipt could not be audited. Nothing was recorded.");
        }
    }

    static @Nullable RemovalReason reason(@Nullable GrantStanding standing) {
        if (standing == null) return null;
        return switch (standing) {
            case CURRENT, ACCOUNT_INACTIVE -> null;
            case WITHDRAWN -> RemovalReason.CONSENT_WITHDRAWN;
            case SUPERSEDED -> RemovalReason.CONSENT_SUPERSEDED;
            case QUESTION_CHANGED -> RemovalReason.QUESTION_CHANGED;
            case ACCOUNT_ERASED -> RemovalReason.ACCOUNT_ERASED;
        };
    }

    private static ResponseStatusException changed() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "Research consent changed while the dataset was built. Nothing was released.");
    }
}
