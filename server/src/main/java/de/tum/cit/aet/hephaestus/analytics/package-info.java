/**
 * Product analytics adapters (currently PostHog).
 *
 * <p>Renamed from {@code integrations/} in #1198 to avoid the {@code integration/} vs
 * {@code integrations/} foot-gun. This module hosts <em>analytics-only</em> outbound
 * clients — vendor integrations that participate in the unified webhook/oauth/SCM
 * framework live under {@code integration/}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Analytics")
package de.tum.cit.aet.hephaestus.analytics;
