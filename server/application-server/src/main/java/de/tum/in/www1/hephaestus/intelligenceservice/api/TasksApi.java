package de.tum.in.www1.hephaestus.intelligenceservice.api;

import de.tum.in.www1.hephaestus.intelligenceservice.ApiClient;

import java.math.BigDecimal;
import de.tum.in.www1.hephaestus.intelligenceservice.model.InsertTask;
import de.tum.in.www1.hephaestus.intelligenceservice.model.PatchTask;
import de.tum.in.www1.hephaestus.intelligenceservice.model.Task;
import de.tum.in.www1.hephaestus.intelligenceservice.model.TasksIdGet404Response;
import de.tum.in.www1.hephaestus.intelligenceservice.model.TasksIdGet422Response;
import de.tum.in.www1.hephaestus.intelligenceservice.model.TasksIdPatch422Response;
import de.tum.in.www1.hephaestus.intelligenceservice.model.TasksPost422Response;

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
public class TasksApi {
    private ApiClient apiClient;

    public TasksApi() {
        this(new ApiClient());
    }

    @Autowired
    public TasksApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 
     * 
     * <p><b>200</b> - The list of tasks
     * @return List&lt;Task&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec tasksGetRequestCreation() throws WebClientResponseException {
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

        ParameterizedTypeReference<Task> localVarReturnType = new ParameterizedTypeReference<Task>() {};
        return apiClient.invokeAPI("/tasks", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>200</b> - The list of tasks
     * @return List&lt;Task&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Task> tasksGet() throws WebClientResponseException {
        ParameterizedTypeReference<Task> localVarReturnType = new ParameterizedTypeReference<Task>() {};
        return tasksGetRequestCreation().bodyToFlux(localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>200</b> - The list of tasks
     * @return ResponseEntity&lt;List&lt;Task&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Task>>> tasksGetWithHttpInfo() throws WebClientResponseException {
        ParameterizedTypeReference<Task> localVarReturnType = new ParameterizedTypeReference<Task>() {};
        return tasksGetRequestCreation().toEntityList(localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>200</b> - The list of tasks
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tasksGetWithResponseSpec() throws WebClientResponseException {
        return tasksGetRequestCreation();
    }
    /**
     * 
     * 
     * <p><b>204</b> - Task deleted
     * <p><b>404</b> - Task not found
     * <p><b>422</b> - Invalid id error
     * @param id The id parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec tasksIdDeleteRequestCreation(BigDecimal id) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'id' is set
        if (id == null) {
            throw new WebClientResponseException("Missing the required parameter 'id' when calling tasksIdDelete", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("id", id);

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

        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return apiClient.invokeAPI("/tasks/{id}", HttpMethod.DELETE, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>204</b> - Task deleted
     * <p><b>404</b> - Task not found
     * <p><b>422</b> - Invalid id error
     * @param id The id parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Void> tasksIdDelete(BigDecimal id) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return tasksIdDeleteRequestCreation(id).bodyToMono(localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>204</b> - Task deleted
     * <p><b>404</b> - Task not found
     * <p><b>422</b> - Invalid id error
     * @param id The id parameter
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Void>> tasksIdDeleteWithHttpInfo(BigDecimal id) throws WebClientResponseException {
        ParameterizedTypeReference<Void> localVarReturnType = new ParameterizedTypeReference<Void>() {};
        return tasksIdDeleteRequestCreation(id).toEntity(localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>204</b> - Task deleted
     * <p><b>404</b> - Task not found
     * <p><b>422</b> - Invalid id error
     * @param id The id parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tasksIdDeleteWithResponseSpec(BigDecimal id) throws WebClientResponseException {
        return tasksIdDeleteRequestCreation(id);
    }
    /**
     * 
     * 
     * <p><b>200</b> - The requested task
     * <p><b>404</b> - Task not found
     * <p><b>422</b> - Invalid id error
     * @param id The id parameter
     * @return Task
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec tasksIdGetRequestCreation(BigDecimal id) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'id' is set
        if (id == null) {
            throw new WebClientResponseException("Missing the required parameter 'id' when calling tasksIdGet", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("id", id);

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

        ParameterizedTypeReference<Task> localVarReturnType = new ParameterizedTypeReference<Task>() {};
        return apiClient.invokeAPI("/tasks/{id}", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>200</b> - The requested task
     * <p><b>404</b> - Task not found
     * <p><b>422</b> - Invalid id error
     * @param id The id parameter
     * @return Task
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Task> tasksIdGet(BigDecimal id) throws WebClientResponseException {
        ParameterizedTypeReference<Task> localVarReturnType = new ParameterizedTypeReference<Task>() {};
        return tasksIdGetRequestCreation(id).bodyToMono(localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>200</b> - The requested task
     * <p><b>404</b> - Task not found
     * <p><b>422</b> - Invalid id error
     * @param id The id parameter
     * @return ResponseEntity&lt;Task&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Task>> tasksIdGetWithHttpInfo(BigDecimal id) throws WebClientResponseException {
        ParameterizedTypeReference<Task> localVarReturnType = new ParameterizedTypeReference<Task>() {};
        return tasksIdGetRequestCreation(id).toEntity(localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>200</b> - The requested task
     * <p><b>404</b> - Task not found
     * <p><b>422</b> - Invalid id error
     * @param id The id parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tasksIdGetWithResponseSpec(BigDecimal id) throws WebClientResponseException {
        return tasksIdGetRequestCreation(id);
    }
    /**
     * 
     * 
     * <p><b>200</b> - The updated task
     * <p><b>404</b> - Task not found
     * <p><b>422</b> - The validation error(s)
     * @param id The id parameter
     * @param patchTask The task updates
     * @return Task
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec tasksIdPatchRequestCreation(BigDecimal id, PatchTask patchTask) throws WebClientResponseException {
        Object postBody = patchTask;
        // verify the required parameter 'id' is set
        if (id == null) {
            throw new WebClientResponseException("Missing the required parameter 'id' when calling tasksIdPatch", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'patchTask' is set
        if (patchTask == null) {
            throw new WebClientResponseException("Missing the required parameter 'patchTask' when calling tasksIdPatch", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("id", id);

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

        ParameterizedTypeReference<Task> localVarReturnType = new ParameterizedTypeReference<Task>() {};
        return apiClient.invokeAPI("/tasks/{id}", HttpMethod.PATCH, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>200</b> - The updated task
     * <p><b>404</b> - Task not found
     * <p><b>422</b> - The validation error(s)
     * @param id The id parameter
     * @param patchTask The task updates
     * @return Task
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Task> tasksIdPatch(BigDecimal id, PatchTask patchTask) throws WebClientResponseException {
        ParameterizedTypeReference<Task> localVarReturnType = new ParameterizedTypeReference<Task>() {};
        return tasksIdPatchRequestCreation(id, patchTask).bodyToMono(localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>200</b> - The updated task
     * <p><b>404</b> - Task not found
     * <p><b>422</b> - The validation error(s)
     * @param id The id parameter
     * @param patchTask The task updates
     * @return ResponseEntity&lt;Task&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Task>> tasksIdPatchWithHttpInfo(BigDecimal id, PatchTask patchTask) throws WebClientResponseException {
        ParameterizedTypeReference<Task> localVarReturnType = new ParameterizedTypeReference<Task>() {};
        return tasksIdPatchRequestCreation(id, patchTask).toEntity(localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>200</b> - The updated task
     * <p><b>404</b> - Task not found
     * <p><b>422</b> - The validation error(s)
     * @param id The id parameter
     * @param patchTask The task updates
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tasksIdPatchWithResponseSpec(BigDecimal id, PatchTask patchTask) throws WebClientResponseException {
        return tasksIdPatchRequestCreation(id, patchTask);
    }
    /**
     * 
     * 
     * <p><b>200</b> - The created task
     * <p><b>422</b> - The validation error(s)
     * @param insertTask The task to create
     * @return Task
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec tasksPostRequestCreation(InsertTask insertTask) throws WebClientResponseException {
        Object postBody = insertTask;
        // verify the required parameter 'insertTask' is set
        if (insertTask == null) {
            throw new WebClientResponseException("Missing the required parameter 'insertTask' when calling tasksPost", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
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

        ParameterizedTypeReference<Task> localVarReturnType = new ParameterizedTypeReference<Task>() {};
        return apiClient.invokeAPI("/tasks", HttpMethod.POST, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>200</b> - The created task
     * <p><b>422</b> - The validation error(s)
     * @param insertTask The task to create
     * @return Task
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Task> tasksPost(InsertTask insertTask) throws WebClientResponseException {
        ParameterizedTypeReference<Task> localVarReturnType = new ParameterizedTypeReference<Task>() {};
        return tasksPostRequestCreation(insertTask).bodyToMono(localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>200</b> - The created task
     * <p><b>422</b> - The validation error(s)
     * @param insertTask The task to create
     * @return ResponseEntity&lt;Task&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Task>> tasksPostWithHttpInfo(InsertTask insertTask) throws WebClientResponseException {
        ParameterizedTypeReference<Task> localVarReturnType = new ParameterizedTypeReference<Task>() {};
        return tasksPostRequestCreation(insertTask).toEntity(localVarReturnType);
    }

    /**
     * 
     * 
     * <p><b>200</b> - The created task
     * <p><b>422</b> - The validation error(s)
     * @param insertTask The task to create
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec tasksPostWithResponseSpec(InsertTask insertTask) throws WebClientResponseException {
        return tasksPostRequestCreation(insertTask);
    }
}
