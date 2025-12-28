package de.tum.in.www1.hephaestus.intelligenceservice.api;

import de.tum.in.www1.hephaestus.intelligenceservice.ApiClient;
import de.tum.in.www1.hephaestus.intelligenceservice.BaseApi;

import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatThreadGroup;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ErrorResponse;
import de.tum.in.www1.hephaestus.intelligenceservice.model.MentorChatRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.StreamPart;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ThreadDetail;
import java.util.UUID;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", comments = "Generator version: 7.7.0")
@SuppressWarnings("unused")
public class MentorApi extends BaseApi {

    public MentorApi() {
        super(new ApiClient());
    }

    public MentorApi(ApiClient apiClient) {
        super(apiClient);
    }

    /**
     * List chat threads grouped by time buckets
     * 
     * <p><b>200</b> - Grouped chat threads
     * <p><b>400</b> - Missing context
     * <p><b>500</b> - Internal error
     * @return List&lt;ChatThreadGroup&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public List<ChatThreadGroup> getGroupedThreads() throws RestClientException {
        return getGroupedThreadsWithHttpInfo().getBody();
    }

    /**
     * List chat threads grouped by time buckets
     * 
     * <p><b>200</b> - Grouped chat threads
     * <p><b>400</b> - Missing context
     * <p><b>500</b> - Internal error
     * @return ResponseEntity&lt;List&lt;ChatThreadGroup&gt;&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public ResponseEntity<List<ChatThreadGroup>> getGroupedThreadsWithHttpInfo() throws RestClientException {
        Object localVarPostBody = null;
        

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders localVarHeaderParams = new HttpHeaders();
        final MultiValueMap<String, String> localVarCookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> localVarFormParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
         };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = {  };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<List<ChatThreadGroup>> localReturnType = new ParameterizedTypeReference<List<ChatThreadGroup>>() {};
        return apiClient.invokeAPI("/mentor/threads/grouped", HttpMethod.GET, Collections.<String, Object>emptyMap(), localVarQueryParams, localVarPostBody, localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAccept, localVarContentType, localVarAuthNames, localReturnType);
    }
    /**
     * Get mentor chat thread detail
     * 
     * <p><b>200</b> - Thread detail with messages
     * <p><b>400</b> - Missing required context
     * <p><b>404</b> - Thread not found
     * <p><b>500</b> - Internal error
     * <p><b>503</b> - Service temporarily unavailable
     * @param threadId  (required)
     * @return ThreadDetail
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public ThreadDetail getThread(UUID threadId) throws RestClientException {
        return getThreadWithHttpInfo(threadId).getBody();
    }

    /**
     * Get mentor chat thread detail
     * 
     * <p><b>200</b> - Thread detail with messages
     * <p><b>400</b> - Missing required context
     * <p><b>404</b> - Thread not found
     * <p><b>500</b> - Internal error
     * <p><b>503</b> - Service temporarily unavailable
     * @param threadId  (required)
     * @return ResponseEntity&lt;ThreadDetail&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public ResponseEntity<ThreadDetail> getThreadWithHttpInfo(UUID threadId) throws RestClientException {
        Object localVarPostBody = null;
        
        // verify the required parameter 'threadId' is set
        if (threadId == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'threadId' when calling getThread");
        }
        
        // create path and map variables
        final Map<String, Object> uriVariables = new HashMap<String, Object>();
        uriVariables.put("threadId", threadId);

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders localVarHeaderParams = new HttpHeaders();
        final MultiValueMap<String, String> localVarCookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> localVarFormParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
         };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = {  };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<ThreadDetail> localReturnType = new ParameterizedTypeReference<ThreadDetail>() {};
        return apiClient.invokeAPI("/mentor/threads/{threadId}", HttpMethod.GET, uriVariables, localVarQueryParams, localVarPostBody, localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAccept, localVarContentType, localVarAuthNames, localReturnType);
    }
    /**
     * Handle mentor chat (set greeting&#x3D;true for initial greeting without user message)
     * 
     * <p><b>200</b> - Event stream of chat updates.
     * @param mentorChatRequest Chat request body (required)
     * @return StreamPart
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public StreamPart mentorChat(MentorChatRequest mentorChatRequest) throws RestClientException {
        return mentorChatWithHttpInfo(mentorChatRequest).getBody();
    }

    /**
     * Handle mentor chat (set greeting&#x3D;true for initial greeting without user message)
     * 
     * <p><b>200</b> - Event stream of chat updates.
     * @param mentorChatRequest Chat request body (required)
     * @return ResponseEntity&lt;StreamPart&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public ResponseEntity<StreamPart> mentorChatWithHttpInfo(MentorChatRequest mentorChatRequest) throws RestClientException {
        Object localVarPostBody = mentorChatRequest;
        
        // verify the required parameter 'mentorChatRequest' is set
        if (mentorChatRequest == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'mentorChatRequest' when calling mentorChat");
        }
        

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders localVarHeaderParams = new HttpHeaders();
        final MultiValueMap<String, String> localVarCookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> localVarFormParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "text/event-stream"
         };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { 
            "application/json"
         };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<StreamPart> localReturnType = new ParameterizedTypeReference<StreamPart>() {};
        return apiClient.invokeAPI("/mentor/chat", HttpMethod.POST, Collections.<String, Object>emptyMap(), localVarQueryParams, localVarPostBody, localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAccept, localVarContentType, localVarAuthNames, localReturnType);
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
            "text/event-stream"
         };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { 
            "application/json"
         };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        return apiClient.invokeAPI(localVarPath, method, uriVariables, localVarQueryParams, localVarPostBody, localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAccept, localVarContentType, localVarAuthNames, returnType);
    }
}
