package de.tum.in.www1.hephaestus.intelligenceapi.api;

import de.tum.in.www1.hephaestus.intelligenceapi.ApiClient;
import de.tum.in.www1.hephaestus.intelligenceapi.BaseApi;

import de.tum.in.www1.hephaestus.intelligenceapi.model.ChatRequest;
import de.tum.in.www1.hephaestus.intelligenceapi.model.ChatResponse;
import de.tum.in.www1.hephaestus.intelligenceapi.model.HTTPValidationError;

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

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2024-10-25T13:05:04.950342+02:00[Europe/Berlin]", comments = "Generator version: 7.7.0")
@Component("de.tum.in.www1.hephaestus.intelligenceapi.api.DefaultApi")
public class DefaultApi extends BaseApi {

    public DefaultApi() {
        super(new ApiClient());
    }

    @Autowired
    public DefaultApi(ApiClient apiClient) {
        super(apiClient);
    }

    /**
     * Chat with LLM
     * 
     * <p><b>200</b> - Successful Response
     * <p><b>422</b> - Validation Error
     * @param chatRequest  (required)
     * @return ChatResponse
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public ChatResponse chatChatPost(ChatRequest chatRequest) throws RestClientException {
        return chatChatPostWithHttpInfo(chatRequest).getBody();
    }

    /**
     * Chat with LLM
     * 
     * <p><b>200</b> - Successful Response
     * <p><b>422</b> - Validation Error
     * @param chatRequest  (required)
     * @return ResponseEntity&lt;ChatResponse&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public ResponseEntity<ChatResponse> chatChatPostWithHttpInfo(ChatRequest chatRequest) throws RestClientException {
        Object localVarPostBody = chatRequest;
        
        // verify the required parameter 'chatRequest' is set
        if (chatRequest == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'chatRequest' when calling chatChatPost");
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

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<ChatResponse> localReturnType = new ParameterizedTypeReference<ChatResponse>() {};
        return apiClient.invokeAPI("/chat", HttpMethod.POST, Collections.<String, Object>emptyMap(), localVarQueryParams, localVarPostBody, localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAccept, localVarContentType, localVarAuthNames, localReturnType);
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
