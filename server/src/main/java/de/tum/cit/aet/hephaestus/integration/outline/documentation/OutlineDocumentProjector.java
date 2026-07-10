package de.tum.cit.aet.hephaestus.integration.outline.documentation;

import de.tum.cit.aet.hephaestus.agent.documentation.DocumentProjection;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocument;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.integration.outline.identity.OutlineIdentityResolver;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outline-owned implementation of the agent {@link DocumentProjection} SPI: projects the mirrored
 * {@code outline_document} rows into the flat, agent-facing view the documentation content source materialises.
 * This is the sole agent-facing reader of the mirror — the agent consumes the projected payload through the SPI,
 * never the schema, so a column rename in {@code outline_document} is a compile-and-SQL change <em>inside
 * Outline</em>, not a silent runtime break in the agent.
 *
 * <p><strong>Tombstones and evictions.</strong> A document removed upstream ({@code deleted_at} set) or whose body
 * was evicted under the size cap comes back as a marker ({@code deleted = true} / {@code bodyMarkdown = null})
 * rather than being dropped, so a link to a vanished document still resolves to a placeholder. Reads are pure: this
 * projector never mutates {@code last_materialized_at} (that write belongs to the content source that actually
 * materialises a body into a sandbox run), which the {@code readOnly} transaction enforces.
 *
 * <p><strong>Authorship.</strong> The row's author substrate (subject + display name) projects as-is; the
 * workspace member id is resolved <em>lazily per batch</em> through {@link OutlineIdentityResolver} — the
 * connection context (server URL from the ACTIVE {@code OutlineConfig}, team id = the connection's
 * {@code instance_key}) is looked up once per projection call and each distinct subject resolves once (memoised).
 * An unlinked author degrades to name-only ({@code memberId = null}); no member id is ever stamped on the row.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
@Transactional(readOnly = true)
public class OutlineDocumentProjector implements DocumentProjection {

    /** Cap on documents surfaced to the mentor per turn — the corpus-breadth envelope. */
    static final int MAX_DOCUMENTS = 200;

    private final OutlineDocumentRepository documentRepository;
    private final ConnectionService connectionService;
    private final OutlineIdentityResolver identityResolver;

    public OutlineDocumentProjector(
        OutlineDocumentRepository documentRepository,
        ConnectionService connectionService,
        OutlineIdentityResolver identityResolver
    ) {
        this.documentRepository = documentRepository;
        this.connectionService = connectionService;
        this.identityResolver = identityResolver;
    }

    @Override
    public List<ProjectedDocument> documentsForWorkspace(long workspaceId) {
        AuthorContext authors = authorContext(workspaceId);
        return documentRepository
            .findForProjection(workspaceId, PageRequest.of(0, MAX_DOCUMENTS))
            .stream()
            .map(doc -> project(doc, authors))
            .toList();
    }

    @Override
    public List<ProjectedDocument> documentsByReference(long workspaceId, Collection<String> outlineDocIdsOrUrls) {
        if (outlineDocIdsOrUrls == null || outlineDocIdsOrUrls.isEmpty()) {
            return List.of();
        }
        // A reference is either a raw Outline document id or a document URL; match on the id and slug columns,
        // adding the last URL path segment as a candidate token so a link resolves to the mirrored row.
        Set<String> tokens = new LinkedHashSet<>();
        for (String ref : outlineDocIdsOrUrls) {
            if (ref == null || ref.isBlank()) {
                continue;
            }
            String trimmed = ref.trim();
            tokens.add(trimmed);
            int lastSlash = trimmed.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < trimmed.length() - 1) {
                tokens.add(trimmed.substring(lastSlash + 1));
            }
        }
        if (tokens.isEmpty()) {
            return List.of();
        }
        AuthorContext authors = authorContext(workspaceId);
        return documentRepository
            .findByWorkspaceIdAndReferenceIn(workspaceId, tokens)
            .stream()
            .map(doc -> project(doc, authors))
            .toList();
    }

    /** Maps a mirrored row to the agent view; a tombstoned/evicted document serves a null body. */
    private static ProjectedDocument project(OutlineDocument doc, AuthorContext authors) {
        boolean deleted = doc.isDeleted();
        String body = deleted ? null : doc.getBodyMarkdown();
        return new ProjectedDocument(
            orEmpty(doc.getCollectionSlug()),
            orEmpty(doc.getSlug()),
            orEmpty(doc.getTitle()),
            body,
            deleted,
            doc.getCreatedByName(),
            doc.getCreatedBySubject(),
            authors.memberIdFor(doc.getCreatedBySubject()),
            doc.getUpdatedByName(),
            doc.getUpdatedBySubject(),
            authors.memberIdFor(doc.getUpdatedBySubject())
        );
    }

    /**
     * The per-batch author-resolution context: the ACTIVE Outline connection's server URL and instance
     * key (= Outline team UUID), resolved ONCE per projection call. Without an ACTIVE connection (or a
     * config missing its server URL) authors degrade to name-only rather than failing the projection.
     */
    private AuthorContext authorContext(long workspaceId) {
        Optional<Connection> active = connectionService.findActive(workspaceId, IntegrationKind.OUTLINE);
        if (active.isEmpty() || !(active.get().getConfig() instanceof ConnectionConfig.OutlineConfig config)) {
            return AuthorContext.unresolvable();
        }
        String serverUrl = config.serverUrl();
        if (serverUrl == null || serverUrl.isBlank()) {
            return AuthorContext.unresolvable();
        }
        return new AuthorContext(identityResolver, workspaceId, serverUrl, active.get().getInstanceKey());
    }

    /** Memoises resolver lookups so each distinct author subject resolves at most once per batch. */
    private static final class AuthorContext {

        private final @Nullable OutlineIdentityResolver resolver;
        private final long workspaceId;
        private final @Nullable String serverUrl;
        private final @Nullable String teamId;
        private final Map<String, Optional<Long>> memberIdBySubject = new HashMap<>();

        private AuthorContext(
            @Nullable OutlineIdentityResolver resolver,
            long workspaceId,
            @Nullable String serverUrl,
            @Nullable String teamId
        ) {
            this.resolver = resolver;
            this.workspaceId = workspaceId;
            this.serverUrl = serverUrl;
            this.teamId = teamId;
        }

        static AuthorContext unresolvable() {
            return new AuthorContext(null, 0L, null, null);
        }

        @Nullable
        Long memberIdFor(@Nullable String subject) {
            if (resolver == null || serverUrl == null || subject == null || subject.isBlank()) {
                return null;
            }
            return memberIdBySubject
                .computeIfAbsent(subject, s -> resolver.resolveMemberId(workspaceId, serverUrl, teamId, s))
                .orElse(null);
        }
    }

    private static String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}
