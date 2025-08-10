package de.tum.in.www1.hephaestus.mentor.document;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Document management with versioning support")
public class DocumentController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentService documentService;
    private final UserRepository userRepository;

    public DocumentController(DocumentService documentService, UserRepository userRepository) {
        this.documentService = documentService;
        this.userRepository = userRepository;
    }

    @PostMapping
    @Operation(summary = "Create a new document")
    @ApiResponse(responseCode = "201", description = "Document created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request data")
    public ResponseEntity<DocumentDTO> createDocument(@Valid @RequestBody CreateDocumentRequestDTO request) {
        logger.info("Creating new document: {}", request.title());

        User user = userRepository.getCurrentUserElseThrow();
        DocumentDTO response = documentService.createDocument(request, user);

        return ResponseEntity.status(HttpStatus.CREATED).body(response); // 201 for creation
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get latest version of a document")
    @ApiResponse(responseCode = "200", description = "Document retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<DocumentDTO> getDocument(@PathVariable UUID id) {
        logger.debug("Fetching document: {}", id);

        User user = userRepository.getCurrentUserElseThrow();
        try {
            DocumentDTO response = documentService.getLatestDocument(id, user);
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException e) {
            logger.debug("Document not found: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a document (creates new version)")
    @ApiResponse(responseCode = "200", description = "Document updated successfully")
    @ApiResponse(responseCode = "404", description = "Document not found")
    @ApiResponse(responseCode = "400", description = "Invalid request data")
    public ResponseEntity<DocumentDTO> updateDocument(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateDocumentRequestDTO request
    ) {
        logger.info("Updating document: {}", id);

        User user = userRepository.getCurrentUserElseThrow();
        try {
            DocumentDTO response = documentService.updateDocument(id, request, user);
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException e) {
            logger.debug("Document not found for update: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document and all its versions")
    @ApiResponse(responseCode = "204", description = "Document deleted successfully")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
        logger.info("Deleting document: {}", id);

        User user = userRepository.getCurrentUserElseThrow();
        try {
            documentService.deleteDocument(id, user);
            return ResponseEntity.noContent().build(); // 204 for successful deletion
        } catch (EntityNotFoundException e) {
            logger.debug("Document not found for deletion: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    @Operation(summary = "Get all user documents (latest versions only)")
    @ApiResponse(responseCode = "200", description = "Documents retrieved successfully")
    public ResponseEntity<Page<DocumentSummaryDTO>> getUserDocuments(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDir
    ) {
        logger.debug("Fetching user documents - page: {}, size: {}", page, size);

        User user = userRepository.getCurrentUserElseThrow();

        // Create sort
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<DocumentSummaryDTO> documents = documentService.getDocumentsByUser(user, pageable);

        return ResponseEntity.ok(documents);
    }

    @GetMapping("/{id}/versions")
    @Operation(summary = "Get all versions of a document")
    @ApiResponse(responseCode = "200", description = "Document versions retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<Page<DocumentDTO>> getDocumentVersions(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        logger.debug("Fetching versions for document: {}", id);

        User user = userRepository.getCurrentUserElseThrow();
        try {
            // Always sort versions by createdAt DESC (latest first)
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<DocumentDTO> versions = documentService.getDocumentVersions(id, user, pageable);
            return ResponseEntity.ok(versions);
        } catch (EntityNotFoundException e) {
            logger.debug("Document not found: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/versions/{timestamp}")
    @Operation(summary = "Get specific version of a document by timestamp")
    @ApiResponse(responseCode = "200", description = "Document version retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Document version not found")
    public ResponseEntity<DocumentDTO> getDocumentVersion(@PathVariable UUID id, @PathVariable Instant timestamp) {
        logger.debug("Fetching document version: {} at {}", id, timestamp);

        User user = userRepository.getCurrentUserElseThrow();
        try {
            DocumentDTO response = documentService.getDocumentVersion(id, timestamp, user);
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException e) {
            logger.debug("Document version not found: {} at {}", id, timestamp);
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}/versions")
    @Operation(summary = "Delete document versions after specified timestamp")
    @ApiResponse(responseCode = "200", description = "Document versions deleted successfully")
    @ApiResponse(responseCode = "404", description = "Document not found")
    @ApiResponse(responseCode = "400", description = "Invalid timestamp parameter")
    public ResponseEntity<List<DocumentDTO>> deleteVersionsAfterTimestamp(
        @PathVariable UUID id,
        @RequestParam Instant after
    ) {
        logger.info("Deleting versions of document {} after timestamp: {}", id, after);

        User user = userRepository.getCurrentUserElseThrow();
        try {
            List<DocumentDTO> deletedVersions = documentService.deleteDocumentsAfterTimestamp(id, after, user);
            return ResponseEntity.ok(deletedVersions);
        } catch (EntityNotFoundException e) {
            logger.debug("Document not found: {}", id);
            return ResponseEntity.notFound().build();
        }
    }
}
