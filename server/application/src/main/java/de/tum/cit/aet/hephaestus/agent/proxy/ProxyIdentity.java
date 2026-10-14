package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.proxy.MentorProxyCredentialRegistry.AuthenticatedSession;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.JobJwt;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Verified job attempt or mentor execution. Contains no bearer credential or frozen configuration. */
record ProxyIdentity(
        @Nullable JobJwt job,
        @Nullable String workerId,
        @Nullable AuthenticatedSession mentor) {
    ProxyIdentity {
        if ((job == null) == (mentor == null))
            throw new IllegalArgumentException("Exactly one proxy identity is required");
        if (job != null) Objects.requireNonNull(workerId, "workerId");
    }

    String description() {
        return job == null ? "mentor-session:" + Objects.requireNonNull(mentor).sessionId() : "job:" + job.jobId();
    }
}
