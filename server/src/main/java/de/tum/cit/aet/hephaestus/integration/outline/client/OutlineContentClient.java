package de.tum.cit.aet.hephaestus.integration.outline.client;

import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlineCollectionModel;
import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlineDocumentModel;
import de.tum.cit.aet.hephaestus.integration.outline.client.model.OutlineNavigationNode;
import java.util.List;
import java.util.Optional;

public interface OutlineContentClient {
    List<OutlineCollectionModel> listCollections(String serverUrl, String token);
    List<OutlineCollectionModel> listCollections(String serverUrl, String token, int maxPages);
    List<OutlineNavigationNode> listCollectionDocuments(String serverUrl, String token, String collectionId);
    List<OutlineDocumentModel> listDocuments(String serverUrl, String token, String collectionId);
    Optional<OutlineDocumentModel> getDocumentInfo(String serverUrl, String token, String documentId);
    List<OutlineDocumentModel> listArchivedDocuments(String serverUrl, String token, String collectionId);
    String exportDocument(String serverUrl, String token, String documentId);
}
