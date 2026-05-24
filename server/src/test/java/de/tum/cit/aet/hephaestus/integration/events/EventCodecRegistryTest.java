package de.tum.cit.aet.hephaestus.integration.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EventCodecRegistry longest-prefix-match")
class EventCodecRegistryTest extends BaseUnitTest {

    @Test
    void resolvesByLongestPrefix() {
        EventCodec generic = stubCodec(IntegrationKind.GITHUB, "com.hephaestus.github.");
        EventCodec specific = stubCodec(IntegrationKind.GITHUB, "com.hephaestus.github.pullrequest.");
        EventCodecRegistry registry = new EventCodecRegistry(List.of(generic, specific));

        assertThat(registry.find("com.hephaestus.github.pullrequest.created"))
            .contains(specific);
        assertThat(registry.find("com.hephaestus.github.issue.opened"))
            .contains(generic);
    }

    @Test
    void unknownTypeReturnsEmpty() {
        EventCodecRegistry registry = new EventCodecRegistry(List.of(
            stubCodec(IntegrationKind.GITHUB, "com.hephaestus.github.")
        ));
        assertThat(registry.find("com.example.unknown")).isEmpty();
        assertThat(registry.find("")).isEmpty();
        assertThat(registry.find(null)).isEmpty();
    }

    @Test
    void duplicatePrefixesFailFast() {
        EventCodec a = stubCodec(IntegrationKind.GITHUB, "com.hephaestus.github.");
        EventCodec b = stubCodec(IntegrationKind.GITHUB, "com.hephaestus.github.");
        assertThatThrownBy(() -> new EventCodecRegistry(List.of(a, b)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("duplicate EventCodec prefix");
    }

    private static EventCodec stubCodec(IntegrationKind kind, String prefix) {
        return new EventCodec() {
            @Override public IntegrationKind kind() { return kind; }
            @Override public String typePrefix() { return prefix; }
            @Override public Object decode(HephaestusCloudEvent event) { return event; }
            @Override public HephaestusCloudEvent encode(Object domainEvent, EncodeHints hints) {
                throw new UnsupportedOperationException("test stub");
            }
        };
    }
}
