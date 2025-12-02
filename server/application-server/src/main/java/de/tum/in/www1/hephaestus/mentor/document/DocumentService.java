package de.tum.in.www1.hephaestus.mentor.document;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing documents with versioning.
 */
@Service
public class DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    @Autowired
    private DocumentRepository documentRepository;

    /**
     * Create a new document
     */
    @Transactional
    public DocumentDTO createDocument(CreateDocumentRequestDTO request, User user, Workspace workspace) {
        logger.info(
            "Creating new document: {} for user: {} in workspace {}",
            request.title(),
            user.getId(),
            workspace.getId()
        );

        // Generate new UUID for document
        UUID documentId = UUID.randomUUID();

        // First version is 1
        Document document = new Document(
            documentId,
            1,
            request.title(),
            request.content(),
            request.kind(),
            user,
            workspace
        );

        Document savedDocument = documentRepository.save(document);
        logger.info("Document created successfully with ID: {}", savedDocument.getId());

        return DocumentDTO.from(savedDocument);
    }

    /**
     * Update document (creates new version)
     */
    @Transactional
    public DocumentDTO updateDocument(UUID id, UpdateDocumentRequestDTO request, User user, Workspace workspace) {
        logger.info("Updating document: {} for user: {} in workspace {}", id, user.getId(), workspace.getId());

        // Verify document exists and belongs to user
        if (!documentRepository.existsByWorkspaceAndIdAndUser(workspace, id, user)) {
            throw new EntityNotFoundException("Document", id.toString());
        }

        // Determine next version number
        int nextVersion = documentRepository
            .findFirstByWorkspaceAndUserAndIdOrderByVersionNumberDesc(workspace, user, id)
            .map(d -> d.getVersionNumber() + 1)
            .orElse(1);

        // Create new version
        Document document = new Document(
            id,
            nextVersion,
            request.title(),
            request.content(),
            request.kind(),
            user,
            workspace
        );

        Document savedDocument = documentRepository.save(document);
        logger.info("Document updated successfully: {}", id);

        return DocumentDTO.from(savedDocument);
    }

    /**
     * Get all versions of a document by id, ordered by createdAt desc (latest first)
     */
    @Transactional(readOnly = true)
    public List<DocumentDTO> getDocumentsById(UUID id, User user, Workspace workspace) {
        logger.debug("Getting all versions of document ID: {} for user: {}", id, user.getId());

        List<Document> documents = documentRepository.findByWorkspaceAndUserAndIdOrderByVersionNumberDesc(
            workspace,
            id,
            user
        );

        return documents.stream().map(DocumentDTO::from).toList();
    }

    /**
     * Get latest version of a document
     */
    @Transactional(readOnly = true)
    public DocumentDTO getLatestDocument(UUID id, User user, Workspace workspace) {
        logger.debug("Getting latest document ID: {} for user: {}", id, user.getId());

        return documentRepository
            .findFirstByWorkspaceAndUserAndIdOrderByVersionNumberDesc(workspace, user, id)
            .map(DocumentDTO::from)
            .orElseThrow(() -> new EntityNotFoundException("Document", id.toString()));
    }

    /**
     * Delete document versions after specified timestamp
     */
    @Transactional
    public List<DocumentDTO> deleteDocumentsAfterTimestamp(UUID id, Instant timestamp, User user, Workspace workspace) {
        logger.info(
            "Deleting document versions for ID: {} after timestamp: {} for user: {}",
            id,
            timestamp,
            user.getId()
        );

        // Verify user has access to the document
        if (!documentRepository.existsByWorkspaceAndIdAndUser(workspace, id, user)) {
            throw new EntityNotFoundException("Document", id.toString());
        }

        // Determine the smallest version strictly after the timestamp.
        // Then delete all versions with versionNumber >= that minimum (i.e., greater than min-1).
        var versionsDesc = documentRepository.findByWorkspaceAndUserAndIdOrderByVersionNumberDesc(workspace, id, user);
        int minVersionAfter = versionsDesc
            .stream()
            .filter(d -> d.getCreatedAt().isAfter(timestamp))
            .mapToInt(Document::getVersionNumber)
            .min()
            .orElse(Integer.MAX_VALUE);

        List<Document> deletedDocuments = minVersionAfter == Integer.MAX_VALUE
            ? List.of()
            : documentRepository.findByWorkspaceAndUserAndIdAndVersionNumberGreaterThanOrderByVersionNumberDesc(
                workspace,
                id,
                user,
                minVersionAfter - 1
            );

        if (minVersionAfter != Integer.MAX_VALUE) {
            documentRepository.deleteByWorkspaceAndUserAndIdAndVersionNumberGreaterThan(
                workspace,
                id,
                user,
                minVersionAfter - 1
            );
        }

        return deletedDocuments.stream().map(DocumentDTO::from).toList();
    }

    /**
     * Get documents by user (all documents, latest version only)
     */
    @Transactional(readOnly = true)
    public List<DocumentSummaryDTO> getDocumentsByUser(User user, Workspace workspace) {
        logger.debug("Getting all documents for user: {} in workspace {}", user.getId(), workspace.getId());

        // Get latest version of each document
        List<Document> documents = documentRepository.findLatestVersionsByUser(workspace, user);

        return documents
            .stream()
            .map(DocumentSummaryDTO::from) // Use summary DTO for lists
            .toList();
    }

    /**
     * Delete document and all its versions
     */
    @Transactional
    public void deleteDocument(UUID id, User user, Workspace workspace) {
        logger.info("Deleting document: {} for user: {} in workspace {}", id, user.getId(), workspace.getId());

        List<Document> documents = documentRepository.findByWorkspaceAndUserAndId(workspace, user, id);
        if (documents.isEmpty()) {
            throw new EntityNotFoundException("Document", id.toString());
        }

        documentRepository.deleteAll(documents);
        logger.info("Deleted {} versions of document: {}", documents.size(), id);
    }

    /**
     * Get document versions with pagination
     */
    public Page<DocumentDTO> getDocumentVersions(UUID id, User user, Workspace workspace, Pageable pageable) {
        logger.info("Getting versions for document: {} for user: {}", id, user.getId());

        Page<Document> documents = documentRepository.findByWorkspaceAndUserAndId(workspace, user, id, pageable);
        if (documents.isEmpty()) {
            throw new EntityNotFoundException("Document", id.toString());
        }

        return documents.map(this::mapToResponseDTO);
    }

    /**
     * Get specific document version by timestamp
     */
    public DocumentDTO getDocumentVersion(UUID id, Integer versionNumber, User user, Workspace workspace) {
        logger.info("Getting document version: {} #{} for user: {}", id, versionNumber, user.getId());

        Document document = documentRepository
            .findByWorkspaceAndUserAndIdAndVersionNumber(workspace, user, id, versionNumber)
            .orElseThrow(() -> new EntityNotFoundException("Document version", id + " #" + versionNumber));

        return mapToResponseDTO(document);
    }

    /**
     * Get documents by user with pagination (summary view)
     */
    public Page<DocumentSummaryDTO> getDocumentsByUser(User user, Workspace workspace, Pageable pageable) {
        logger.debug("Getting documents for user: {} with pagination", user.getId());

        Page<Document> latestDocuments = documentRepository.findLatestVersionsByUser(workspace, user, pageable);
        return latestDocuments.map(DocumentSummaryDTO::from); // Use summary DTO for lists
    }

    private DocumentDTO mapToResponseDTO(Document document) {
        return DocumentDTO.from(document);
    }
}
