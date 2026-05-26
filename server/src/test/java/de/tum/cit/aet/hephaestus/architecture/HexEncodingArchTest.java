package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link java.util.HexFormat#of()} is the only approved hex source inside {@code integration.webhook}.
 * {@code Integer/Long.toHexString} drop leading zeros (e.g. {@code 0x0a → "a"}), silently
 * corrupting dedup IDs and HMAC signatures.
 */
@Tag("architecture")
class HexEncodingArchTest extends HephaestusArchitectureTest {

    private static final String WEBHOOK_PACKAGE = "..integration.webhook..";

    @Test
    void noIntegerToHexStringCall() {
        rejectToHexString(Integer.class, int.class).check(classes);
    }

    @Test
    void noLongToHexStringCall() {
        rejectToHexString(Long.class, long.class).check(classes);
    }

    private static ArchRule rejectToHexString(Class<?> boxed, Class<?> primitive) {
        return noClasses()
            .that()
            .resideInAPackage(WEBHOOK_PACKAGE)
            .should()
            .callMethod(boxed, "toHexString", primitive)
            .because(boxed.getSimpleName() + ".toHexString drops leading zeros — use HexFormat.of()");
    }
}
