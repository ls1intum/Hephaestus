package de.tum.in.www1.hephaestus.intelligenceservice.api;

import de.tum.in.www1.hephaestus.intelligenceservice.ApiClient;

import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatThreadGroup;
import de.tum.in.www1.hephaestus.intelligenceservice.model.MentorChatPost200Response;
import de.tum.in.www1.hephaestus.intelligenceservice.model.MentorChatPostRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.MentorThreadsThreadIdGet200Response;
import de.tum.in.www1.hephaestus.intelligenceservice.model.MentorThreadsThreadIdGet404Response;
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
public class MentorApi {
    private ApiClient apiClient;

    public MentorApi() {
        this(new ApiClient());
    }

    @Autowired
    public MentorApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * List chat threads grouped by time buckets
     * 
     * <p><b>200</b> - Grouped chat threads
     * @return List&lt;ChatThreadGroup&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec getGroupedThreadsRequestCreation() throws WebClientResponseException {
        Object postBody = null;
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
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<ChatThreadGroup> localVarReturnType = new ParameterizedTypeReference<ChatThreadGroup>() {};
        return apiClient.invokeAPI("/mentor/threads/grouped", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List chat threads grouped by time buckets
     * 
     * <p><b>200</b> - Grouped chat threads
     * @return List&lt;ChatThreadGroup&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<ChatThreadGroup> getGroupedThreads() throws WebClientResponseException {
        ParameterizedTypeReference<ChatThreadGroup> localVarReturnType = new ParameterizedTypeReference<ChatThreadGroup>() {};
        return getGroupedThreadsRequestCreation().bodyToFlux(localVarReturnType);
    }

    /**
     * List chat threads grouped by time buckets
     * 
     * <p><b>200</b> - Grouped chat threads
     * @return ResponseEntity&lt;List&lt;ChatThreadGroup&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<ChatThreadGroup>>> getGroupedThreadsWithHttpInfo() throws WebClientResponseException {
        ParameterizedTypeReference<ChatThreadGroup> localVarReturnType = new ParameterizedTypeReference<ChatThreadGroup>() {};
        return getGroupedThreadsRequestCreation().toEntityList(localVarReturnType);
    }

    /**
     * List chat threads grouped by time buckets
     * 
     * <p><b>200</b> - Grouped chat threads
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec getGroupedThreadsWithResponseSpec() throws WebClientResponseException {
        return getGroupedThreadsRequestCreation();
    }
    /**
     * Handle mentor chat
     * 
     * <p><b>200</b> - Event stream of chat updates.
     * @param mentorChatPostRequest Chat request body
     * @return MentorChatPost200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mentorChatPostRequestCreation(MentorChatPostRequest mentorChatPostRequest) throws WebClientResponseException {
        Object postBody = mentorChatPostRequest;
        // verify the required parameter 'mentorChatPostRequest' is set
        if (mentorChatPostRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'mentorChatPostRequest' when calling mentorChatPost", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "text/event-stream"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { 
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<MentorChatPost200Response> localVarReturnType = new ParameterizedTypeReference<MentorChatPost200Response>() {};
        return apiClient.invokeAPI("/mentor/chat", HttpMethod.POST, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Handle mentor chat
     * 
     * <p><b>200</b> - Event stream of chat updates.
     * @param mentorChatPostRequest Chat request body
     * @return MentorChatPost200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<MentorChatPost200Response> mentorChatPost(MentorChatPostRequest mentorChatPostRequest) throws WebClientResponseException {
        ParameterizedTypeReference<MentorChatPost200Response> localVarReturnType = new ParameterizedTypeReference<MentorChatPost200Response>() {};
        return mentorChatPostRequestCreation(mentorChatPostRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Handle mentor chat
     * 
     * <p><b>200</b> - Event stream of chat updates.
     * @param mentorChatPostRequest Chat request body
     * @return ResponseEntity&lt;MentorChatPost200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<MentorChatPost200Response>> mentorChatPostWithHttpInfo(MentorChatPostRequest mentorChatPostRequest) throws WebClientResponseException {
        ParameterizedTypeReference<MentorChatPost200Response> localVarReturnType = new ParameterizedTypeReference<MentorChatPost200Response>() {};
        return mentorChatPostRequestCreation(mentorChatPostRequest).toEntity(localVarReturnType);
    }

    /**
     * Handle mentor chat
     * 
     * <p><b>200</b> - Event stream of chat updates.
     * @param mentorChatPostRequest Chat request body
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mentorChatPostWithResponseSpec(MentorChatPostRequest mentorChatPostRequest) throws WebClientResponseException {
        return mentorChatPostRequestCreation(mentorChatPostRequest);
    }
    /**
     * Get mentor chat thread detail
     * 
     * <p><b>200</b> - Thread detail with messages
     * <p><b>404</b> - Thread not found
     * <p><b>500</b> - Internal error
     * <p><b>503</b> - Service temporarily unavailable
     * @param threadId The threadId parameter
     * @return MentorThreadsThreadIdGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mentorThreadsThreadIdGetRequestCreation(UUID threadId) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'threadId' is set
        if (threadId == null) {
            throw new WebClientResponseException("Missing the required parameter 'threadId' when calling mentorThreadsThreadIdGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("threadId", threadId);

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

        ParameterizedTypeReference<MentorThreadsThreadIdGet200Response> localVarReturnType = new ParameterizedTypeReference<MentorThreadsThreadIdGet200Response>() {};
        return apiClient.invokeAPI("/mentor/threads/{threadId}", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Get mentor chat thread detail
     * 
     * <p><b>200</b> - Thread detail with messages
     * <p><b>404</b> - Thread not found
     * <p><b>500</b> - Internal error
     * <p><b>503</b> - Service temporarily unavailable
     * @param threadId The threadId parameter
     * @return MentorThreadsThreadIdGet200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<MentorThreadsThreadIdGet200Response> mentorThreadsThreadIdGet(UUID threadId) throws WebClientResponseException {
        ParameterizedTypeReference<MentorThreadsThreadIdGet200Response> localVarReturnType = new ParameterizedTypeReference<MentorThreadsThreadIdGet200Response>() {};
        return mentorThreadsThreadIdGetRequestCreation(threadId).bodyToMono(localVarReturnType);
    }

    /**
     * Get mentor chat thread detail
     * 
     * <p><b>200</b> - Thread detail with messages
     * <p><b>404</b> - Thread not found
     * <p><b>500</b> - Internal error
     * <p><b>503</b> - Service temporarily unavailable
     * @param threadId The threadId parameter
     * @return ResponseEntity&lt;MentorThreadsThreadIdGet200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<MentorThreadsThreadIdGet200Response>> mentorThreadsThreadIdGetWithHttpInfo(UUID threadId) throws WebClientResponseException {
        ParameterizedTypeReference<MentorThreadsThreadIdGet200Response> localVarReturnType = new ParameterizedTypeReference<MentorThreadsThreadIdGet200Response>() {};
        return mentorThreadsThreadIdGetRequestCreation(threadId).toEntity(localVarReturnType);
    }

    /**
     * Get mentor chat thread detail
     * 
     * <p><b>200</b> - Thread detail with messages
     * <p><b>404</b> - Thread not found
     * <p><b>500</b> - Internal error
     * <p><b>503</b> - Service temporarily unavailable
     * @param threadId The threadId parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mentorThreadsThreadIdGetWithResponseSpec(UUID threadId) throws WebClientResponseException {
        return mentorThreadsThreadIdGetRequestCreation(threadId);
    }
}
