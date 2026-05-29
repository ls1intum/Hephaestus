package de.tum.cit.aet.hephaestus.config;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

/**
 * Teaches SpringDoc/swagger-core to derive OpenAPI {@code required} from JSpecify
 * {@link org.jspecify.annotations.NonNull}.
 *
 * <p>swagger-core natively recognises the (now deprecated) {@code org.springframework.lang.@NonNull}
 * to mark schema properties {@code required}, but does <em>not</em> understand JSpecify
 * (<a href="https://github.com/springdoc/springdoc-openapi/issues/3110">springdoc#3110</a>). After
 * migrating the codebase to JSpecify, this converter restores the exact same contract: a property
 * whose record component / field carries {@code @NonNull} (a {@code TYPE_USE} annotation, read off
 * {@link java.lang.reflect.AnnotatedType}) is added to the model's {@code required} list. Bare and
 * {@code @Nullable} members stay optional — identical to the prior behaviour.
 *
 * <p>Registered automatically: SpringDoc wires every {@link ModelConverter} bean into swagger-core.
 * Scoped to {@code de.tum.cit.aet.hephaestus} types so third-party models are untouched.
 */
@Component
public class JSpecifyRequiredModelConverter implements ModelConverter {

    private static final String BASE_PACKAGE = "de.tum.cit.aet.hephaestus";

    @Override
    public Schema<?> resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Schema<?> resolved = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
        if (resolved == null) {
            return null;
        }
        Class<?> raw = rawClass(type.getType());
        if (raw == null || !raw.getName().startsWith(BASE_PACKAGE)) {
            return resolved;
        }
        // The resolved schema may be the object model (properties present) or a $ref to a model
        // swagger-core just defined. Locate the actual model with properties either way.
        Schema<?> model = resolved.getProperties() != null ? resolved : lookupModel(resolved, raw, context);
        if (model == null || model.getProperties() == null) {
            return resolved;
        }
        for (String name : nonNullMembers(raw)) {
            if (model.getProperties().containsKey(name) && !isAlreadyRequired(model, name)) {
                model.addRequiredItem(name);
            }
        }
        return resolved;
    }

    private static Schema<?> lookupModel(Schema<?> resolved, Class<?> raw, ModelConverterContext context) {
        var defined = context.getDefinedModels();
        if (defined == null) {
            return null;
        }
        if (resolved.get$ref() != null) {
            String ref = resolved.get$ref();
            Schema<?> byRef = defined.get(ref.substring(ref.lastIndexOf('/') + 1));
            if (byRef != null) {
                return byRef;
            }
        }
        return defined.get(raw.getSimpleName());
    }

    private static boolean isAlreadyRequired(Schema<?> schema, String name) {
        return schema.getRequired() != null && schema.getRequired().contains(name);
    }

    /** Names of record components / fields carrying JSpecify {@code @NonNull} on their type. */
    private static List<String> nonNullMembers(Class<?> raw) {
        List<String> names = new ArrayList<>();
        if (raw.isRecord()) {
            for (RecordComponent rc : raw.getRecordComponents()) {
                if (
                    hasNonNull(rc.getAnnotatedType().getDeclaredAnnotations()) || rc.isAnnotationPresent(NonNull.class)
                ) {
                    names.add(rc.getName());
                }
            }
        } else {
            for (Class<?> c = raw; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    if (
                        hasNonNull(f.getAnnotatedType().getDeclaredAnnotations()) ||
                        f.isAnnotationPresent(NonNull.class)
                    ) {
                        names.add(f.getName());
                    }
                }
            }
        }
        return names;
    }

    private static boolean hasNonNull(java.lang.annotation.Annotation[] annotations) {
        for (java.lang.annotation.Annotation a : annotations) {
            if (a.annotationType() == NonNull.class) {
                return true;
            }
        }
        return false;
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) {
            return c;
        }
        // swagger-core hands nested property types through as a Jackson JavaType (e.g. SimpleType),
        // not a java.lang.Class. Top-level types arrive as Class, nested ones as JavaType.
        if (type instanceof com.fasterxml.jackson.databind.JavaType jt) {
            return jt.getRawClass();
        }
        return null;
    }
}
