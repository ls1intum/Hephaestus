package de.tum.in.www1.hephaestus.intelligenceservice.api;

import de.tum.in.www1.hephaestus.intelligenceservice.ApiClient;
import de.tum.in.www1.hephaestus.intelligenceservice.BaseApi;

import de.tum.in.www1.hephaestus.intelligenceservice.model.HealthCheck;

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
public class HealthcheckApi extends BaseApi {

    public HealthcheckApi() {
        super(new ApiClient());
    }

    public HealthcheckApi(ApiClient apiClient) {
        super(apiClient);
    }

    /**
     * Perform a Health Check
     * ## Perform a Health Check Endpoint to perform a healthcheck on. This endpoint can primarily be used Docker to ensure a robust container orchestration and management is in place. Other services which rely on proper functioning of the API service will not deploy if this endpoint returns any other HTTP status code except 200 (OK). Returns:     HealthCheck: Returns a JSON response with the health status
     * <p><b>200</b> - Return HTTP Status Code 200 (OK)
     * @return HealthCheck
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public HealthCheck getHealthHealthGet() throws RestClientException {
        return getHealthHealthGetWithHttpInfo().getBody();
    }

    /**
     * Perform a Health Check
     * ## Perform a Health Check Endpoint to perform a healthcheck on. This endpoint can primarily be used Docker to ensure a robust container orchestration and management is in place. Other services which rely on proper functioning of the API service will not deploy if this endpoint returns any other HTTP status code except 200 (OK). Returns:     HealthCheck: Returns a JSON response with the health status
     * <p><b>200</b> - Return HTTP Status Code 200 (OK)
     * @return ResponseEntity&lt;HealthCheck&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public ResponseEntity<HealthCheck> getHealthHealthGetWithHttpInfo() throws RestClientException {
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

        ParameterizedTypeReference<HealthCheck> localReturnType = new ParameterizedTypeReference<HealthCheck>() {};
        return apiClient.invokeAPI("/health", HttpMethod.GET, Collections.<String, Object>emptyMap(), localVarQueryParams, localVarPostBody, localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAccept, localVarContentType, localVarAuthNames, localReturnType);
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
        final String[] localVarContentTypes = {  };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] {  };

        return apiClient.invokeAPI(localVarPath, method, uriVariables, localVarQueryParams, localVarPostBody, localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAccept, localVarContentType, localVarAuthNames, returnType);
    }
}
