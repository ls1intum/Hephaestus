package de.tum.cit.aet.hephaestus.integration.core.framework;

import de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandlerRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyCapability;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ReviewContractValidator {
    static final Map<CopyCapability, Capability> LANE_CAPABILITIES = Map.of(
            CopyCapability.SUMMARY,
            Capability.FEEDBACK_DELIVERY,
            CopyCapability.LINE_NOTE,
            Capability.INLINE_FEEDBACK,
            CopyCapability.CONVERSATION,
            Capability.FEEDBACK_DELIVERY);
    static final Set<CopyCapability> HEPHAESTUS_OWNED_LANES = Set.of(CopyCapability.PRACTICE_PAGE);
    private final ItemDescriptorRegistry descriptors;
    private final IntegrationMessageHandlerRegistry handlers;

    public ReviewContractValidator(ItemDescriptorRegistry descriptors, IntegrationMessageHandlerRegistry handlers) {
        this.descriptors = descriptors;
        this.handlers = handlers;
    }

    public List<String> validateDescriptors() {
        List<String> violations = new ArrayList<>();
        for (ItemDescriptor descriptor : descriptors.all()) {
            if (descriptor.recordSchemaResource().isBlank())
                violations.add(descriptor.kind() + " has no record schema");
            for (ItemEventDefinition event : descriptor.events())
                if (!event.name().value().startsWith(descriptor.kind().value() + "."))
                    violations.add("Event belongs to another item kind: " + event.name());
            if (descriptor.events().stream()
                            .map(ItemEventDefinition::name)
                            .distinct()
                            .count()
                    != descriptor.events().size()) violations.add("Duplicate item event: " + descriptor.kind());
        }
        return violations;
    }

    public List<String> validateContribution(IntegrationManifest manifest) {
        List<String> violations = new ArrayList<>();
        var contribution = manifest.reviewContribution();
        for (var kind : contribution.observes())
            if (descriptors.descriptorFor(kind).isEmpty()) violations.add("Unknown item kind: " + kind);
        contribution.raises().forEach((kind, events) -> {
            if (!contribution.observes().contains(kind))
                violations.add("Integration raises events without observing " + kind);
            for (var name : events) {
                var declaration = descriptors.descriptorFor(kind).stream()
                        .flatMap(d -> d.events().stream())
                        .filter(e -> e.name().equals(name))
                        .findFirst();
                if (declaration.isEmpty()) {
                    violations.add("Undeclared item event: " + name);
                    continue;
                }
                var provenance = declaration.get().producedBy().stream()
                        .filter(k -> k.kind() == manifest.kind())
                        .toList();
                if (provenance.isEmpty()) violations.add("Missing event provenance: " + name);
                for (var key : provenance)
                    if (handlers.resolve(key).isEmpty()) violations.add("Missing event handler: " + key);
            }
        });
        contribution.delivers().forEach((kind, copies) -> {
            var descriptor = descriptors.descriptorFor(kind);
            if (!contribution.observes().contains(kind))
                violations.add("Integration delivers without observing " + kind);
            for (var copy : copies) {
                if (HEPHAESTUS_OWNED_LANES.contains(copy)) violations.add("Integration claims local delivery: " + copy);
                if (descriptor.isEmpty() || !descriptor.get().copyCapabilities().contains(copy))
                    violations.add("Unsupported copy capability: " + copy);
                Capability required = LANE_CAPABILITIES.get(copy);
                if (required != null && !manifest.declaredCapabilities().contains(required))
                    violations.add("Missing delivery capability: " + required);
            }
        });
        return violations;
    }
}
