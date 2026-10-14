package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.capture.CaptureContribution;
import de.tum.cit.aet.hephaestus.evidence.capture.CaptureRequest;
import de.tum.cit.aet.hephaestus.evidence.capture.CitableText;
import de.tum.cit.aet.hephaestus.evidence.capture.EvidenceDirectory;
import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.integration.core.spi.RoleHolder;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.LinkedWorkItemContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.RepositoryTreeContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.ScmCaptureContext;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.ScmCapturePreparation;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.ScmCapturedWork;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.ScmCitableTexts;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReviewRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.events.ScmEventPayload;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Resolves the provider-neutral SCM kinds through exact workspace-owned item references. */
@Service
@ConditionalOnCaptureRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScmItemService {
    private final ScmItemAccess items;
    private final ScmItemLabelQuery labels;
    private final ScmRepositoryAccess repositories;
    private final RepositoryRepository repositoryRows;
    private final IssueRepository issues;
    private final PullRequestReviewRepository reviews;
    private final IssueEvidenceVersion issueVersions;
    private final ScmGitTree tree;
    private final ScmFileVersions fileVersions;
    private final ObjectMapper mapper;
    private final ScmWorkCapture workCapture;
    private final ScmCaptureContributors contributors;

    public Map<Long, ItemLabel> labels(long workspaceId, Collection<ItemRef> refs) {
        items.requireAll(workspaceId, refs);
        return labels.labels(workspaceId, refs.stream().map(ItemRef::itemId).toList());
    }

    public Optional<ItemSnapshot> current(ItemRef item) {
        try {
            return Optional.of(pin(item, null));
        } catch (ItemCaptureException e) {
            if (e.code() == ItemCaptureCode.ITEM_REMOVED || e.code() == ItemCaptureCode.ITEM_NOT_VISIBLE)
                return Optional.empty();
            throw e;
        }
    }

    public ItemSnapshot pin(ItemRef item, @Nullable ItemVersion requested) {
        items.require(item);
        if (item.kind().equals(ScmItemEvents.FILE) ? item.path() == null : item.sourceId() == null) throw invisible();
        ItemSnapshot snapshot;
        if (item.kind().equals(ScmItemEvents.REPOSITORY)) snapshot = repository(item, requested);
        else if (item.kind().equals(ScmItemEvents.FILE)) snapshot = file(item, requested);
        else snapshot = work(item);
        if (requested != null && !requested.equals(snapshot.version())) throw versionUnavailable();
        return snapshot;
    }

    private ItemSnapshot work(ItemRef item) {
        PullRequestReview review = null;
        ItemRef workItem = item;
        Issue work;
        if (item.kind().equals(ScmItemEvents.PULL_REQUEST_REVIEW)) {
            review = reviews.findById(Objects.requireNonNull(item.sourceId())).orElseThrow(ScmItemService::gone);
            work = Objects.requireNonNull(review.getPullRequest(), "Provider review has no pull request");
            workItem = items.parent(item, ScmItemEvents.PULL_REQUEST);
            if (!Objects.equals(workItem.sourceId(), work.getId())) throw invisible();
        } else {
            work = issues.findByIdWithAuthorAndRepository(Objects.requireNonNull(item.sourceId()))
                    .orElseThrow(ScmItemService::gone);
            if ((work instanceof PullRequest) != item.kind().equals(ScmItemEvents.PULL_REQUEST)
                    || (!item.kind().equals(ScmItemEvents.ISSUE) && !item.kind().equals(ScmItemEvents.PULL_REQUEST)))
                throw invisible();
        }
        if (work.getDeletedAt() != null) throw gone();
        Repository repository = Objects.requireNonNull(work.getRepository());
        ItemRef holder = items.parent(workItem, ScmItemEvents.REPOSITORY);
        if (!Objects.equals(holder.sourceId(), repository.getId())) throw invisible();
        IntegrationRef integration = repositories.authorize(item.workspaceId(), repository);
        List<RoleHolder> roles = new ArrayList<>();
        Map<String, String> state = new HashMap<>();
        state.put("state", work.getState().name());
        if (work instanceof PullRequest pull && !pull.getBaseRefName().isBlank())
            state.put("baseBranch", pull.getBaseRefName());
        ItemVersion version;
        if (review != null) {
            add(roles, AboutRole.REVIEWER, review.getAuthor());
            PullRequest pull = (PullRequest) work;
            version = ScmVersion.pullRequestReview(
                    objectId(pull.getBaseRefOid()),
                    objectId(pull.getHeadRefOid()),
                    pull.getTitle(),
                    pull.getBody(),
                    new ScmVersion.ReviewContent(
                            objectId(review.getCommitId()),
                            review.getBody(),
                            review.getState().name()));
            state.put("reviewState", review.getState().name());
        } else {
            add(roles, AboutRole.AUTHOR, work.getAuthor());
            work.getAssignees().stream()
                    .sorted(Comparator.comparing(User::getId))
                    .forEach(u -> add(roles, AboutRole.ASSIGNEE, u));
            if (work instanceof PullRequest pull) {
                add(roles, AboutRole.MERGER, pull.getMergedBy());
                version = ScmVersion.pullRequest(
                        objectId(pull.getBaseRefOid()),
                        objectId(pull.getHeadRefOid()),
                        pull.getTitle(),
                        pull.getBody());
                var linkedVersion = LinkedWorkItemContentSource.currentClosingMaterialVersion(
                        pull, List.copyOf(pull.getClosingIssues()));
                if (linkedVersion.isPresent()) version = ScmVersion.withContext(version, linkedVersion.get());
                state.put("draft", Boolean.toString(pull.isDraft()));
                state.put("merged", Boolean.toString(pull.isMerged()));
            } else version = issueVersions.of(ScmEventPayload.IssueData.from(work));
        }
        String noun = work instanceof PullRequest
                ? integration.kind() == IntegrationKind.GITLAB ? "merge request" : "pull request"
                : "issue";
        if (review != null) noun = "review";
        ItemLabel label = new ItemLabel(
                item.itemId(),
                review == null ? work.getTitle() : "Review of " + work.getTitle(),
                noun,
                work.getNumber(),
                repository.getNameWithOwner(),
                review == null ? work.getHtmlUrl() : review.getHtmlUrl(),
                integration.kind());
        return new ItemSnapshot(item, version, integration, state, roles, label);
    }

    private ItemSnapshot repository(ItemRef item, @Nullable ItemVersion requested) {
        Repository repository =
                repositoryRows.findById(Objects.requireNonNull(item.sourceId())).orElseThrow(ScmItemService::gone);
        IntegrationRef integration = repositories.authorize(item.workspaceId(), repository);
        var key = repositories.prepare(item.workspaceId(), repository);
        if (requested == null && repository.getDefaultBranch() == null) throw versionUnavailable();
        String sha = requested == null
                ? tree.resolve(key, "refs/heads/" + Objects.requireNonNull(repository.getDefaultBranch()))
                : objectId(requested.value());
        tree.inventory(key, sha);
        return new ItemSnapshot(
                item,
                new ItemVersion(sha, null),
                integration,
                repository.getDefaultBranch() == null
                        ? Map.of()
                        : Map.of("defaultBranch", repository.getDefaultBranch()),
                List.of(),
                new ItemLabel(
                        item.itemId(),
                        repository.getNameWithOwner(),
                        "repository",
                        null,
                        null,
                        repository.getHtmlUrl(),
                        integration.kind()));
    }

    private ItemSnapshot file(ItemRef item, @Nullable ItemVersion requested) {
        ItemRef parent = items.parent(item, ScmItemEvents.REPOSITORY);
        if (requested != null && requested.baseVersion() == null) throw versionUnavailable();
        ItemSnapshot holder = repository(
                parent,
                requested == null
                        ? null
                        : new ItemVersion(
                                Objects.requireNonNull(
                                        requested.baseVersion(), "File version has no repository version"),
                                null));
        Repository repository = repositoryRows
                .findById(Objects.requireNonNull(parent.sourceId()))
                .orElseThrow(ScmItemService::gone);
        var key = repositories.prepare(item.workspaceId(), repository);
        var inventory = tree.inventory(key, holder.version().value());
        var file = inventory.stream()
                .filter(e -> e.path().equals(item.path()))
                .findFirst()
                .orElseThrow(ScmItemService::gone);
        ItemVersion version = fileVersions.version(
                file.path(),
                file.objectId(),
                file.mode(),
                List.of(),
                List.of(),
                holder.version().value());
        if (requested != null) {
            String[] fields = requested.value().split(":", -1);
            if (fields.length != 4
                    || !fields[0].equals("scm.file")
                    || !fields[1].equals("v1")
                    || !fields[2].equals(file.objectId())
                    || !fields[3].matches("[0-9a-f]{64}")) throw versionUnavailable();
            version = requested;
        }
        return new ItemSnapshot(
                item,
                version,
                holder.integration(),
                Map.of("mode", file.mode()),
                List.of(),
                new ItemLabel(
                        item.itemId(),
                        file.path(),
                        "file",
                        null,
                        repository.getNameWithOwner(),
                        null,
                        holder.integration().kind()));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CaptureContribution capture(ItemSnapshot pinned, CaptureRequest request) {
        ItemSnapshot verified = pin(pinned.item(), pinned.version());
        if (!verified.integration().equals(pinned.integration())
                || !verified.roles().equals(pinned.roles())) throw versionUnavailable();
        if (!request.sourceContractVersion().equals("2.0.0") || request.scriptContractVersion() != 3)
            throw new ItemCaptureException(ItemCaptureCode.CAPTURE_FAILED, "Capture contract is unavailable");
        if (pinned.item().kind().equals(ScmItemEvents.REPOSITORY)
                || pinned.item().kind().equals(ScmItemEvents.FILE)) return captureTree(pinned, request);
        Issue work;
        PullRequestReview review = null;
        if (pinned.item().kind().equals(ScmItemEvents.PULL_REQUEST_REVIEW)) {
            review = reviews.findById(Objects.requireNonNull(pinned.item().sourceId()))
                    .orElseThrow(ScmItemService::gone);
            work = Objects.requireNonNull(review.getPullRequest());
        } else
            work = issues.findByIdWithAuthorAndRepository(
                            Objects.requireNonNull(pinned.item().sourceId()))
                    .orElseThrow(ScmItemService::gone);
        contributors.require(work, review, request.sources());
        Repository repository = Objects.requireNonNull(work.getRepository());
        PullRequest pull = work instanceof PullRequest p ? p : null;
        ScmCapturedWork frozen = new ScmCapturedWork(
                work.getId(),
                repository.getId(),
                work.getNumber(),
                work.getHtmlUrl(),
                repository.getNameWithOwner(),
                work.getTitle(),
                work.getBody(),
                pull == null ? null : pull.getHeadRefOid(),
                pull == null ? null : pull.getBaseRefOid(),
                pull == null ? null : pull.getHeadRefName(),
                pull == null ? null : pull.getBaseRefName(),
                review == null ? null : review.getId(),
                review == null || review.getAuthor() == null
                        ? null
                        : review.getAuthor().getId());
        ScmCaptureContext context =
                new ScmCaptureContext(pinned, request, frozen, new ScmCapturePreparation(), Instant.now());
        return workCapture.capture(context);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public JsonNode record(ItemSnapshot pinned, Map<String, byte[]> capturedFiles) {
        ItemKind kind = pinned.item().kind();
        boolean treeRecord = kind.equals(ScmItemEvents.FILE) || kind.equals(ScmItemEvents.REPOSITORY);
        if (!treeRecord
                && !kind.equals(ScmItemEvents.ISSUE)
                && !kind.equals(ScmItemEvents.PULL_REQUEST)
                && !kind.equals(ScmItemEvents.PULL_REQUEST_REVIEW)) throw invisible();
        byte[] bytes = capturedFiles.get(treeRecord ? "context/item.json" : "context/metadata.json");
        if (bytes == null)
            throw new ItemCaptureException(ItemCaptureCode.CAPTURE_FAILED, "Governed SCM item record is unavailable");
        try {
            JsonNode record = mapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(bytes);
            if (!record.isObject())
                throw new ItemCaptureException(
                        ItemCaptureCode.CAPTURE_FAILED, "Captured SCM item record is not an object");
            if (kind.equals(ScmItemEvents.FILE)) {
                String[] version = pinned.version().value().split(":", -1);
                if (version.length != 4
                        || !version[0].equals("scm.file")
                        || !version[1].equals("v1")
                        || !Objects.equals(
                                pinned.item().path(), record.path("path").asString())
                        || !version[2].equals(record.path("blobId").asString())
                        || !Objects.equals(
                                pinned.version().baseVersion(),
                                record.path("commitId").asString())) throw versionUnavailable();
            } else if (kind.equals(ScmItemEvents.REPOSITORY)) {
                if (!Long.toString(Objects.requireNonNull(pinned.item().sourceId()))
                                .equals(record.path("repositoryId").asString())
                        || !pinned.version()
                                .value()
                                .equals(record.path("commitId").asString())) throw versionUnavailable();
            } else if ((kind.equals(ScmItemEvents.PULL_REQUEST) || kind.equals(ScmItemEvents.PULL_REQUEST_REVIEW))
                    && !ScmVersion.reviewedHead(pinned.version())
                            .equals(record.path("commit_sha").asString())) throw versionUnavailable();
            return record;
        } catch (ItemCaptureException refusal) {
            throw refusal;
        } catch (RuntimeException malformed) {
            throw new ItemCaptureException(
                    ItemCaptureCode.CAPTURE_FAILED, "Captured SCM item record is invalid", malformed);
        }
    }

    private CaptureContribution captureTree(ItemSnapshot pinned, CaptureRequest request) {
        SourceKind source = RepositoryTreeContentSource.KIND;
        if (!request.sources().contains(source)) return ScmCaptureContributions.combine(List.of());
        ItemRef holder = pinned.item().kind().equals(ScmItemEvents.FILE)
                ? items.parent(pinned.item(), ScmItemEvents.REPOSITORY)
                : pinned.item();
        Repository repository = repositoryRows
                .findById(Objects.requireNonNull(holder.sourceId()))
                .orElseThrow(ScmItemService::gone);
        var key = repositories.prepare(holder.workspaceId(), repository);
        String sha = pinned.item().kind().equals(ScmItemEvents.FILE)
                ? Objects.requireNonNull(pinned.version().baseVersion())
                : pinned.version().value();
        var inventory = tree.inventory(key, sha);
        var record = mapper.createObjectNode();
        record.put("repositoryId", Long.toString(repository.getId()));
        record.put(
                "providerId",
                Long.toString(Objects.requireNonNull(
                        repository.getProvider().getId(), "Persisted SCM provider requires an id")));
        if (pinned.item().path() != null) {
            var entry = inventory.stream()
                    .filter(e -> e.path().equals(pinned.item().path()))
                    .findFirst()
                    .orElseThrow(ScmItemService::gone);
            tree.content(key, entry);
            record.put("path", entry.path());
            record.put("blobId", entry.objectId());
        }
        record.put("commitId", sha);
        byte[] recordBytes = mapper.writeValueAsBytes(record);
        byte[] inventoryBytes = mapper.writeValueAsBytes(inventory);
        var snapshot = tree.snapshot(key, sha);
        var metadataFiles =
                new LinkedHashMap<>(Map.of("context/inventory.json", inventoryBytes, "context/item.json", recordBytes));
        List<CitableText> citable;
        try {
            citable = new ArrayList<>(ScmCitableTexts.tree(snapshot.stagingDir(), sha));
            citable.addAll(ScmCitableTexts.files(metadataFiles, path -> source, mapper));
        } catch (RuntimeException failure) {
            snapshot.close();
            throw failure;
        }
        return new CaptureContribution(
                metadataFiles,
                Map.of(source, snapshot.complete() ? SourceCompleteness.COMPLETE : SourceCompleteness.PARTIAL),
                Map.of(source, sha),
                Map.of(source, Instant.now()),
                Map.of(),
                Map.of(source, inventory.isEmpty() ? SourceContentState.EMPTY : SourceContentState.NON_EMPTY),
                Map.of(),
                Map.of(),
                snapshot,
                snapshot.limitations().isEmpty() ? Map.of() : Map.of(source, List.copyOf(snapshot.limitations())),
                List.of(new EvidenceDirectory("repos/reviewed/", snapshot.stagingDir())),
                List.of(),
                citable);
    }

    private static void add(List<RoleHolder> roles, AboutRole role, @Nullable User user) {
        if (user != null)
            roles.add(new RoleHolder(
                    role,
                    new PersonIdentity(
                            Objects.requireNonNull(user.getProvider().getId(), "Persisted SCM provider requires an id"),
                            Long.toString(user.getNativeId()),
                            null),
                    user.getType() == User.Type.USER));
    }

    private static String objectId(@Nullable String value) {
        if (value == null || !value.matches("[0-9a-f]{40}|[0-9a-f]{64}")) throw versionUnavailable();
        return value;
    }

    private static ItemCaptureException gone() {
        return new ItemCaptureException(ItemCaptureCode.ITEM_REMOVED, "SCM item is unavailable");
    }

    private static ItemCaptureException invisible() {
        return new ItemCaptureException(
                ItemCaptureCode.ITEM_NOT_VISIBLE, "SCM item does not belong to this workspace reference");
    }

    private static ItemCaptureException versionUnavailable() {
        return new ItemCaptureException(ItemCaptureCode.VERSION_UNAVAILABLE, "Pinned SCM version is unavailable");
    }
}
