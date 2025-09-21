package de.tum.in.www1.hephaestus.intelligenceservice.api;

import de.tum.in.www1.hephaestus.intelligenceservice.ApiClient;

import de.tum.in.www1.hephaestus.intelligenceservice.model.CreateDocumentRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.Document;
import de.tum.in.www1.hephaestus.intelligenceservice.model.DocumentSummary;
import de.tum.in.www1.hephaestus.intelligenceservice.model.MentorThreadsThreadIdGet404Response;
import java.time.OffsetDateTime;
import java.util.UUID;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", comments = "Generator version: 7.7.0")
public class DocumentsApi {
    private ApiClient apiClient;

    public DocumentsApi() {
        this(new ApiClient());
    }

    @Autowired
    public DocumentsApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * List latest version of documents (no auth; all users)
     * 
     * <p><b>200</b> - Document summaries
     * <p><b>500</b> - Internal error
     * @param page The page parameter
     * @param size The size parameter
     * @return List&lt;DocumentSummary&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mentorDocumentsGetRequestCreation(Integer page, Integer size) throws WebClientResponseException {
        Object postBody = null;
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "page", page));
        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "size", size));
        
        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<DocumentSummary> localVarReturnType = new ParameterizedTypeReference<DocumentSummary>() {};
        return apiClient.invokeAPI("/mentor/documents", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List latest version of documents (no auth; all users)
     * 
     * <p><b>200</b> - Document summaries
     * <p><b>500</b> - Internal error
     * @param page The page parameter
     * @param size The size parameter
     * @return List&lt;DocumentSummary&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<DocumentSummary> mentorDocumentsGet(Integer page, Integer size) throws WebClientResponseException {
        ParameterizedTypeReference<DocumentSummary> localVarReturnType = new ParameterizedTypeReference<DocumentSummary>() {};
        return mentorDocumentsGetRequestCreation(page, size).bodyToFlux(localVarReturnType);
    }

    /**
     * List latest version of documents (no auth; all users)
     * 
     * <p><b>200</b> - Document summaries
     * <p><b>500</b> - Internal error
     * @param page The page parameter
     * @param size The size parameter
     * @return ResponseEntity&lt;List&lt;DocumentSummary&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<DocumentSummary>>> mentorDocumentsGetWithHttpInfo(Integer page, Integer size) throws WebClientResponseException {
        ParameterizedTypeReference<DocumentSummary> localVarReturnType = new ParameterizedTypeReference<DocumentSummary>() {};
        return mentorDocumentsGetRequestCreation(page, size).toEntityList(localVarReturnType);
    }

    /**
     * List latest version of documents (no auth; all users)
     * 
     * <p><b>200</b> - Document summaries
     * <p><b>500</b> - Internal error
     * @param page The page parameter
     * @param size The size parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mentorDocumentsGetWithResponseSpec(Integer page, Integer size) throws WebClientResponseException {
        return mentorDocumentsGetRequestCreation(page, size);
    }
    /**
     * Delete a document and all versions
     * 
     * <p><b>204</b> - Deleted
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mentorDocumentsIdDeleteRequestCreation(UUID id) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'id' is set
        if (id == null) {
            throw new WebClientResponseException("Missing the required parameter 'id' when calling mentorDocumentsIdDelete", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("id", id);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/mentor/documents/{id}", HttpMethod.DELETE, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Delete a document and all versions
     * 
     * <p><b>204</b> - Deleted
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> mentorDocumentsIdDelete(UUID id) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return mentorDocumentsIdDeleteRequestCreation(id).bodyToMono(localVarReturnType);
    }

    /**
     * Delete a document and all versions
     * 
     * <p><b>204</b> - Deleted
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> mentorDocumentsIdDeleteWithHttpInfo(UUID id) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return mentorDocumentsIdDeleteRequestCreation(id).toEntity(localVarReturnType);
    }

    /**
     * Delete a document and all versions
     * 
     * <p><b>204</b> - Deleted
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mentorDocumentsIdDeleteWithResponseSpec(UUID id) throws WebClientResponseException {
        return mentorDocumentsIdDeleteRequestCreation(id);
    }
    /**
     * Get latest version of a document
     * 
     * <p><b>200</b> - Document
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @return Document
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mentorDocumentsIdGetRequestCreation(UUID id) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'id' is set
        if (id == null) {
            throw new WebClientResponseException("Missing the required parameter 'id' when calling mentorDocumentsIdGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("id", id);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Document> localVarReturnType = new ParameterizedTypeReference<Document>() {};
        return apiClient.invokeAPI("/mentor/documents/{id}", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get latest version of a document
     * 
     * <p><b>200</b> - Document
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @return Document
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Document> mentorDocumentsIdGet(UUID id) throws WebClientResponseException {
        ParameterizedTypeReference<Document> localVarReturnType = new ParameterizedTypeReference<Document>() {};
        return mentorDocumentsIdGetRequestCreation(id).bodyToMono(localVarReturnType);
    }

    /**
     * Get latest version of a document
     * 
     * <p><b>200</b> - Document
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @return ResponseEntity&lt;Document&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Document>> mentorDocumentsIdGetWithHttpInfo(UUID id) throws WebClientResponseException {
        ParameterizedTypeReference<Document> localVarReturnType = new ParameterizedTypeReference<Document>() {};
        return mentorDocumentsIdGetRequestCreation(id).toEntity(localVarReturnType);
    }

    /**
     * Get latest version of a document
     * 
     * <p><b>200</b> - Document
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mentorDocumentsIdGetWithResponseSpec(UUID id) throws WebClientResponseException {
        return mentorDocumentsIdGetRequestCreation(id);
    }
    /**
     * Update a document (creates new version)
     * 
     * <p><b>200</b> - Updated document
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @param createDocumentRequest Update document
     * @return Document
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mentorDocumentsIdPutRequestCreation(UUID id, CreateDocumentRequest createDocumentRequest) throws WebClientResponseException {
        Object postBody = createDocumentRequest;
        // verify the required parameter 'id' is set
        if (id == null) {
            throw new WebClientResponseException("Missing the required parameter 'id' when calling mentorDocumentsIdPut", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'createDocumentRequest' is set
        if (createDocumentRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'createDocumentRequest' when calling mentorDocumentsIdPut", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("id", id);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { 
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Document> localVarReturnType = new ParameterizedTypeReference<Document>() {};
        return apiClient.invokeAPI("/mentor/documents/{id}", HttpMethod.PUT, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Update a document (creates new version)
     * 
     * <p><b>200</b> - Updated document
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @param createDocumentRequest Update document
     * @return Document
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Document> mentorDocumentsIdPut(UUID id, CreateDocumentRequest createDocumentRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Document> localVarReturnType = new ParameterizedTypeReference<Document>() {};
        return mentorDocumentsIdPutRequestCreation(id, createDocumentRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Update a document (creates new version)
     * 
     * <p><b>200</b> - Updated document
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @param createDocumentRequest Update document
     * @return ResponseEntity&lt;Document&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Document>> mentorDocumentsIdPutWithHttpInfo(UUID id, CreateDocumentRequest createDocumentRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Document> localVarReturnType = new ParameterizedTypeReference<Document>() {};
        return mentorDocumentsIdPutRequestCreation(id, createDocumentRequest).toEntity(localVarReturnType);
    }

    /**
     * Update a document (creates new version)
     * 
     * <p><b>200</b> - Updated document
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @param createDocumentRequest Update document
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mentorDocumentsIdPutWithResponseSpec(UUID id, CreateDocumentRequest createDocumentRequest) throws WebClientResponseException {
        return mentorDocumentsIdPutRequestCreation(id, createDocumentRequest);
    }
    /**
     * Delete versions after timestamp
     * 
     * <p><b>200</b> - Deleted versions
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @param after The after parameter
     * @return List&lt;Document&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mentorDocumentsIdVersionsDeleteRequestCreation(UUID id, OffsetDateTime after) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'id' is set
        if (id == null) {
            throw new WebClientResponseException("Missing the required parameter 'id' when calling mentorDocumentsIdVersionsDelete", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'after' is set
        if (after == null) {
            throw new WebClientResponseException("Missing the required parameter 'after' when calling mentorDocumentsIdVersionsDelete", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("id", id);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "after", after));
        
        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Document> localVarReturnType = new ParameterizedTypeReference<Document>() {};
        return apiClient.invokeAPI("/mentor/documents/{id}/versions", HttpMethod.DELETE, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Delete versions after timestamp
     * 
     * <p><b>200</b> - Deleted versions
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @param after The after parameter
     * @return List&lt;Document&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Document> mentorDocumentsIdVersionsDelete(UUID id, OffsetDateTime after) throws WebClientResponseException {
        ParameterizedTypeReference<Document> localVarReturnType = new ParameterizedTypeReference<Document>() {};
        return mentorDocumentsIdVersionsDeleteRequestCreation(id, after).bodyToFlux(localVarReturnType);
    }

    /**
     * Delete versions after timestamp
     * 
     * <p><b>200</b> - Deleted versions
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @param after The after parameter
     * @return ResponseEntity&lt;List&lt;Document&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Document>>> mentorDocumentsIdVersionsDeleteWithHttpInfo(UUID id, OffsetDateTime after) throws WebClientResponseException {
        ParameterizedTypeReference<Document> localVarReturnType = new ParameterizedTypeReference<Document>() {};
        return mentorDocumentsIdVersionsDeleteRequestCreation(id, after).toEntityList(localVarReturnType);
    }

    /**
     * Delete versions after timestamp
     * 
     * <p><b>200</b> - Deleted versions
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @param after The after parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mentorDocumentsIdVersionsDeleteWithResponseSpec(UUID id, OffsetDateTime after) throws WebClientResponseException {
        return mentorDocumentsIdVersionsDeleteRequestCreation(id, after);
    }
    /**
     * List versions of a document
     * 
     * <p><b>200</b> - Document versions
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @param page The page parameter
     * @param size The size parameter
     * @return List&lt;Document&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mentorDocumentsIdVersionsGetRequestCreation(UUID id, Integer page, Integer size) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'id' is set
        if (id == null) {
            throw new WebClientResponseException("Missing the required parameter 'id' when calling mentorDocumentsIdVersionsGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("id", id);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "page", page));
        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "size", size));
        
        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Document> localVarReturnType = new ParameterizedTypeReference<Document>() {};
        return apiClient.invokeAPI("/mentor/documents/{id}/versions", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List versions of a document
     * 
     * <p><b>200</b> - Document versions
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @param page The page parameter
     * @param size The size parameter
     * @return List&lt;Document&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Document> mentorDocumentsIdVersionsGet(UUID id, Integer page, Integer size) throws WebClientResponseException {
        ParameterizedTypeReference<Document> localVarReturnType = new ParameterizedTypeReference<Document>() {};
        return mentorDocumentsIdVersionsGetRequestCreation(id, page, size).bodyToFlux(localVarReturnType);
    }

    /**
     * List versions of a document
     * 
     * <p><b>200</b> - Document versions
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @param page The page parameter
     * @param size The size parameter
     * @return ResponseEntity&lt;List&lt;Document&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Document>>> mentorDocumentsIdVersionsGetWithHttpInfo(UUID id, Integer page, Integer size) throws WebClientResponseException {
        ParameterizedTypeReference<Document> localVarReturnType = new ParameterizedTypeReference<Document>() {};
        return mentorDocumentsIdVersionsGetRequestCreation(id, page, size).toEntityList(localVarReturnType);
    }

    /**
     * List versions of a document
     * 
     * <p><b>200</b> - Document versions
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @param page The page parameter
     * @param size The size parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mentorDocumentsIdVersionsGetWithResponseSpec(UUID id, Integer page, Integer size) throws WebClientResponseException {
        return mentorDocumentsIdVersionsGetRequestCreation(id, page, size);
    }
    /**
     * Get specific version
     * 
     * <p><b>200</b> - Document
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @param versionNumber The versionNumber parameter
     * @return Document
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mentorDocumentsIdVersionsVersionNumberGetRequestCreation(UUID id, Integer versionNumber) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'id' is set
        if (id == null) {
            throw new WebClientResponseException("Missing the required parameter 'id' when calling mentorDocumentsIdVersionsVersionNumberGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("id", id);
        pathParams.put("versionNumber", versionNumber);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Document> localVarReturnType = new ParameterizedTypeReference<Document>() {};
        return apiClient.invokeAPI("/mentor/documents/{id}/versions/{versionNumber}", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get specific version
     * 
     * <p><b>200</b> - Document
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @param versionNumber The versionNumber parameter
     * @return Document
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Document> mentorDocumentsIdVersionsVersionNumberGet(UUID id, Integer versionNumber) throws WebClientResponseException {
        ParameterizedTypeReference<Document> localVarReturnType = new ParameterizedTypeReference<Document>() {};
        return mentorDocumentsIdVersionsVersionNumberGetRequestCreation(id, versionNumber).bodyToMono(localVarReturnType);
    }

    /**
     * Get specific version
     * 
     * <p><b>200</b> - Document
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @param versionNumber The versionNumber parameter
     * @return ResponseEntity&lt;Document&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Document>> mentorDocumentsIdVersionsVersionNumberGetWithHttpInfo(UUID id, Integer versionNumber) throws WebClientResponseException {
        ParameterizedTypeReference<Document> localVarReturnType = new ParameterizedTypeReference<Document>() {};
        return mentorDocumentsIdVersionsVersionNumberGetRequestCreation(id, versionNumber).toEntity(localVarReturnType);
    }

    /**
     * Get specific version
     * 
     * <p><b>200</b> - Document
     * <p><b>404</b> - Not found
     * <p><b>500</b> - Internal error
     * @param id The id parameter
     * @param versionNumber The versionNumber parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mentorDocumentsIdVersionsVersionNumberGetWithResponseSpec(UUID id, Integer versionNumber) throws WebClientResponseException {
        return mentorDocumentsIdVersionsVersionNumberGetRequestCreation(id, versionNumber);
    }
    /**
     * Create a new document
     * 
     * <p><b>201</b> - Created document
     * <p><b>500</b> - Internal error
     * @param createDocumentRequest Create document
     * @return Document
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mentorDocumentsPostRequestCreation(CreateDocumentRequest createDocumentRequest) throws WebClientResponseException {
        Object postBody = createDocumentRequest;
        // verify the required parameter 'createDocumentRequest' is set
        if (createDocumentRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'createDocumentRequest' when calling mentorDocumentsPost", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { 
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<Document> localVarReturnType = new ParameterizedTypeReference<Document>() {};
        return apiClient.invokeAPI("/mentor/documents", HttpMethod.POST, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Create a new document
     * 
     * <p><b>201</b> - Created document
     * <p><b>500</b> - Internal error
     * @param createDocumentRequest Create document
     * @return Document
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Document> mentorDocumentsPost(CreateDocumentRequest createDocumentRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Document> localVarReturnType = new ParameterizedTypeReference<Document>() {};
        return mentorDocumentsPostRequestCreation(createDocumentRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Create a new document
     * 
     * <p><b>201</b> - Created document
     * <p><b>500</b> - Internal error
     * @param createDocumentRequest Create document
     * @return ResponseEntity&lt;Document&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Document>> mentorDocumentsPostWithHttpInfo(CreateDocumentRequest createDocumentRequest) throws WebClientResponseException {
        ParameterizedTypeReference<Document> localVarReturnType = new ParameterizedTypeReference<Document>() {};
        return mentorDocumentsPostRequestCreation(createDocumentRequest).toEntity(localVarReturnType);
    }

    /**
     * Create a new document
     * 
     * <p><b>201</b> - Created document
     * <p><b>500</b> - Internal error
     * @param createDocumentRequest Create document
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mentorDocumentsPostWithResponseSpec(CreateDocumentRequest createDocumentRequest) throws WebClientResponseException {
        return mentorDocumentsPostRequestCreation(createDocumentRequest);
    }
}
