package de.tum.in.www1.hephaestus.gitprovider.common.github.graphql;

import java.util.List;
import org.springframework.graphql.ResponseError;

public class GitHubGraphQlException extends RuntimeException {

    private final List<ResponseError> errors;

    public GitHubGraphQlException(String message, List<ResponseError> errors) {
        super(message);
        this.errors = errors;
    }

    public List<ResponseError> getErrors() {
        return errors;
    }
}
