package de.tum.in.www1.hephaestus.workspace.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an attempt is made to remove the last OWNER from a workspace.
 * <p>
 * Every workspace must have at least one owner for administrative purposes.
 * This exception prevents accidental orphaning of workspaces.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class LastOwnerRemovalException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String workspaceSlug;

    public LastOwnerRemovalException(String workspaceSlug) {
        super(
            String.format(
                "Cannot remove the last OWNER from workspace '%s'. " +
                    "Assign another user as OWNER before removing this one.",
                workspaceSlug
            )
        );
        this.workspaceSlug = workspaceSlug;
    }

    public String getWorkspaceSlug() {
        return workspaceSlug;
    }
}
