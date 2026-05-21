package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Default-locale case folding corrupts ASCII on Turkish-locale JVMs (dotted-i bug). The webhook
 * subject grammar must never depend on locale, so every case-fold inside the package must pass
 * {@code Locale.ROOT} explicitly, and {@link Locale#getDefault()} is banned.
 */
@Tag("architecture")
class LocaleSafetyArchTest extends HephaestusArchitectureTest {

    private static final String WEBHOOK_PACKAGE = "..gitprovider.webhook..";

    @Test
    void noNakedToLowerCase() {
        rejectNakedCaseFold("toLowerCase").check(classes);
    }

    @Test
    void noNakedToUpperCase() {
        rejectNakedCaseFold("toUpperCase").check(classes);
    }

    @Test
    void noLocaleGetDefault() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage(WEBHOOK_PACKAGE)
            .should()
            .callMethod(Locale.class, "getDefault")
            .because("Locale.getDefault() threads JVM-default locale into case-folds — use Locale.ROOT");
        rule.check(classes);
    }

    private static ArchRule rejectNakedCaseFold(String methodName) {
        return noClasses()
            .that()
            .resideInAPackage(WEBHOOK_PACKAGE)
            .should()
            .callMethod(String.class, methodName)
            .because("Default-locale " + methodName + "() — use " + methodName + "(Locale.ROOT)");
    }
}
