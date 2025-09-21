package de.tum.in.www1.hephaestus.intelligenceservice.api;

import de.tum.in.www1.hephaestus.intelligenceservice.ApiClient;

import de.tum.in.www1.hephaestus.intelligenceservice.model.DetectorRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.DetectorResponse;

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
public class DetectorApi {
    private ApiClient apiClient;

    public DetectorApi() {
        this(new ApiClient());
    }

    @Autowired
    public DetectorApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Detect bad practices for a pull request
     * 
     * <p><b>200</b> - Detection response
     * @param detectorRequest Detector request
     * @return DetectorResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec detectorPostRequestCreation(DetectorRequest detectorRequest) throws WebClientResponseException {
        Object postBody = detectorRequest;
        // verify the required parameter 'detectorRequest' is set
        if (detectorRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'detectorRequest' when calling detectorPost", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<DetectorResponse> localVarReturnType = new ParameterizedTypeReference<DetectorResponse>() {};
        return apiClient.invokeAPI("/detector", HttpMethod.POST, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Detect bad practices for a pull request
     * 
     * <p><b>200</b> - Detection response
     * @param detectorRequest Detector request
     * @return DetectorResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<DetectorResponse> detectorPost(DetectorRequest detectorRequest) throws WebClientResponseException {
        ParameterizedTypeReference<DetectorResponse> localVarReturnType = new ParameterizedTypeReference<DetectorResponse>() {};
        return detectorPostRequestCreation(detectorRequest).bodyToMono(localVarReturnType);
    }

    /**
     * Detect bad practices for a pull request
     * 
     * <p><b>200</b> - Detection response
     * @param detectorRequest Detector request
     * @return ResponseEntity&lt;DetectorResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<DetectorResponse>> detectorPostWithHttpInfo(DetectorRequest detectorRequest) throws WebClientResponseException {
        ParameterizedTypeReference<DetectorResponse> localVarReturnType = new ParameterizedTypeReference<DetectorResponse>() {};
        return detectorPostRequestCreation(detectorRequest).toEntity(localVarReturnType);
    }

    /**
     * Detect bad practices for a pull request
     * 
     * <p><b>200</b> - Detection response
     * @param detectorRequest Detector request
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec detectorPostWithResponseSpec(DetectorRequest detectorRequest) throws WebClientResponseException {
        return detectorPostRequestCreation(detectorRequest);
    }
}
