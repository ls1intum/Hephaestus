package de.tum.in.www1.hephaestus;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import org.springframework.modulith.docs.Documenter.DiagramOptions;
import org.springframework.modulith.docs.Documenter.DiagramOptions.DiagramStyle;

public class HephaestusModulithTests {

    ApplicationModules modules = ApplicationModules.of(Application.class);

    @Test
    void shouldBeCompliant() {
        modules.forEach(System.out::println);
        modules.verify();
    }

    @Test
    void writeDocumentationSnippets() {
        new Documenter(modules)
                .writeModuleCanvases()
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml(DiagramOptions.defaults().withStyle(DiagramStyle.UML));
    }

}
