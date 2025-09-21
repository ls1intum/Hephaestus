package de.tum.in.www1.hephaestus.intelligenceservice.api;

import de.tum.in.www1.hephaestus.intelligenceservice.ApiClient;

import de.tum.in.www1.hephaestus.intelligenceservice.model.HealthCheck;

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
public class HealthcheckApi {
    private ApiClient apiClient;

    public HealthcheckApi() {
        this(new ApiClient());
    }

    @Autowired
    public HealthcheckApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Perform a Health Check
     * Endpoint to perform a healthcheck on. This endpoint can primarily be used by Docker to ensure robust container orchestration and management. Other services which rely on proper functioning of the API service will not deploy if this endpoint returns any other HTTP status code except 200 (OK).
     * <p><b>200</b> - Return HTTP Status Code 200 (OK)
     * @return HealthCheck
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec healthGetRequestCreation() throws WebClientResponseException {
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

        ParameterizedTypeReference<HealthCheck> localVarReturnType = new ParameterizedTypeReference<HealthCheck>() {};
        return apiClient.invokeAPI("/health", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Perform a Health Check
     * Endpoint to perform a healthcheck on. This endpoint can primarily be used by Docker to ensure robust container orchestration and management. Other services which rely on proper functioning of the API service will not deploy if this endpoint returns any other HTTP status code except 200 (OK).
     * <p><b>200</b> - Return HTTP Status Code 200 (OK)
     * @return HealthCheck
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<HealthCheck> healthGet() throws WebClientResponseException {
        ParameterizedTypeReference<HealthCheck> localVarReturnType = new ParameterizedTypeReference<HealthCheck>() {};
        return healthGetRequestCreation().bodyToMono(localVarReturnType);
    }

    /**
     * Perform a Health Check
     * Endpoint to perform a healthcheck on. This endpoint can primarily be used by Docker to ensure robust container orchestration and management. Other services which rely on proper functioning of the API service will not deploy if this endpoint returns any other HTTP status code except 200 (OK).
     * <p><b>200</b> - Return HTTP Status Code 200 (OK)
     * @return ResponseEntity&lt;HealthCheck&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<HealthCheck>> healthGetWithHttpInfo() throws WebClientResponseException {
        ParameterizedTypeReference<HealthCheck> localVarReturnType = new ParameterizedTypeReference<HealthCheck>() {};
        return healthGetRequestCreation().toEntity(localVarReturnType);
    }

    /**
     * Perform a Health Check
     * Endpoint to perform a healthcheck on. This endpoint can primarily be used by Docker to ensure robust container orchestration and management. Other services which rely on proper functioning of the API service will not deploy if this endpoint returns any other HTTP status code except 200 (OK).
     * <p><b>200</b> - Return HTTP Status Code 200 (OK)
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec healthGetWithResponseSpec() throws WebClientResponseException {
        return healthGetRequestCreation();
    }
}
