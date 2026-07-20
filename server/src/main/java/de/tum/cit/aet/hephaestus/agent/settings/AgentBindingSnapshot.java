package de.tum.cit.aet.hephaestus.agent.settings;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import org.jspecify.annotations.Nullable;

/** Audit snapshot of which agent config powers a workspace capability. Null id means unbound. */
record AgentBindingSnapshot(@Nullable Long configId) implements ConfigAuditSnapshot {}
