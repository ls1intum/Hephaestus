package de.tum.in.www1.hephaestus.observability;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Stamps every response with the serving replica id — see docs/contributor/unified-pi-runtime.mdx. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReplicaIdentityFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Hephaestus-Replica";

    private static final Logger log = LoggerFactory.getLogger(ReplicaIdentityFilter.class);
    private static final String REPLICA_ID = resolveReplicaId();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        response.setHeader(HEADER_NAME, REPLICA_ID);
        chain.doFilter(request, response);
    }

    @PostConstruct
    void announceReplica() {
        log.info("Replica id: {}", REPLICA_ID);
    }

    // HOSTNAME is the container id under Docker; InetAddress fallback is dev-only.
    private static String resolveReplicaId() {
        String env = System.getenv("HOSTNAME");
        if (env != null && !env.isBlank()) return env;
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
