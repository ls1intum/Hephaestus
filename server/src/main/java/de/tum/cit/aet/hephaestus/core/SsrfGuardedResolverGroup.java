package de.tum.cit.aet.hephaestus.core;

import de.tum.cit.aet.hephaestus.core.security.PrivateAddressGuard;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * A Netty {@link AddressResolverGroup} that rejects DNS answers resolving to a non-public address,
 * closing the DNS-rebind / TOCTOU SSRF window at the HTTP-client layer (OWASP SSRF Prevention Cheat
 * Sheet recommends exactly this: do the IP check where the connection is actually made).
 *
 * <p>Validating only the URL string and then letting the client re-resolve DNS at connect time is the
 * classic bypass: an attacker host with a low TTL answers with a public IP during validation and flips
 * to {@code 169.254.169.254} / {@code 127.0.0.1} for the real connection. Here the check runs on the
 * SAME resolution the client uses to connect (we wrap the resolver and validate its output before
 * handing it back), so there is no second lookup to race — a non-public answer fails the resolution and
 * the connection never opens.
 *
 * <p>This enforces, for hostnames, the very policy {@code core.security.ServerUrlValidator} already
 * enforces for literal IPs in user-supplied server URLs — it does not add a new restriction, it stops a
 * bypass of the existing one. Delegates to {@link DefaultAddressResolverGroup} (the JVM/system resolver,
 * matching {@link WebClientConnectors#systemDns()}).
 */
public final class SsrfGuardedResolverGroup extends AddressResolverGroup<InetSocketAddress> {

    public static final SsrfGuardedResolverGroup INSTANCE = new SsrfGuardedResolverGroup(false);

    /**
     * Loopback-exempt variant: still blocks every other private/reserved range (so DNS rebinding to a
     * non-loopback private address is closed exactly like {@link #INSTANCE}), but lets a RESOLVED
     * loopback address through. For outbound calls whose policy layer has already decided loopback is
     * an acceptable dev/e2e target (see {@code EgressPolicy#allowLoopback} /
     * {@code hephaestus.llm.egress.allow-loopback}) — without this, that policy's own literal-host
     * loopback allowance would be silently re-blocked at connect time by the general-purpose guard.
     */
    public static final SsrfGuardedResolverGroup LOOPBACK_EXEMPT_INSTANCE = new SsrfGuardedResolverGroup(true);

    private final boolean allowLoopback;

    private SsrfGuardedResolverGroup(boolean allowLoopback) {
        this.allowLoopback = allowLoopback;
    }

    @Override
    protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
        return new GuardedResolver(executor, DefaultAddressResolverGroup.INSTANCE.getResolver(executor), allowLoopback);
    }

    /**
     * Reject the resolution if the resolved address is non-public. Package-private + returning the
     * {@link UnknownHostException} (rather than throwing) so it is unit-testable with a stub delegate.
     */
    static UnknownHostException blockedReason(InetSocketAddress resolved) {
        return blockedReason(resolved, false);
    }

    /** Overload threading through the loopback exemption — see {@link #LOOPBACK_EXEMPT_INSTANCE}. */
    static UnknownHostException blockedReason(InetSocketAddress resolved, boolean allowLoopback) {
        InetAddress addr = (resolved == null) ? null : resolved.getAddress();
        if (addr == null) {
            return new UnknownHostException("address did not resolve");
        }
        if (allowLoopback && addr.isLoopbackAddress()) {
            return null;
        }
        if (PrivateAddressGuard.isNonPublic(addr)) {
            return new UnknownHostException("blocked non-public address (SSRF guard): " + addr.getHostAddress());
        }
        return null;
    }

    /** Wraps a delegate resolver and validates every resolved address before completing the promise. */
    static final class GuardedResolver implements AddressResolver<InetSocketAddress> {

        private final EventExecutor executor;
        private final AddressResolver<InetSocketAddress> delegate;
        private final boolean allowLoopback;

        GuardedResolver(EventExecutor executor, AddressResolver<InetSocketAddress> delegate) {
            this(executor, delegate, false);
        }

        GuardedResolver(EventExecutor executor, AddressResolver<InetSocketAddress> delegate, boolean allowLoopback) {
            this.executor = executor;
            this.delegate = delegate;
            this.allowLoopback = allowLoopback;
        }

        @Override
        public boolean isSupported(SocketAddress address) {
            return delegate.isSupported(address);
        }

        @Override
        public boolean isResolved(SocketAddress address) {
            return delegate.isResolved(address);
        }

        // CRITICAL on the 2-arg overloads: resolve the delegate into its OWN (1-arg) future and gate the
        // result into the caller's promise. We must NOT call delegate.resolve(address, callerPromise) —
        // that completes the caller's promise with the UNVALIDATED address (the caller reads the promise,
        // not our return value), which is an SSRF bypass. The 1-arg overloads gate into a fresh promise.

        @Override
        public Future<InetSocketAddress> resolve(SocketAddress address) {
            return gateOne(delegate.resolve(address), executor.newPromise());
        }

        @Override
        public Future<InetSocketAddress> resolve(SocketAddress address, Promise<InetSocketAddress> promise) {
            return gateOne(delegate.resolve(address), promise);
        }

        @Override
        public Future<List<InetSocketAddress>> resolveAll(SocketAddress address) {
            return gateAll(delegate.resolveAll(address), executor.newPromise());
        }

        @Override
        public Future<List<InetSocketAddress>> resolveAll(
            SocketAddress address,
            Promise<List<InetSocketAddress>> promise
        ) {
            return gateAll(delegate.resolveAll(address), promise);
        }

        private Future<InetSocketAddress> gateOne(Future<InetSocketAddress> source, Promise<InetSocketAddress> target) {
            source.addListener(
                (FutureListener<InetSocketAddress>) f -> {
                    if (!f.isSuccess()) {
                        target.setFailure(f.cause());
                        return;
                    }
                    UnknownHostException blocked = blockedReason(f.getNow(), allowLoopback);
                    if (blocked != null) {
                        target.setFailure(blocked);
                    } else {
                        target.setSuccess(f.getNow());
                    }
                }
            );
            return target;
        }

        private Future<List<InetSocketAddress>> gateAll(
            Future<List<InetSocketAddress>> source,
            Promise<List<InetSocketAddress>> target
        ) {
            source.addListener(
                (FutureListener<List<InetSocketAddress>>) f -> {
                    if (!f.isSuccess()) {
                        target.setFailure(f.cause());
                        return;
                    }
                    for (InetSocketAddress isa : f.getNow()) {
                        UnknownHostException blocked = blockedReason(isa, allowLoopback);
                        if (blocked != null) {
                            target.setFailure(blocked);
                            return;
                        }
                    }
                    target.setSuccess(f.getNow());
                }
            );
            return target;
        }

        @Override
        public void close() {
            // The delegate resolver is owned/cached by the shared DefaultAddressResolverGroup singleton,
            // which closes it on its own lifecycle — closing it here would yank a process-wide resolver.
        }
    }
}
