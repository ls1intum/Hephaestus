package de.tum.cit.aet.hephaestus.evidence;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/** Reject unknown fields and scalar coercion at JSON trust boundaries. */
public final class StrictJson {
    private StrictJson() {}

    public static JsonMapper.Builder configure(JsonMapper.Builder builder) {
        return builder.enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .enable(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .withCoercionConfig(
                        LogicalType.Textual,
                        coercion -> coercion.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}
