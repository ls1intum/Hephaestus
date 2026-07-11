package de.tum.cit.aet.hephaestus.integration.outline.client;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineCollectionDocumentsResponse;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineCollectionListResponse;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineDocumentInfoResponse;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineDocumentListResponse;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineWebhookSubscriptionListResponse;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.ObjectMapper;

/**
 * Pins the tolerant-reader contract for the Outline REST DTOs ({@code OutlineApiClient}'s response
 * types) against REAL response bodies captured live from a self-hosted Outline instance
 * (outline-test.felixdietrich.com) — {@code documents.list}, {@code documents.info} (a root document
 * and a nested child with {@code parentDocumentId} set), {@code collections.list},
 * {@code collections.documents} (a real nested tree), and {@code webhookSubscriptions.list} (signing
 * {@code secret} redacted; every other field is the live wire shape).
 *
 * <p>Real Outline responses carry many more fields than our narrow tolerant DTOs declare (e.g.
 * {@code documents.list}'s {@code text}, {@code tasks}, {@code revision}, {@code publishedAt}, …) — this
 * is exactly the shape drift a hand-authored fixture would not exercise. Booting the real
 * Spring-Boot-autoconfigured Jackson&nbsp;3 mapper (rather than a hand-built one), the same way
 * {@code MultiVersionFixtureTest} does for the SCM DTOs, is the point: unknown-field tolerance is mapper
 * <em>configuration</em>, not an annotation effect.
 */
@Tag("unit")
@SpringBootTest(classes = JacksonAutoConfiguration.class)
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
class OutlineApiFixtureDeserializationTest {

    @Autowired
    private ObjectMapper jackson3;

    @Test
    void documentsList_mapsRealDocumentsIncludingTheNestedChild() throws Exception {
        OutlineDocumentListResponse response = deserialize(
            "/outline-api/documents.list.json",
            OutlineDocumentListResponse.class
        );

        assertThat(response.data()).isNotNull().isNotEmpty();
        OutlineDocumentListResponse.Meta parent = findById(response.data(), "7d11d73d-1b36-43e3-9f31-b43e98c69b5b");
        assertThat(parent.title()).isEqualTo("Fixture Capture Doc Renamed");
        assertThat(parent.url()).isEqualTo("/doc/fixture-capture-doc-renamed-JpRHHJuY8M");
        assertThat(parent.urlId()).isEqualTo("JpRHHJuY8M");
        assertThat(parent.parentDocumentId()).isNull();
        assertThat(parent.collectionId()).isEqualTo("fbe68839-b131-44e2-bb93-0bc533d39193");
        assertThat(parent.createdAt()).isNotNull();
        assertThat(parent.updatedAt()).isNotNull();
        assertThat(parent.createdBy()).isNotNull();
        assertThat(parent.createdBy().name()).isEqualTo("Felix Admin");
        assertThat(parent.collaboratorIds()).isNotEmpty();

        // The nested child — proves parentDocumentId maps correctly off a real tree, not just a flat list.
        OutlineDocumentListResponse.Meta child = findById(response.data(), "cec98e59-623c-4392-a343-6e96b0995e51");
        assertThat(child.title()).isEqualTo("Fixture Capture Child 2");
        assertThat(child.parentDocumentId()).isEqualTo("7d11d73d-1b36-43e3-9f31-b43e98c69b5b");
    }

    @Test
    void documentsInfo_mapsARootDocumentWithNoParent() throws Exception {
        OutlineDocumentInfoResponse response = deserialize(
            "/outline-api/documents.info.json",
            OutlineDocumentInfoResponse.class
        );

        assertThat(response.data()).isNotNull();
        OutlineDocumentInfoResponse.Data data = response.data();
        assertThat(data.id()).isEqualTo("7d11d73d-1b36-43e3-9f31-b43e98c69b5b");
        assertThat(data.title()).isEqualTo("Fixture Capture Doc Renamed");
        assertThat(data.url()).isEqualTo("/doc/fixture-capture-doc-renamed-JpRHHJuY8M");
        assertThat(data.parentDocumentId()).isNull();
        assertThat(data.collectionId()).isEqualTo("fbe68839-b131-44e2-bb93-0bc533d39193");
        assertThat(data.createdAt()).isEqualTo(Instant.parse("2026-07-11T05:23:17.240Z"));
        assertThat(data.createdBy()).isNotNull();
        assertThat(data.updatedBy()).isNotNull();
        assertThat(data.collaboratorIds()).contains("99bdd8e2-176a-42fa-ba0c-4f9c4ce6caa9");
    }

    @Test
    void documentsInfo_mapsANestedChildWithItsParentId() throws Exception {
        OutlineDocumentInfoResponse response = deserialize(
            "/outline-api/documents.info.child.json",
            OutlineDocumentInfoResponse.class
        );

        OutlineDocumentInfoResponse.Data data = response.data();
        assertThat(data.id()).isEqualTo("cec98e59-623c-4392-a343-6e96b0995e51");
        assertThat(data.parentDocumentId()).isEqualTo("7d11d73d-1b36-43e3-9f31-b43e98c69b5b");
        assertThat(data.collectionId()).isEqualTo("fbe68839-b131-44e2-bb93-0bc533d39193");
    }

    @Test
    void collectionsList_mapsTheRealCollectionCatalog() throws Exception {
        OutlineCollectionListResponse response = deserialize(
            "/outline-api/collections.list.json",
            OutlineCollectionListResponse.class
        );

        assertThat(response.data()).isNotNull().isNotEmpty();
        OutlineCollectionListResponse.Collection engineering = response
            .data()
            .stream()
            .filter(c -> "fbe68839-b131-44e2-bb93-0bc533d39193".equals(c.id()))
            .findFirst()
            .orElseThrow();
        assertThat(engineering.name()).isEqualTo("Engineering Docs");
        assertThat(engineering.urlId()).isEqualTo("j4Gxqv1NCn");
    }

    @Test
    void collectionsDocuments_mapsTheRealNestedTree() throws Exception {
        OutlineCollectionDocumentsResponse response = deserialize(
            "/outline-api/collections.documents.json",
            OutlineCollectionDocumentsResponse.class
        );

        assertThat(response.data()).isNotNull().isNotEmpty();
        OutlineCollectionDocumentsResponse.Node parent = response
            .data()
            .stream()
            .filter(n -> "7d11d73d-1b36-43e3-9f31-b43e98c69b5b".equals(n.id()))
            .findFirst()
            .orElseThrow();
        assertThat(parent.title()).isEqualTo("Fixture Capture Doc Renamed");
        assertThat(parent.children()).isNotNull().isNotEmpty();
        assertThat(parent.children().get(0).id()).isEqualTo("cec98e59-623c-4392-a343-6e96b0995e51");
        assertThat(parent.children().get(0).url()).isEqualTo("/doc/fixture-capture-child-2-OHQpaAib7z");
    }

    @Test
    void webhookSubscriptionsList_mapsTheRegisteredSubscription() throws Exception {
        OutlineWebhookSubscriptionListResponse response = deserialize(
            "/outline-api/webhookSubscriptions.list.json",
            OutlineWebhookSubscriptionListResponse.class
        );

        assertThat(response.data()).isNotNull().hasSize(1);
        OutlineWebhookSubscriptionListResponse.Subscription subscription = response.data().get(0);
        assertThat(subscription.id()).isEqualTo("451e2dc4-010e-44e7-8052-8537a3927ba8");
        assertThat(subscription.enabled()).isTrue();
        assertThat(subscription.events()).contains("documents.update", "collections.delete");
        // The secret itself is redacted in the committed fixture — the DTO field still maps.
        assertThat(subscription.url()).isEqualTo("https://hephaestus-test.felixdietrich.com/webhooks/outline");
    }

    private static OutlineDocumentListResponse.Meta findById(List<OutlineDocumentListResponse.Meta> data, String id) {
        return data
            .stream()
            .filter(m -> id.equals(m.id()))
            .findFirst()
            .orElseThrow();
    }

    private <T> T deserialize(String classpath, Class<T> type) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(classpath)) {
            assertThat(in).as("fixture %s must be on the classpath", classpath).isNotNull();
            return jackson3.readValue(in.readAllBytes(), type);
        }
    }
}
