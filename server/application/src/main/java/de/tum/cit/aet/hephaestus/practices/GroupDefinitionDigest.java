package de.tum.cit.aet.hephaestus.practices;

final class GroupDefinitionDigest {

    private GroupDefinitionDigest() {}

    static String digest(String slug, GroupDefinition definition) {
        return new CanonicalDigest()
                .add(slug)
                .add(definition.name())
                .addNullable(definition.description())
                .addNullable(definition.icon())
                .addNullable(definition.color())
                .hex();
    }
}
