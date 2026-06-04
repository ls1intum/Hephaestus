package de.tum.cit.aet.hephaestus.workspace.dto;

import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Metadata-only view of a workspace for the instance-admin overview. DELIBERATELY excludes any tenant
 * content (member lists, repo names, etc.) — an instance admin sees the shape of a workspace, and
 * reaches its content only via audited impersonation. Owner is the first OWNER-role member's git login.
 */
@Schema(description = "Metadata-only workspace summary for the instance-admin overview")
public record AdminWorkspaceViewDTO(
    @NonNull Long id,
    @NonNull String workspaceSlug,
    @NonNull String displayName,
    @NonNull String status,
    @NonNull String accountLogin,
    @Nullable GitProviderType providerType,
    @Nullable String ownerLogin,
    @NonNull Long memberCount,
    @NonNull Instant createdAt
) {}
