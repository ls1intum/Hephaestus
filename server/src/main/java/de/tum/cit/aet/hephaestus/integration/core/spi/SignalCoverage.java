package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import java.util.Set;

/**
 * Answers "will anything ever raise this signal here?" — the question that separates a practice which
 * is merely quiet from one that is broken.
 *
 * <p>Two different answers, because they carry different consequences. A signal no <em>compiled</em>
 * integration declares it raises is a build-time mistake — the vocabulary offers something the system
 * cannot produce, and that must stop a boot. A signal no <em>connected</em> integration raises is an
 * ordinary fact about one workspace part-way through onboarding, and the practice bound to it is
 * dormant: worth surfacing, never worth crashing over. Conflating the two would either break every
 * workspace that has not finished connecting, or restore the silence this exists to remove.
 *
 * <p>A port so that the practices module can ask without importing the integration framework, the
 * connection store, or any vendor.
 */
public interface SignalCoverage {
    /**
     * Every signal some registered integration declares it raises, irrespective of any workspace's
     * connections. Empty for a signal nothing can produce.
     */
    Set<SignalName> compiledCoverage();

    /** Every signal some integration <em>connected to this workspace</em> declares it raises. */
    Set<SignalName> connectedCoverage(long workspaceId);

    /** Which integrations declare they raise this signal. Empty means nothing does. */
    Set<IntegrationKind> raisedBy(SignalName signal);
}
