package de.tum.cit.aet.hephaestus.core.auth;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@code apiBasePath} feeds string-concatenated OAuth URLs ({@code {baseUrl}} + path + callback), so a
 * stray missing/duplicated slash silently breaks login. Pin the constructor normalization that makes
 * {@code api}, {@code /api} and {@code /api/} equivalent and collapses blank/{@code /} to root.
 */
class AuthPropertiesTest extends BaseUnitTest {

    @ParameterizedTest
    @CsvSource(
        value = {
            "/api | /api",
            "api | /api",
            "//api | /api",
            "/api/ | /api",
            "/api/v2/ | /api/v2",
            "'' | ''",
            "/ | ''",
            "'  /api/  ' | /api",
        },
        delimiterString = "|",
        emptyValue = ""
    )
    void apiBasePath_isNormalizedToLeadingSlashNoTrailingSlash(String raw, String expected) {
        assertThat(AuthPropertiesFixture.withApiBasePath(raw).apiBasePath()).isEqualTo(expected);
    }
}
