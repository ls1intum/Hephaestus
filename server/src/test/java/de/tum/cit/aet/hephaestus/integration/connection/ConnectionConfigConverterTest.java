package de.tum.cit.aet.hephaestus.integration.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins the sealed-type polymorphism round-trip through the JPA AttributeConverter.
 * The risk surface is Jackson's discriminator handling for sealed types: a regression
 * here would mean Hibernate writes a config row that no other JVM can read.
 */
class ConnectionConfigConverterTest extends BaseUnitTest {

    private final ConnectionConfigConverter converter = newConverter();

    private static ConnectionConfigConverter newConverter() {
        ConnectionConfigConverter c = new ConnectionConfigConverter();
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        c.setObjectMapper(mapper);
        return c;
    }

    @Test
    void roundTripsEverySubtype() {
        ConnectionConfig[] cases = new ConnectionConfig[] {
            new ConnectionConfig.GitHubAppConfig(42L, "ls1intum", null, Set.of("repository")),
            new ConnectionConfig.GitHubPatConfig("ls1intum", "https://github.example.com", Set.of()),
            new ConnectionConfig.GitLabConfig(
                "https://gitlab.lrz.de", 12345L, 999L,
                ConnectionConfig.GitLabConfig.SigningMode.WHSEC, Set.of("merge_request")),
            new ConnectionConfig.SlackConfig("T0001", "TUM", "C0001", "core-team", Set.of()),
            new ConnectionConfig.OutlineConfig("https://outline.example.com", "wks-ext", Set.of("document")),
        };
        for (ConnectionConfig original : cases) {
            String json = converter.convertToDatabaseColumn(original);
            assertThat(json).contains("\"type\"");
            ConnectionConfig back = converter.convertToEntityAttribute(json);
            assertThat(back).isEqualTo(original);
        }
    }

    @Test
    void nullPersistsAsNullAndReadsBack() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute("")).isNull();
        assertThat(converter.convertToEntityAttribute("  ")).isNull();
    }

    @Test
    void missingDiscriminatorThrows() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("{}"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ConnectionConfig");
    }

    @Test
    void unknownDiscriminatorThrows() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("{\"type\":\"BOGUS\"}"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ConnectionConfig");
    }

    @Test
    void preservesGitlabSigningModeRoundtrip() {
        ConnectionConfig.GitLabConfig plain = new ConnectionConfig.GitLabConfig(
            "https://gitlab.lrz.de", null, null,
            ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT, Set.of());
        String json = converter.convertToDatabaseColumn(plain);
        ConnectionConfig back = converter.convertToEntityAttribute(json);
        assertThat(back).isInstanceOf(ConnectionConfig.GitLabConfig.class);
        assertThat(((ConnectionConfig.GitLabConfig) back).signingMode())
            .isEqualTo(ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT);
    }
}
