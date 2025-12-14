package de.tum.in.www1.hephaestus.intelligenceservice.api;

import de.tum.in.www1.hephaestus.intelligenceservice.ApiClient;
import de.tum.in.www1.hephaestus.intelligenceservice.BaseApi;

import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatMessageVote;
import de.tum.in.www1.hephaestus.intelligenceservice.model.GetGroupedThreads500Response;
import java.util.UUID;
import de.tum.in.www1.hephaestus.intelligenceservice.model.VoteMessageRequest;

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
public class VoteApi extends BaseApi {

    public VoteApi() {
        super(new ApiClient());
    }

    public VoteApi(ApiClient apiClient) {
        super(apiClient);
    }

    /**
     * Vote on a chat message (upvote/downvote)
     * 
     * <p><b>200</b> - Vote recorded
     * <p><b>404</b> - Message not found
     * <p><b>500</b> - Internal error
     * @param messageId  (required)
     * @param voteMessageRequest Vote request body (required)
     * @return ChatMessageVote
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public ChatMessageVote voteMessage(UUID messageId, VoteMessageRequest voteMessageRequest) throws RestClientException {
        return voteMessageWithHttpInfo(messageId, voteMessageRequest).getBody();
    }

    /**
     * Vote on a chat message (upvote/downvote)
     * 
     * <p><b>200</b> - Vote recorded
     * <p><b>404</b> - Message not found
     * <p><b>500</b> - Internal error
     * @param messageId  (required)
     * @param voteMessageRequest Vote request body (required)
     * @return ResponseEntity&lt;ChatMessageVote&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public ResponseEntity<ChatMessageVote> voteMessageWithHttpInfo(UUID messageId, VoteMessageRequest voteMessageRequest) throws RestClientException {
        Object localVarPostBody = voteMessageRequest;
        
        // verify the required parameter 'messageId' is set
        if (messageId == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'messageId' when calling voteMessage");
        }
        
        // verify the required parameter 'voteMessageRequest' is set
        if (voteMessageRequest == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'voteMessageRequest' when calling voteMessage");
        }
        
        // create path and map variables
        final Map<String, Object> uriVariables = new HashMap<String, Object>();
        uriVariables.put("messageId", messageId);

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

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<ChatMessageVote> localReturnType = new ParameterizedTypeReference<ChatMessageVote>() {};
        return apiClient.invokeAPI("/mentor/chat/messages/{messageId}/vote", HttpMethod.POST, uriVariables, localVarQueryParams, localVarPostBody, localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAccept, localVarContentType, localVarAuthNames, localReturnType);
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

        String[] localVarAuthNames = new String[] {  };

        return apiClient.invokeAPI(localVarPath, method, uriVariables, localVarQueryParams, localVarPostBody, localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAccept, localVarContentType, localVarAuthNames, returnType);
    }
}
