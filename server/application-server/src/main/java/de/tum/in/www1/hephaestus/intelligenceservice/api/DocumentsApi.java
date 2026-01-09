package de.tum.in.www1.hephaestus.intelligenceservice.api;

import de.tum.in.www1.hephaestus.intelligenceservice.ApiClient;
import de.tum.in.www1.hephaestus.intelligenceservice.BaseApi;
import de.tum.in.www1.hephaestus.intelligenceservice.model.CreateDocumentRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.Document;
import de.tum.in.www1.hephaestus.intelligenceservice.model.DocumentSummary;
import jakarta.annotation.Generated;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.time.OffsetDateTime;
import java.util.*;

@Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", comments = "Generator version: 7.7.0")
@SuppressWarnings("all")
public class DocumentsApi extends BaseApi {

    public DocumentsApi() {
        super(new ApiClient());
    }

    public DocumentsApi(ApiClient apiClient) {
        super(apiClient);
    }

    /**
     * Create a new document
     *
     * <p><b>201</b> - Created document
     * <p><b>400</b> - Missing required context
     * <p><b>500</b> - Internal error
     *
     * @param createDocumentRequest Create document (required)
     * @return Document
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public Document createDocument(CreateDocumentRequest createDocumentRequest) throws RestClientException {
        return createDocumentWithHttpInfo(createDocumentRequest).getBody();
    }

    /**
     * Create a new document
     *
     * <p><b>201</b> - Created document
     * <p><b>400</b> - Missing required context
     * <p><b>500</b> - Internal error
     *
     * @param createDocumentRequest Create document (required)
     * @return ResponseEntity&lt;Document&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public ResponseEntity<Document> createDocumentWithHttpInfo(CreateDocumentRequest createDocumentRequest) throws RestClientException {
        Object localVarPostBody = createDocumentRequest;

        // verify the required parameter 'createDocumentRequest' is set
        if (createDocumentRequest == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'createDocumentRequest' when calling createDocument");
        }


        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders localVarHeaderParams = new HttpHeaders();
        final MultiValueMap<String, String> localVarCookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> localVarFormParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[]{};

        ParameterizedTypeReference<Document> localReturnType = new ParameterizedTypeReference<Document>() {
        };
        return apiClient.invokeAPI("/mentor/documents", HttpMethod.POST, Collections.<String, Object>emptyMap(), localVarQueryParams, localVarPostBody, localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAccept, localVarContentType, localVarAuthNames, localReturnType);
    }

    /**
     * Delete a document and all versions
     *
     * <p><b>204</b> - Deleted
     * <p><b>400</b> - Missing context
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     *
     * @param id (required)
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public void deleteDocument(UUID id) throws RestClientException {
        deleteDocumentWithHttpInfo(id);
    }

    /**
     * Delete a document and all versions
     *
     * <p><b>204</b> - Deleted
     * <p><b>400</b> - Missing context
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     *
     * @param id (required)
     * @return ResponseEntity&lt;Void&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public ResponseEntity<Void> deleteDocumentWithHttpInfo(UUID id) throws RestClientException {
        Object localVarPostBody = null;

        // verify the required parameter 'id' is set
        if (id == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'id' when calling deleteDocument");
        }

        // create path and map variables
        final Map<String, Object> uriVariables = new HashMap<String, Object>();
        uriVariables.put("id", id);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders localVarHeaderParams = new HttpHeaders();
        final MultiValueMap<String, String> localVarCookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> localVarFormParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = {};
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[]{};

        ParameterizedTypeReference<Void> localReturnType = new ParameterizedTypeReference<Void>() {
        };
        return apiClient.invokeAPI("/mentor/documents/{id}", HttpMethod.DELETE, uriVariables, localVarQueryParams, localVarPostBody, localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAccept, localVarContentType, localVarAuthNames, localReturnType);
    }

    /**
     * Delete versions after timestamp
     *
     * <p><b>200</b> - Deleted versions
     * <p><b>400</b> - Missing context
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     *
     * @param id    (required)
     * @param after (required)
     * @return List&lt;Document&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public List<Document> deleteDocumentVersionsAfter(UUID id, OffsetDateTime after) throws RestClientException {
        return deleteDocumentVersionsAfterWithHttpInfo(id, after).getBody();
    }

    /**
     * Delete versions after timestamp
     *
     * <p><b>200</b> - Deleted versions
     * <p><b>400</b> - Missing context
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     *
     * @param id    (required)
     * @param after (required)
     * @return ResponseEntity&lt;List&lt;Document&gt;&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public ResponseEntity<List<Document>> deleteDocumentVersionsAfterWithHttpInfo(UUID id, OffsetDateTime after) throws RestClientException {
        Object localVarPostBody = null;

        // verify the required parameter 'id' is set
        if (id == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'id' when calling deleteDocumentVersionsAfter");
        }

        // verify the required parameter 'after' is set
        if (after == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'after' when calling deleteDocumentVersionsAfter");
        }

        // create path and map variables
        final Map<String, Object> uriVariables = new HashMap<String, Object>();
        uriVariables.put("id", id);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders localVarHeaderParams = new HttpHeaders();
        final MultiValueMap<String, String> localVarCookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> localVarFormParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "after", after));


        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = {};
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[]{};

        ParameterizedTypeReference<List<Document>> localReturnType = new ParameterizedTypeReference<List<Document>>() {
        };
        return apiClient.invokeAPI("/mentor/documents/{id}/versions", HttpMethod.DELETE, uriVariables, localVarQueryParams, localVarPostBody, localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAccept, localVarContentType, localVarAuthNames, localReturnType);
    }

    /**
     * Get latest version of a document
     *
     * <p><b>200</b> - Document
     * <p><b>400</b> - Missing context
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     *
     * @param id (required)
     * @return Document
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public Document getDocument(UUID id) throws RestClientException {
        return getDocumentWithHttpInfo(id).getBody();
    }

    /**
     * Get latest version of a document
     *
     * <p><b>200</b> - Document
     * <p><b>400</b> - Missing context
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     *
     * @param id (required)
     * @return ResponseEntity&lt;Document&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public ResponseEntity<Document> getDocumentWithHttpInfo(UUID id) throws RestClientException {
        Object localVarPostBody = null;

        // verify the required parameter 'id' is set
        if (id == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'id' when calling getDocument");
        }

        // create path and map variables
        final Map<String, Object> uriVariables = new HashMap<String, Object>();
        uriVariables.put("id", id);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders localVarHeaderParams = new HttpHeaders();
        final MultiValueMap<String, String> localVarCookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> localVarFormParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = {};
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[]{};

        ParameterizedTypeReference<Document> localReturnType = new ParameterizedTypeReference<Document>() {
        };
        return apiClient.invokeAPI("/mentor/documents/{id}", HttpMethod.GET, uriVariables, localVarQueryParams, localVarPostBody, localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAccept, localVarContentType, localVarAuthNames, localReturnType);
    }

    /**
     * Get specific version
     *
     * <p><b>200</b> - Document
     * <p><b>400</b> - Missing context
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     *
     * @param id            (required)
     * @param versionNumber (required)
     * @return Document
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public Document getVersion(UUID id, Integer versionNumber) throws RestClientException {
        return getVersionWithHttpInfo(id, versionNumber).getBody();
    }

    /**
     * Get specific version
     *
     * <p><b>200</b> - Document
     * <p><b>400</b> - Missing context
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     *
     * @param id            (required)
     * @param versionNumber (required)
     * @return ResponseEntity&lt;Document&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public ResponseEntity<Document> getVersionWithHttpInfo(UUID id, Integer versionNumber) throws RestClientException {
        Object localVarPostBody = null;

        // verify the required parameter 'id' is set
        if (id == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'id' when calling getVersion");
        }

        // verify the required parameter 'versionNumber' is set
        if (versionNumber == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'versionNumber' when calling getVersion");
        }

        // create path and map variables
        final Map<String, Object> uriVariables = new HashMap<String, Object>();
        uriVariables.put("id", id);
        uriVariables.put("versionNumber", versionNumber);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders localVarHeaderParams = new HttpHeaders();
        final MultiValueMap<String, String> localVarCookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> localVarFormParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = {};
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[]{};

        ParameterizedTypeReference<Document> localReturnType = new ParameterizedTypeReference<Document>() {
        };
        return apiClient.invokeAPI("/mentor/documents/{id}/versions/{versionNumber}", HttpMethod.GET, uriVariables, localVarQueryParams, localVarPostBody, localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAccept, localVarContentType, localVarAuthNames, localReturnType);
    }

    /**
     * List documents owned by the authenticated user
     *
     * <p><b>200</b> - Document summaries
     * <p><b>400</b> - Missing context
     * <p><b>500</b> - Internal error
     *
     * @param page (optional, default to 0)
     * @param size (optional, default to 20)
     * @return List&lt;DocumentSummary&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public List<DocumentSummary> listDocuments(Integer page, Integer size) throws RestClientException {
        return listDocumentsWithHttpInfo(page, size).getBody();
    }

    /**
     * List documents owned by the authenticated user
     *
     * <p><b>200</b> - Document summaries
     * <p><b>400</b> - Missing context
     * <p><b>500</b> - Internal error
     *
     * @param page (optional, default to 0)
     * @param size (optional, default to 20)
     * @return ResponseEntity&lt;List&lt;DocumentSummary&gt;&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public ResponseEntity<List<DocumentSummary>> listDocumentsWithHttpInfo(Integer page, Integer size) throws RestClientException {
        Object localVarPostBody = null;


        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders localVarHeaderParams = new HttpHeaders();
        final MultiValueMap<String, String> localVarCookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> localVarFormParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "page", page));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "size", size));


        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = {};
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[]{};

        ParameterizedTypeReference<List<DocumentSummary>> localReturnType = new ParameterizedTypeReference<List<DocumentSummary>>() {
        };
        return apiClient.invokeAPI("/mentor/documents", HttpMethod.GET, Collections.<String, Object>emptyMap(), localVarQueryParams, localVarPostBody, localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAccept, localVarContentType, localVarAuthNames, localReturnType);
    }

    /**
     * List versions of a document
     *
     * <p><b>200</b> - Document versions
     * <p><b>400</b> - Missing context
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     *
     * @param id   (required)
     * @param page (optional, default to 0)
     * @param size (optional, default to 20)
     * @return List&lt;Document&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public List<Document> listVersions(UUID id, Integer page, Integer size) throws RestClientException {
        return listVersionsWithHttpInfo(id, page, size).getBody();
    }

    /**
     * List versions of a document
     *
     * <p><b>200</b> - Document versions
     * <p><b>400</b> - Missing context
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     *
     * @param id   (required)
     * @param page (optional, default to 0)
     * @param size (optional, default to 20)
     * @return ResponseEntity&lt;List&lt;Document&gt;&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public ResponseEntity<List<Document>> listVersionsWithHttpInfo(UUID id, Integer page, Integer size) throws RestClientException {
        Object localVarPostBody = null;

        // verify the required parameter 'id' is set
        if (id == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'id' when calling listVersions");
        }

        // create path and map variables
        final Map<String, Object> uriVariables = new HashMap<String, Object>();
        uriVariables.put("id", id);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders localVarHeaderParams = new HttpHeaders();
        final MultiValueMap<String, String> localVarCookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> localVarFormParams = new LinkedMultiValueMap<String, Object>();

        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "page", page));
        localVarQueryParams.putAll(apiClient.parameterToMultiValueMap(null, "size", size));


        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = {};
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[]{};

        ParameterizedTypeReference<List<Document>> localReturnType = new ParameterizedTypeReference<List<Document>>() {
        };
        return apiClient.invokeAPI("/mentor/documents/{id}/versions", HttpMethod.GET, uriVariables, localVarQueryParams, localVarPostBody, localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAccept, localVarContentType, localVarAuthNames, localReturnType);
    }

    /**
     * Update a document (creates new version)
     *
     * <p><b>200</b> - Updated document
     * <p><b>400</b> - Missing context
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     *
     * @param id                    (required)
     * @param createDocumentRequest Update document (required)
     * @return Document
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public Document updateDocument(UUID id, CreateDocumentRequest createDocumentRequest) throws RestClientException {
        return updateDocumentWithHttpInfo(id, createDocumentRequest).getBody();
    }

    /**
     * Update a document (creates new version)
     *
     * <p><b>200</b> - Updated document
     * <p><b>400</b> - Missing context
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     *
     * @param id                    (required)
     * @param createDocumentRequest Update document (required)
     * @return ResponseEntity&lt;Document&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public ResponseEntity<Document> updateDocumentWithHttpInfo(UUID id, CreateDocumentRequest createDocumentRequest) throws RestClientException {
        Object localVarPostBody = createDocumentRequest;

        // verify the required parameter 'id' is set
        if (id == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'id' when calling updateDocument");
        }

        // verify the required parameter 'createDocumentRequest' is set
        if (createDocumentRequest == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'createDocumentRequest' when calling updateDocument");
        }

        // create path and map variables
        final Map<String, Object> uriVariables = new HashMap<String, Object>();
        uriVariables.put("id", id);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders localVarHeaderParams = new HttpHeaders();
        final MultiValueMap<String, String> localVarCookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> localVarFormParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[]{};

        ParameterizedTypeReference<Document> localReturnType = new ParameterizedTypeReference<Document>() {
        };
        return apiClient.invokeAPI("/mentor/documents/{id}", HttpMethod.PUT, uriVariables, localVarQueryParams, localVarPostBody, localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAccept, localVarContentType, localVarAuthNames, localReturnType);
    }

    @Override
    public <T> ResponseEntity<T> invokeAPI(String url, HttpMethod method, Object request, ParameterizedTypeReference<T> returnType) throws RestClientException {
        String localVarPath = url.replace(apiClient.getBasePath(), "");
        Object localVarPostBody = request;

        final Map<String, Object> uriVariables = new HashMap<String, Object>();
        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders localVarHeaderParams = new HttpHeaders();
        final MultiValueMap<String, String> localVarCookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> localVarFormParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = {
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = {
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[]{};

        return apiClient.invokeAPI(localVarPath, method, uriVariables, localVarQueryParams, localVarPostBody, localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAccept, localVarContentType, localVarAuthNames, returnType);
    }
}
