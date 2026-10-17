package de.tum.cit.aet.hephaestus.core.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag("unit")
class GlobalControllerAdviceHttpTest {
    @ParameterizedTest
    @ValueSource(ints = {403, 404, 409, 429})
    void shouldPreserveProblemAndHeadersWhenFrameworkErrorIsThrown(int statusCode) throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new RefusingController())
                .setControllerAdvice(new GlobalControllerAdvice())
                .build();
        var response = mvc.perform(get("/refused/{status}", statusCode))
                .andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.detail").value("This review action is not available."))
                .andExpect(jsonPath("$.reason").value("ACTION_REFUSED"));
        if (statusCode == 429) response.andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
    }

    @RestController
    static class RefusingController {
        @GetMapping("/refused/{status}")
        void refuse(@PathVariable int status) {
            var body = ProblemDetail.forStatusAndDetail(
                    HttpStatus.valueOf(status), "This review action is not available.");
            body.setProperty("reason", "ACTION_REFUSED");
            var exception = new ErrorResponseException(HttpStatus.valueOf(status), body, null);
            if (status == 429) exception.getHeaders().set(HttpHeaders.RETRY_AFTER, "60");
            throw exception;
        }
    }
}
