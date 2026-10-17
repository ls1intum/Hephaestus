package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class ItemKindsAgreementTest extends BaseUnitTest {
    @Test
    void itemKindsAreOpenDomainNames() {
        assertThat(new ItemKind("scm.file").value()).isEqualTo("scm.file");
        assertThat(new ItemKind("example.change").value()).isEqualTo("example.change");
    }

    @Test
    void sourceAndParentIdentityAreIndependentFromProviderLabels() {
        var ref = new ItemRef(1, 9, new ItemKind("scm.file"), null, 3L, "src/Main.java");
        assertThat(ref.sourceId()).isNull();
        assertThat(ref.parentId()).isEqualTo(3L);
        assertThat(ref.path()).isEqualTo("src/Main.java");
    }
}
