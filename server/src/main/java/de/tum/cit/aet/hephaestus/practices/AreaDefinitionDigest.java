package de.tum.cit.aet.hephaestus.practices;

/** Identity of a whole area definition. */
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
