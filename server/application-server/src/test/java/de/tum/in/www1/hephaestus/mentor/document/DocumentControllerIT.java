package de.tum.in.www1.hephaestus.mentor.document;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithMentorUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureWebTestClient
@Transactional
public class DocumentControllerIT extends BaseIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setup() {
        testUser = userRepository.findByLogin("mentor").orElseThrow();
    }

    // ==================== Document CRUD Operations ====================

    @Test
    @WithMentorUser
    void shouldCreateDocumentSuccessfully() {
        // Arrange
        var request = new CreateDocumentRequestDTO("My First Document", "This is the content", DocumentKind.TEXT);

        // Act & Assert
        webTestClient
            .post()
            .uri("/api/documents") // Plural resource name
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isCreated() // 201 for creation
            .expectBody(DocumentDTO.class)
            .value(response -> {
                assertThat(response.id()).isNotNull();
                assertThat(response.title()).isEqualTo("My First Document");
                assertThat(response.content()).isEqualTo("This is the content");
                assertThat(response.kind()).isEqualTo(DocumentKind.TEXT);
                assertThat(response.createdAt()).isNotNull();
            });
    }

    @Test
    @WithMentorUser
    void shouldGetDocumentByIdSuccessfully() {
        // Arrange - Create document first
        UUID documentId = createTestDocument("Test Document", "Test content");

        // Act & Assert
        webTestClient
            .get()
            .uri("/api/documents/{id}", documentId) // Path parameter
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(DocumentDTO.class)
            .value(response -> {
                assertThat(response.id()).isEqualTo(documentId);
                assertThat(response.title()).isEqualTo("Test Document");
                assertThat(response.content()).isEqualTo("Test content");
            });
    }

    @Test
    @WithMentorUser
    void shouldUpdateDocumentSuccessfully() {
        // Arrange - Create document first
        UUID documentId = createTestDocument("Original Title", "Original content");

        var updateRequest = new UpdateDocumentRequestDTO("Updated Title", "Updated content", DocumentKind.TEXT);

        // Act & Assert
        webTestClient
            .put()
            .uri("/api/documents/{id}", documentId) // PUT for updates
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(DocumentDTO.class)
            .value(response -> {
                assertThat(response.id()).isEqualTo(documentId);
                assertThat(response.title()).isEqualTo("Updated Title");
                assertThat(response.content()).isEqualTo("Updated content");
                assertThat(response.createdAt()).isNotNull();
            });

        // Assert - Should have created new version
        var allVersions = documentRepository.findByIdAndUserOrderByCreatedAtDesc(documentId, testUser);
        assertThat(allVersions).hasSize(2);
        assertThat(allVersions.get(0).getTitle()).isEqualTo("Updated Title"); // Latest first
        assertThat(allVersions.get(1).getTitle()).isEqualTo("Original Title");
    }

    @Test
    @WithMentorUser
    void shouldDeleteDocumentSuccessfully() {
        // Arrange
        UUID documentId = createTestDocument("To Delete", "Content");

        // Act & Assert
        webTestClient
            .delete()
            .uri("/api/documents/{id}", documentId)
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNoContent(); // 204 for successful deletion

        // Assert - Document should be gone
        var documents = documentRepository.findByIdAndUser(documentId, testUser);
        assertThat(documents).isEmpty();
    }

    @Test
    @WithMentorUser
    void shouldGetAllUserDocumentsSuccessfully() {
        // Arrange - Create documents with unique identifiers to avoid test pollution
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String doc1Title = "UniqueDoc1_" + uniqueId;
        String doc2Title = "UniqueDoc2_" + uniqueId;

        createTestDocument(doc1Title, "Content 1");
        createTestDocument(doc2Title, "Content 2");

        // Act & Assert - Use proper DTO and filter our test data
        webTestClient
            .get()
            .uri("/api/documents")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.content")
            .isArray()
            .consumeWith(result -> {
                // Extract the page content and verify our documents are there
                var body = result.getResponseBody();
                assertThat(body).isNotNull();

                // Parse and verify our specific documents exist
                // Use simple existence checks to avoid test pollution issues
                var bodyStr = new String(body);
                assertThat(bodyStr).contains(doc1Title);
                assertThat(bodyStr).contains(doc2Title);
                assertThat(bodyStr).contains(uniqueId);
            });
    }

    // ==================== Version Management ====================

    @Test
    @WithMentorUser
    void shouldGetAllDocumentVersionsSuccessfully() {
        // Arrange - Create document with multiple versions
        UUID documentId = createTestDocument("Version 1", "Content 1");
        updateTestDocument(documentId, "Version 2", "Content 2");

        // Act & Assert - Use proper DTO types for paginated response
        webTestClient
            .get()
            .uri("/api/documents/{id}/versions?page=0&size=10", documentId)
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .consumeWith(result -> {
                var body = result.getResponseBody();
                assertThat(body).isNotNull();

                // Verify the response contains our version data
                var bodyStr = new String(body);
                assertThat(bodyStr).contains("Version 1");
                assertThat(bodyStr).contains("Version 2");
                assertThat(bodyStr).contains("Content 1");
                assertThat(bodyStr).contains("Content 2");
                assertThat(bodyStr).contains("\"totalElements\":2");
            });
    }

    @Test
    @WithMentorUser
    void shouldGetSpecificDocumentVersionSuccessfully() {
        // Arrange - Create document with multiple versions
        UUID documentId = createTestDocument("Version 1", "Content 1");
        var firstVersion = documentRepository.findFirstByIdAndUserOrderByCreatedAtDesc(documentId, testUser).get();

        updateTestDocument(documentId, "Version 2", "Content 2");

        // Act & Assert - Get specific version by timestamp
        webTestClient
            .get()
            .uri("/api/documents/{id}/versions/{timestamp}", documentId, firstVersion.getCreatedAt())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(DocumentDTO.class)
            .value(response -> {
                assertThat(response.title()).isEqualTo("Version 1");
                assertThat(response.content()).isEqualTo("Content 1");
                assertThat(response.createdAt()).isEqualTo(firstVersion.getCreatedAt());
            });
    }

    @Test
    @WithMentorUser
    void shouldDeleteVersionsAfterTimestampSuccessfully() {
        // Arrange - Create multiple versions
        UUID documentId = createTestDocument("Version 1", "Content 1");
        var firstVersionTime = documentRepository
            .findFirstByIdAndUserOrderByCreatedAtDesc(documentId, testUser)
            .get()
            .getCreatedAt();

        // Wait and create more versions
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {}
        updateTestDocument(documentId, "Version 2", "Content 2");
        updateTestDocument(documentId, "Version 3", "Content 3");

        // Act & Assert
        webTestClient
            .delete()
            .uri("/api/documents/{id}/versions?after={timestamp}", documentId, firstVersionTime.plusMillis(1))
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBodyList(DocumentDTO.class)
            .value(deletedVersions -> {
                assertThat(deletedVersions).hasSize(2); // Deleted v2 and v3
                assertThat(deletedVersions)
                    .extracting(DocumentDTO::title)
                    .containsExactlyInAnyOrder("Version 2", "Version 3");
            });

        // Assert - Only first version should remain
        var remainingVersions = documentRepository.findByIdAndUserOrderByCreatedAtDesc(documentId, testUser);
        assertThat(remainingVersions).hasSize(1);
        assertThat(remainingVersions.get(0).getTitle()).isEqualTo("Version 1");
    }

    // ==================== Error Handling ====================

    @Test
    @WithMentorUser
    void shouldReturn404ForNonExistentDocument() {
        // Act & Assert
        webTestClient
            .get()
            .uri("/api/documents/{id}", UUID.randomUUID())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    @WithMentorUser
    void shouldReturn404WhenUpdatingNonExistentDocument() {
        // Arrange
        var updateRequest = new UpdateDocumentRequestDTO("Updated Title", "Updated content", DocumentKind.TEXT);

        // Act & Assert
        webTestClient
            .put()
            .uri("/api/documents/{id}", UUID.randomUUID())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    @WithMentorUser
    void shouldReturn400ForInvalidDocumentData() {
        // Arrange - Invalid request (missing title)
        var invalidRequest = new CreateDocumentRequestDTO(
            null, // Invalid: null title
            "Content",
            DocumentKind.TEXT
        );

        // Act & Assert
        webTestClient
            .post()
            .uri("/api/documents")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidRequest)
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    // ==================== Pagination Tests ====================

    @Test
    @WithMentorUser
    void shouldSupportPaginationForUserDocuments() {
        // Arrange - Create documents with unique prefix to avoid conflicts
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        List<String> docTitles = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            String title = "PaginationDoc" + i + "_" + uniqueId;
            docTitles.add(title);
            createTestDocument(title, "Content " + i);
        }

        // Act & Assert - Test pagination structure without relying on absolute counts
        webTestClient
            .get()
            .uri("/api/documents?page=0&size=2")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .consumeWith(result -> {
                var body = result.getResponseBody();
                assertThat(body).isNotNull();

                var bodyStr = new String(body);
                // Verify pagination structure exists
                assertThat(bodyStr).contains("\"content\":");
                assertThat(bodyStr).contains("\"totalPages\":");
                assertThat(bodyStr).contains("\"totalElements\":");
                assertThat(bodyStr).contains("\"numberOfElements\":");
                assertThat(bodyStr).contains("\"first\":true");

                // Verify our test documents are present somewhere in the paginated results
                assertThat(bodyStr).contains(uniqueId);
            });
    }

    @Test
    @WithMentorUser
    void shouldSupportPaginationForDocumentVersions() {
        // Arrange - Create document with multiple versions
        UUID documentId = createTestDocument("Version 1", "Content 1");
        for (int i = 2; i <= 5; i++) {
            updateTestDocument(documentId, "Version " + i, "Content " + i);
        }

        // Act & Assert
        webTestClient
            .get()
            .uri("/api/documents/{id}/versions?page=0&size=2", documentId)
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.content")
            .isArray()
            .jsonPath("$.content.length()")
            .isEqualTo(2)
            .jsonPath("$.totalElements")
            .isEqualTo(5);
    }

    // ==================== Helper Methods ====================

    private UUID createTestDocument(String title, String content) {
        var request = new CreateDocumentRequestDTO(title, content, DocumentKind.TEXT);

        return webTestClient
            .post()
            .uri("/api/documents")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(DocumentDTO.class)
            .returnResult()
            .getResponseBody()
            .id();
    }

    private void updateTestDocument(UUID documentId, String title, String content) {
        var updateRequest = new UpdateDocumentRequestDTO(title, content, DocumentKind.TEXT);

        webTestClient
            .put()
            .uri("/api/documents/{id}", documentId)
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus()
            .isOk();
    }
}
