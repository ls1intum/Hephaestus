package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workers")
@ConditionalOnProperty(name = RuntimeRole.SERVER_PROPERTY, havingValue = "true", matchIfMissing = true)
@Hidden
public class WorkerTokenExchangeController {

    private static final Logger log = LoggerFactory.getLogger(WorkerTokenExchangeController.class);
    private static final int MAX_FAILURES_PER_IP_PER_MINUTE = 10;

    private final WorkerJwtIssuer issuer;
    private final WorkerTokenProperties properties;
    /**
     * Per-source-IP failure counter: caps brute-force attempts at {@value MAX_FAILURES_PER_IP_PER_MINUTE}
     * per minute. The registration token is high-entropy enough to make exhaustive search
     * impractical, but the counter bounds noise and makes auth-failure alerting tractable.
     */
    private final Cache<String, AtomicInteger> failuresByIp = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(1))
        .maximumSize(10_000)
        .build();

    public WorkerTokenExchangeController(WorkerJwtIssuer issuer, WorkerTokenProperties properties) {
        this.issuer = issuer;
        this.properties = properties;
    }

    @PostMapping("/exchange")
    public ResponseEntity<?> exchange(@RequestBody ExchangeRequest request, HttpServletRequest http) {
        if (!properties.isExchangeEnabled()) {
            log.warn("worker token exchange attempted but no registration token is configured");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String sourceIp = http.getRemoteAddr();
        AtomicInteger failures = failuresByIp.get(sourceIp, k -> new AtomicInteger(0));
        if (failures.get() >= MAX_FAILURES_PER_IP_PER_MINUTE) {
            log.warn("worker token exchange throttled: too many failures from sourceIp={}", sourceIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        if (request == null || request.workerId() == null || request.workerId().isBlank()) {
            failures.incrementAndGet();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (!constantTimeEquals(request.registrationToken(), properties.registrationToken())) {
            failures.incrementAndGet();
            log.warn("worker token exchange rejected: bad registration token for workerId={} sourceIp={}",
                request.workerId(), sourceIp);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        WorkerJwtIssuer.IssuedWorkerJwt issued = issuer.issue(request.workerId().trim());
        return ResponseEntity.ok(new ExchangeResponse(issued.token(), issued.expiresAt()));
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] ba = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return MessageDigest.isEqual(ba, bb);
    }

    public record ExchangeRequest(String workerId, String registrationToken) {}

    public record ExchangeResponse(String token, Instant expiresAt) {}
}
