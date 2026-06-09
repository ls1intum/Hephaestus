package de.tum.cit.aet.hephaestus.core;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.netty.resolver.AddressResolver;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Proves the resolver wrapper rejects an answer that resolves to a non-public address (the DNS-rebind
 * close), and passes a public one through. A stub delegate returns a fixed resolution, so the test
 * exercises the gate logic without real DNS; {@link ImmediateEventExecutor} runs listeners inline.
 */
class SsrfGuardedResolverGroupTest extends BaseUnitTest {

    private static final EventExecutor EXEC = ImmediateEventExecutor.INSTANCE;
    private static final SocketAddress UNRESOLVED = InetSocketAddress.createUnresolved("host.test", 443);

    private static SsrfGuardedResolverGroup.GuardedResolver resolverReturning(String literalIp) throws Exception {
        InetSocketAddress fixed = new InetSocketAddress(InetAddress.getByName(literalIp), 443);
        return new SsrfGuardedResolverGroup.GuardedResolver(EXEC, new StubResolver(fixed));
    }

    @Test
    void resolveFailsWhenHostResolvesToInternalAddress() throws Exception {
        Future<InetSocketAddress> result = resolverReturning("169.254.169.254").resolve(UNRESOLVED);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.cause()).isInstanceOf(UnknownHostException.class);
    }

    @Test
    void resolveAllFailsWhenAnyAddressIsInternal() throws Exception {
        Future<List<InetSocketAddress>> result = resolverReturning("10.0.0.9").resolveAll(UNRESOLVED);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.cause()).isInstanceOf(UnknownHostException.class);
    }

    @Test
    void resolvePassesThroughPublicAddress() throws Exception {
        Future<InetSocketAddress> result = resolverReturning("8.8.8.8").resolve(UNRESOLVED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getNow().getAddress().getHostAddress()).isEqualTo("8.8.8.8");
    }

    @Test
    void resolveIntoCallerPromiseGatesInternalAddress() throws Exception {
        // The 2-arg contract: the caller reads the promise it passed, NOT the returned future. The gate
        // MUST complete that promise with the validated verdict (a regression here is a silent bypass).
        Promise<InetSocketAddress> caller = EXEC.newPromise();
        Future<InetSocketAddress> ret = resolverReturning("169.254.169.254").resolve(UNRESOLVED, caller);

        assertThat(ret).isSameAs(caller);
        assertThat(caller.isSuccess()).isFalse();
        assertThat(caller.cause()).isInstanceOf(UnknownHostException.class);
    }

    @Test
    void resolveIntoCallerPromisePassesPublicAddress() throws Exception {
        Promise<InetSocketAddress> caller = EXEC.newPromise();
        resolverReturning("1.1.1.1").resolve(UNRESOLVED, caller);

        assertThat(caller.isSuccess()).isTrue();
        assertThat(caller.getNow().getAddress().getHostAddress()).isEqualTo("1.1.1.1");
    }

    @Test
    void resolveAllIntoCallerPromiseGatesInternalAddress() throws Exception {
        Promise<List<InetSocketAddress>> caller = EXEC.newPromise();
        resolverReturning("10.0.0.9").resolveAll(UNRESOLVED, caller);

        assertThat(caller.isSuccess()).isFalse();
        assertThat(caller.cause()).isInstanceOf(UnknownHostException.class);
    }

    @Test
    void blockedReasonRejectsUnresolvedAndInternal() throws Exception {
        assertThat(SsrfGuardedResolverGroup.blockedReason(InetSocketAddress.createUnresolved("x", 1))).isNotNull();
        assertThat(
            SsrfGuardedResolverGroup.blockedReason(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 1))
        ).isNotNull();
        assertThat(
            SsrfGuardedResolverGroup.blockedReason(new InetSocketAddress(InetAddress.getByName("1.1.1.1"), 1))
        ).isNull();
    }

    /** Delegate resolver that always succeeds with one fixed, already-resolved address. */
    private record StubResolver(InetSocketAddress fixed) implements AddressResolver<InetSocketAddress> {
        @Override
        public boolean isSupported(SocketAddress address) {
            return true;
        }

        @Override
        public boolean isResolved(SocketAddress address) {
            return address instanceof InetSocketAddress isa && !isa.isUnresolved();
        }

        @Override
        public Future<InetSocketAddress> resolve(SocketAddress address) {
            return EXEC.newSucceededFuture(fixed);
        }

        @Override
        public Future<InetSocketAddress> resolve(SocketAddress address, Promise<InetSocketAddress> promise) {
            return promise.setSuccess(fixed);
        }

        @Override
        public Future<List<InetSocketAddress>> resolveAll(SocketAddress address) {
            return EXEC.newSucceededFuture(List.of(fixed));
        }

        @Override
        public Future<List<InetSocketAddress>> resolveAll(
            SocketAddress address,
            Promise<List<InetSocketAddress>> promise
        ) {
            return promise.setSuccess(List.of(fixed));
        }

        @Override
        public void close() {
            // no-op stub
        }
    }
}
