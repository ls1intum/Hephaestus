package de.tum.in.www1.hephaestus.core.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class EntityNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public EntityNotFoundException(String entityName, Long entityId) {
        super(entityName + " with id: \"" + entityId + "\" does not exist");
    }

    public EntityNotFoundException(String entityName, String entityIdentifier) {
        super(entityName + " with identifier: \"" + entityIdentifier + "\" does not exist");
    }
}
