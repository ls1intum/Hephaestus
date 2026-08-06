package de.tum.cit.aet.hephaestus.integration.scm.domain.signal;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.SignalVocabulary;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The SCM domain's answer to "what does this stored trigger-event literal mean?".
 *
 * <p>A bean rather than a static call so the practices module can ask without importing anything of the
 * SCM domain. That direction is the whole contract: a domain publishes its vocabulary, and the module
 * that binds practices to it never learns which domains exist.
 */
@Component
public class ScmSignalVocabulary implements SignalVocabulary {

    @Override
    public Optional<SignalName> signalForTriggerEvent(String triggerEventName) {
        return ScmSignals.forTriggerEvent(triggerEventName);
    }

    @Override
    public Set<String> triggerEventNames() {
        return ScmSignals.triggerEventNames();
    }
}
