package de.tum.in.www1.hephaestus.intelligenceservice.api;

import de.tum.in.www1.hephaestus.intelligenceservice.ApiClient;
import de.tum.in.www1.hephaestus.intelligenceservice.BaseApi;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ISHTTPValidationError;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ISMentorMessage;
import de.tum.in.www1.hephaestus.intelligenceservice.model.ISMessageHistory;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

@jakarta.annotation.Generated(
    value = "org.openapitools.codegen.languages.JavaClientCodegen",
    comments = "Generator version: 7.7.0"
)
public class MentorApi extends BaseApi {

    public MentorApi() {
        super(new ApiClient());
    }

    public MentorApi(ApiClient apiClient) {
        super(apiClient);
    }

    /**
     * Start and continue a chat session with an LLM.
     *
     * <p><b>200</b> - Successful Response
     * <p><b>422</b> - Validation Error
     * @param isMessageHistory  (required)
     * @return ISMentorMessage
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public ISMentorMessage generateMentorPost(ISMessageHistory isMessageHistory) throws RestClientException {
        return generateMentorPostWithHttpInfo(isMessageHistory).getBody();
    }

    /**
     * Start and continue a chat session with an LLM.
     *
     * <p><b>200</b> - Successful Response
     * <p><b>422</b> - Validation Error
     * @param isMessageHistory  (required)
     * @return ResponseEntity&lt;ISMentorMessage&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public ResponseEntity<ISMentorMessage> generateMentorPostWithHttpInfo(ISMessageHistory isMessageHistory)
        throws RestClientException {
        Object localVarPostBody = isMessageHistory;

        // verify the required parameter 'isMessageHistory' is set
        if (isMessageHistory == null) {
            throw new HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Missing the required parameter 'isMessageHistory' when calling generateMentorPost"
            );
        }

        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders localVarHeaderParams = new HttpHeaders();
        final MultiValueMap<String, String> localVarCookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> localVarFormParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { "application/json" };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { "application/json" };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {};

        ParameterizedTypeReference<ISMentorMessage> localReturnType = new ParameterizedTypeReference<
            ISMentorMessage
        >() {};
        return apiClient.invokeAPI(
            "/mentor/",
            HttpMethod.POST,
            Collections.<String, Object>emptyMap(),
            localVarQueryParams,
            localVarPostBody,
            localVarHeaderParams,
            localVarCookieParams,
            localVarFormParams,
            localVarAccept,
            localVarContentType,
            localVarAuthNames,
            localReturnType
        );
    }

    @Override
    public <T> ResponseEntity<T> invokeAPI(
        String url,
        HttpMethod method,
        Object request,
        ParameterizedTypeReference<T> returnType
    ) throws RestClientException {
        String localVarPath = url.replace(apiClient.getBasePath(), "");
        Object localVarPostBody = request;

        final Map<String, Object> uriVariables = new HashMap<String, Object>();
        final MultiValueMap<String, String> localVarQueryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders localVarHeaderParams = new HttpHeaders();
        final MultiValueMap<String, String> localVarCookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> localVarFormParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { "application/json" };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { "application/json" };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {};

        return apiClient.invokeAPI(
            localVarPath,
            method,
            uriVariables,
            localVarQueryParams,
            localVarPostBody,
            localVarHeaderParams,
            localVarCookieParams,
            localVarFormParams,
            localVarAccept,
            localVarContentType,
            localVarAuthNames,
            returnType
        );
    }
}
