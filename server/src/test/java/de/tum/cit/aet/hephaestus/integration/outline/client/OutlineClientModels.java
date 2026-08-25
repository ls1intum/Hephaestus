package de.tum.cit.aet.hephaestus.integration.outline.client;

import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlineApiKey;
import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlineAuth;
import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlineCollectionModel;
import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlineDocumentModel;
import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlineNavigationNode;
import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlineTeam;
import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlineUser;
import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlineWebhookSubscription;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.test.util.ReflectionTestUtils;

public final class OutlineClientModels {

    private OutlineClientModels() {}

    public static OutlineDocumentModel document(
        @Nullable String id,
        @Nullable String url,
        @Nullable String title,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt,
        @Nullable String urlId,
        @Nullable String parentDocumentId,
        @Nullable String collectionId,
        @Nullable OutlineUser createdBy,
        @Nullable OutlineUser updatedBy,
        @Nullable List<String> collaboratorIds,
        @Nullable Instant archivedAt
    ) {
        OutlineDocumentModel document = new OutlineDocumentModel(
            "fixture-id",
            "https://example.invalid/document",
            BigDecimal.ZERO,
            Instant.EPOCH,
            Instant.EPOCH,
            Instant.EPOCH,
            Instant.EPOCH,
            Instant.EPOCH
        );
        setField(document, "id", id);
        setField(document, "url", url);
        setField(document, "revision", null);
        setField(document, "createdAt", createdAt);
        setField(document, "updatedAt", updatedAt);
        setField(document, "publishedAt", null);
        setField(document, "archivedAt", archivedAt);
        setField(document, "deletedAt", null);
        document.setTitle(title);
        document.setUrlId(urlId);
        document.setParentDocumentId(parentDocumentId);
        document.setCollectionId(collectionId);
        document.setCreatedBy(createdBy);
        document.setUpdatedBy(updatedBy);
        document.setCollaboratorIds(collaboratorIds);
        return document;
    }

    public static OutlineUser user(@Nullable String id, @Nullable String name) {
        OutlineUser user = new OutlineUser(
            "fixture-id",
            "#000000",
            "fixture@example.invalid",
            false,
            Instant.EPOCH,
            Instant.EPOCH,
            Instant.EPOCH,
            Instant.EPOCH
        );
        setField(user, "id", id);
        setField(user, "color", null);
        setField(user, "email", null);
        setField(user, "isSuspended", null);
        setField(user, "lastActiveAt", null);
        setField(user, "createdAt", null);
        setField(user, "updatedAt", null);
        setField(user, "deletedAt", null);
        user.setName(name);
        return user;
    }

    public static OutlineCollectionModel collection(
        @Nullable String id,
        @Nullable String name,
        @Nullable String urlId,
        @Nullable String color,
        @Nullable String icon,
        @Nullable String description
    ) {
        OutlineCollectionModel collection = new OutlineCollectionModel(
            "fixture-id",
            "https://example.invalid/collection",
            "fixture-url-id",
            Instant.EPOCH,
            Instant.EPOCH,
            Instant.EPOCH,
            Instant.EPOCH
        );
        setField(collection, "id", id);
        setField(collection, "url", null);
        setField(collection, "urlId", urlId);
        setField(collection, "createdAt", null);
        setField(collection, "updatedAt", null);
        setField(collection, "deletedAt", null);
        setField(collection, "archivedAt", null);
        collection.setName(name);
        collection.setColor(color);
        collection.setIcon(icon);
        collection.setDescription(description);
        return collection;
    }

    public static OutlineNavigationNode node(
        String id,
        String title,
        String url,
        List<OutlineNavigationNode> children
    ) {
        return new OutlineNavigationNode().id(id).title(title).url(url).children(children);
    }

    public static OutlineApiKey apiKey(String id, String name, String last4, Instant expiresAt, Instant lastActiveAt) {
        return new OutlineApiKey().id(id).name(name).last4(last4).expiresAt(expiresAt).lastActiveAt(lastActiveAt);
    }

    public static OutlineWebhookSubscription webhookSubscription(
        String id,
        String name,
        String url,
        Boolean enabled,
        List<String> events
    ) {
        return new OutlineWebhookSubscription().id(id).name(name).url(url).enabled(enabled).events(events);
    }

    public static OutlineAuth auth(OutlineUser user, OutlineTeam team) {
        return new OutlineAuth().user(user).team(team);
    }

    public static OutlineTeam team(@Nullable String id, @Nullable String name) {
        OutlineTeam team = new OutlineTeam("fixture-id", URI.create("https://example.invalid/team"));
        setField(team, "id", id);
        setField(team, "url", null);
        team.setName(name);
        return team;
    }

    private static void setField(Object target, String name, @Nullable Object value) {
        ReflectionTestUtils.setField(target, name, value);
    }
}
