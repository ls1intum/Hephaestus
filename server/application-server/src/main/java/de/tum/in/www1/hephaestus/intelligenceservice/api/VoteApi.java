package de.tum.in.www1.hephaestus.intelligenceservice.api;

import de.tum.in.www1.hephaestus.intelligenceservice.ApiClient;

import de.tum.in.www1.hephaestus.intelligenceservice.model.ChatMessageVote;
import de.tum.in.www1.hephaestus.intelligenceservice.model.MentorThreadsThreadIdGet404Response;
import java.util.UUID;
import de.tum.in.www1.hephaestus.intelligenceservice.model.VoteMessageRequest;

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
public class VoteApi {
    private ApiClient apiClient;

    public VoteApi() {
        this(new ApiClient());
    }

    @Autowired
    public VoteApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Vote on a chat message (upvote/downvote)
     * 
     * <p><b>200</b> - Vote recorded
     * <p><b>404</b> - Message not found
     * <p><b>500</b> - Internal error
     * @param messageId The messageId parameter
     * @param voteMessageRequest Vote request body
     * @return ChatMessageVote
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec mentorChatMessagesMessageIdVotePostRequestCreation(UUID messageId, VoteMessageRequest voteMessageRequest) throws WebClientResponseException {
        Object postBody = voteMessageRequest;
        // verify the required parameter 'messageId' is set
        if (messageId == null) {
            throw new WebClientResponseException("Missing the required parameter 'messageId' when calling mentorChatMessagesMessageIdVotePost", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'voteMessageRequest' is set
        if (voteMessageRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'voteMessageRequest' when calling mentorChatMessagesMessageIdVotePost", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("messageId", messageId);

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

        ParameterizedTypeReference<ChatMessageVote> localVarReturnType = new ParameterizedTypeReference<ChatMessageVote>() {};
        return apiClient.invokeAPI("/mentor/chat/messages/{messageId}/vote", HttpMethod.POST, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Vote on a chat message (upvote/downvote)
     * 
     * <p><b>200</b> - Vote recorded
     * <p><b>404</b> - Message not found
     * <p><b>500</b> - Internal error
     * @param messageId The messageId parameter
     * @param voteMessageRequest Vote request body
     * @return ChatMessageVote
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ChatMessageVote> mentorChatMessagesMessageIdVotePost(UUID messageId, VoteMessageRequest voteMessageRequest) throws WebClientResponseException {
        ParameterizedTypeReference<ChatMessageVote> localVarReturnType = new ParameterizedTypeReference<ChatMessageVote>() {};
        return mentorChatMessagesMessageIdVotePostRequestCreation(messageId, voteMessageRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Vote on a chat message (upvote/downvote)
     * 
     * <p><b>200</b> - Vote recorded
     * <p><b>404</b> - Message not found
     * <p><b>500</b> - Internal error
     * @param messageId The messageId parameter
     * @param voteMessageRequest Vote request body
     * @return ResponseEntity&lt;ChatMessageVote&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ChatMessageVote>> mentorChatMessagesMessageIdVotePostWithHttpInfo(UUID messageId, VoteMessageRequest voteMessageRequest) throws WebClientResponseException {
        ParameterizedTypeReference<ChatMessageVote> localVarReturnType = new ParameterizedTypeReference<ChatMessageVote>() {};
        return mentorChatMessagesMessageIdVotePostRequestCreation(messageId, voteMessageRequest).toEntity(localVarReturnType);
    }

    /**
     * Vote on a chat message (upvote/downvote)
     * 
     * <p><b>200</b> - Vote recorded
     * <p><b>404</b> - Message not found
     * <p><b>500</b> - Internal error
     * @param messageId The messageId parameter
     * @param voteMessageRequest Vote request body
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec mentorChatMessagesMessageIdVotePostWithResponseSpec(UUID messageId, VoteMessageRequest voteMessageRequest) throws WebClientResponseException {
        return mentorChatMessagesMessageIdVotePostRequestCreation(messageId, voteMessageRequest);
    }
}
