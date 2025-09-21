package de.tum.in.www1.hephaestus.intelligenceservice.api;

import de.tum.in.www1.hephaestus.intelligenceservice.ApiClient;

import de.tum.in.www1.hephaestus.intelligenceservice.model.PoemRequest;

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
public class PoemApi {
    private ApiClient apiClient;

    public PoemApi() {
        this(new ApiClient());
    }

    @Autowired
    public PoemApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Generate a poem
     * Generates a short poem using the configured default AI model. The response is streamed as plain text. If Langfuse is configured, telemetry and prompt linking are enabled.
     * <p><b>200</b> - Streamed poem text
     * @param poemRequest Poem generation input
     * @return String
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec poemPostRequestCreation(PoemRequest poemRequest) throws WebClientResponseException {
        Object postBody = poemRequest;
        // verify the required parameter 'poemRequest' is set
        if (poemRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'poemRequest' when calling poemPost", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "text/plain"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { 
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        ParameterizedTypeReference<String> localVarReturnType = new ParameterizedTypeReference<String>() {};
        return apiClient.invokeAPI("/poem", HttpMethod.POST, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Generate a poem
     * Generates a short poem using the configured default AI model. The response is streamed as plain text. If Langfuse is configured, telemetry and prompt linking are enabled.
     * <p><b>200</b> - Streamed poem text
     * @param poemRequest Poem generation input
     * @return String
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<String> poemPost(PoemRequest poemRequest) throws WebClientResponseException {
        ParameterizedTypeReference<String> localVarReturnType = new ParameterizedTypeReference<String>() {};
        return poemPostRequestCreation(poemRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Generate a poem
     * Generates a short poem using the configured default AI model. The response is streamed as plain text. If Langfuse is configured, telemetry and prompt linking are enabled.
     * <p><b>200</b> - Streamed poem text
     * @param poemRequest Poem generation input
     * @return ResponseEntity&lt;String&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<String>> poemPostWithHttpInfo(PoemRequest poemRequest) throws WebClientResponseException {
        ParameterizedTypeReference<String> localVarReturnType = new ParameterizedTypeReference<String>() {};
        return poemPostRequestCreation(poemRequest).toEntity(localVarReturnType);
    }

    /**
     * Generate a poem
     * Generates a short poem using the configured default AI model. The response is streamed as plain text. If Langfuse is configured, telemetry and prompt linking are enabled.
     * <p><b>200</b> - Streamed poem text
     * @param poemRequest Poem generation input
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec poemPostWithResponseSpec(PoemRequest poemRequest) throws WebClientResponseException {
        return poemPostRequestCreation(poemRequest);
    }
}
