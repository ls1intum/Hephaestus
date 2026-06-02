package de.tum.cit.aet.hephaestus.agent.mentor.chat.exception;

import java.io.Serial;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import tools.jackson.databind.JsonNode;

/** JSON-RPC error response from the runner, surfaced to callers as an exception. */
@ResponseStatus(HttpStatus.BAD_GATEWAY)
public final class MentorRunnerException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** PI SDK threw inside the runner. */
    public static final int CODE_PI_ERROR = -32002;

    /** Runner saw an invalid-state precondition (e.g. abort with no in-flight turn). */
    public static final int CODE_INVALID_STATE = -32003;

    private final int code;
    private final transient JsonNode rawError;

    public MentorRunnerException(int code, String message, JsonNode rawError) {
        super("runner error " + code + ": " + message);
        this.code = code;
        this.rawError = rawError;
    }

    public int code() {
        return code;
    }

    @Nullable
    public JsonNode rawError() {
        return rawError;
    }

    /** True when the sandbox state is likely corrupted and must not be reused for another turn. */
    public boolean poisonsSandbox() {
        return code == CODE_PI_ERROR || code == CODE_INVALID_STATE;
    }
}
