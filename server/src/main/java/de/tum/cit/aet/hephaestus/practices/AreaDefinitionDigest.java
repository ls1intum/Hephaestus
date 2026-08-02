package de.tum.cit.aet.hephaestus.practices;

final class AreaDefinitionDigest {

    private AreaDefinitionDigest() {}

    static String digest(String slug, AreaDefinition definition) {
        return new CanonicalDigest()
            .add(slug)
            .add(definition.name())
            .addNullable(definition.description())
            .addNullable(definition.icon())
            .addNullable(definition.color())
            .hex();
    }
}
