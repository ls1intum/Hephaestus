package de.tum.cit.aet.hephaestus.integration.outline.client;

import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlineApiKey;
import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlineAuth;
import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlineCollectionModel;
import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlineDocumentModel;
import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlineNavigationNode;
import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlineTeam;
import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlineUser;
import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlineWebhookSubscription;
import java.time.Instant;
import java.util.List;

/**
 * Test factory for the generated Outline vendor models. The generated classes set read-only fields only
 * through their {@code @JsonCreator} constructor and writable fields through setters, which makes ad-hoc
 * construction verbose; these helpers reproduce the positional shape of the hand-written DTO records they
 * replaced, so a test reads the same as before and the migration stayed a mechanical rename.
 */
public final class OutlineClientModels {

    private OutlineClientModels() {}

    /** Mirrors the former {@code OutlineDocumentListResponse.Meta} constructor order. */
    public static OutlineDocumentModel document(
        String id,
        String url,
        String title,
        Instant createdAt,
        Instant updatedAt,
        String urlId,
        String parentDocumentId,
        String collectionId,
        OutlineUser createdBy,
        OutlineUser updatedBy,
        List<String> collaboratorIds,
        Instant archivedAt
    ) {
        // Read-only fields (id, url, createdAt, updatedAt, archivedAt) via the JsonCreator constructor;
        // revision/publishedAt/deletedAt are irrelevant to the sync and left null.
        OutlineDocumentModel document = new OutlineDocumentModel(
            id,
            url,
            null,
            createdAt,
            updatedAt,
            null,
            archivedAt,
            null
        );
        document.setTitle(title);
        document.setUrlId(urlId);
        document.setParentDocumentId(parentDocumentId);
        document.setCollectionId(collectionId);
        document.setCreatedBy(createdBy);
        document.setUpdatedBy(updatedBy);
        document.setCollaboratorIds(collaboratorIds);
        return document;
    }

    /** Mirrors the former {@code OutlineDocumentListResponse.OutlineUser} record. */
    public static OutlineUser user(String id, String name) {
        OutlineUser user = new OutlineUser(id, null, null, null, null, null, null, null);
        user.setName(name);
        return user;
    }

    /** Mirrors the former {@code OutlineCollectionListResponse.Collection} constructor order. */
    public static OutlineCollectionModel collection(
        String id,
        String name,
        String urlId,
        String color,
        String icon,
        String description
    ) {
        OutlineCollectionModel collection = new OutlineCollectionModel(id, null, urlId, null, null, null, null);
        collection.setName(name);
        collection.setColor(color);
        collection.setIcon(icon);
        collection.setDescription(description);
        return collection;
    }

    /** Mirrors the former {@code OutlineCollectionDocumentsResponse.Node} record. */
    public static OutlineNavigationNode node(
        String id,
        String title,
        String url,
        List<OutlineNavigationNode> children
    ) {
        return new OutlineNavigationNode().id(id).title(title).url(url).children(children);
    }

    /** Mirrors the former {@code OutlineApiKeyListResponse.ApiKey} record. */
    public static OutlineApiKey apiKey(String id, String name, String last4, Instant expiresAt, Instant lastActiveAt) {
        return new OutlineApiKey().id(id).name(name).last4(last4).expiresAt(expiresAt).lastActiveAt(lastActiveAt);
    }

    /** Mirrors the former {@code OutlineWebhookSubscriptionListResponse.Subscription} record. */
    public static OutlineWebhookSubscription webhookSubscription(
        String id,
        String name,
        String url,
        Boolean enabled,
        List<String> events
    ) {
        return new OutlineWebhookSubscription().id(id).name(name).url(url).enabled(enabled).events(events);
    }

    /** Identity payload of {@code auth.info}. */
    public static OutlineAuth auth(OutlineUser user, OutlineTeam team) {
        return new OutlineAuth().user(user).team(team);
    }

    /** A team reference: read-only id via the constructor, name via setter. */
    public static OutlineTeam team(String id, String name) {
        OutlineTeam team = new OutlineTeam(id, null);
        team.setName(name);
        return team;
    }
}
