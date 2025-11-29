package de.tum.in.www1.hephaestus.workspace.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class RepositoryAccessForbiddenException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RepositoryAccessForbiddenException(String nameWithOwner) {
        super("GitHub App installation cannot access repository '" + nameWithOwner + "'. Grant access and retry.");
    }
}
