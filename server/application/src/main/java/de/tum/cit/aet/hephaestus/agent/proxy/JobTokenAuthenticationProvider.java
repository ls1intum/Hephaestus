package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.JobJwt;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtInvalidException;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtIssuer;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtVerifier;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import java.io.Serial;
import java.util.Set;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

/** Checks credential validity and ownership of the current attempt without loading model configuration. */
final class JobTokenAuthenticationProvider implements AuthenticationProvider {
    private final ReviewJobRepository jobs;
    private final WorkerJwtVerifier verifier;
    private final MentorProxyCredentialRegistry mentors;
    private final String workerId;

    JobTokenAuthenticationProvider(
            ReviewJobRepository jobs,
            WorkerJwtVerifier verifier,
            MentorProxyCredentialRegistry mentors,
            String workerId) {
        this.jobs = jobs;
        this.verifier = verifier;
        this.mentors = mentors;
        this.workerId = workerId;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        var token = (BearerTokenAuthenticationToken) authentication;
        var mentor = mentors.authenticate(token.getToken());
        if (mentor.isPresent())
            return new JobTokenAuthentication(
                    new ProxyIdentity(null, null, mentor.get()), Set.of(WorkerJwtIssuer.LLM_PROXY_SCOPE));
        JobJwt jwt;
        try {
            if (!(verifier.verify(token.getToken()) instanceof JobJwt value)) throw invalid();
            jwt = value;
        } catch (WorkerJwtInvalidException invalid) {
            throw new BadCredentialsException("Invalid or expired credential", invalid);
        }
        var job = jobs.findById(jwt.jobId()).orElseThrow(JobTokenAuthenticationProvider::invalid);
        if (job.getStatus() != ReviewJobStatus.RUNNING
                || job.getWorkspaceId() != jwt.workspaceId()
                || job.getAttempt() != jwt.attempt()) throw invalid();
        var request = (JobTokenAuthenticationFilter.RequestIdentity) token.getDetails();
        if (request != null
                && request.runtimeJobId() != null
                && !request.runtimeJobId().equals(jwt.jobId().toString())) throw invalid();
        if (!workerId.equals(job.getWorkerId())) {
            if (request != null && request.resultUpload()) throw new WorkerOwnershipConflict();
            throw invalid();
        }
        return new JobTokenAuthentication(new ProxyIdentity(jwt, workerId, null), jwt.scopes());
    }

    static final class WorkerOwnershipConflict extends BadCredentialsException {
        @Serial
        private static final long serialVersionUID = 1L;

        WorkerOwnershipConflict() {
            super("Job is owned by another worker");
        }
    }

    private static BadCredentialsException invalid() {
        return new BadCredentialsException("Invalid or expired credential");
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return BearerTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
