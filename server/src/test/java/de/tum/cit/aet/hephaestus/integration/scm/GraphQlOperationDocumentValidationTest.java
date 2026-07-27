package de.tum.cit.aet.hephaestus.integration.scm;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.graphql.FragmentMergingDocumentSource;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import graphql.ExecutionInput;
import graphql.GraphQLError;
import graphql.ParseAndValidate;
import graphql.ParseAndValidateResult;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import graphql.validation.ValidationError;
import graphql.validation.ValidationErrorType;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Validates every committed GitHub and GitLab operation document against that vendor's checked-in schema.
 *
 * <p>Why this exists: a malformed operation document fails at the vendor's API with a GraphQL error, and every
 * caller in this module treats a GraphQL error as an unknown/skip outcome. A typo therefore degrades to
 * behaviour indistinguishable from a healthy fail-closed path — a document can be broken from the day it is
 * written and no unit test, whose responses are mocked, will ever notice. This test is the mechanism that
 * notices: it runs the real GraphQL validator (the same {@code graphql-java} rules the vendors run) over the
 * exact document text the client sends, so an unknown field, a wrong argument name, a mistyped variable or a
 * missing fragment fails the build instead of silently disabling a feature in production.
 *
 * <p>The document text is assembled by {@link FragmentMergingDocumentSource} — the production document source —
 * so fragment resolution is validated too, not bypassed.
 *
 * <p>Scope of the guarantee: this proves each document is valid <em>against the checked-in schema</em>. It
 * cannot prove the schema is current; a schema refresh is what catches vendor-side deprecation.
 */
class GraphQlOperationDocumentValidationTest extends BaseUnitTest {

    /** Vendor directory layout under {@code src/main/resources/graphql/}. */
    private record Vendor(String name, String schema, String operations, String fragments) {}

    private static final List<Vendor> VENDORS = List.of(
        new Vendor(
            "github",
            "graphql/github/schema.github.graphql",
            "graphql/github/operations/",
            "graphql/github/fragments/ProjectFragments.graphql"
        ),
        new Vendor(
            "gitlab",
            "graphql/gitlab/schema.gitlab.graphql",
            "graphql/gitlab/operations/",
            "graphql/gitlab/fragments/GitLabUserFields.graphql"
        )
    );

    static Stream<Arguments> operationDocuments() {
        List<Arguments> arguments = new ArrayList<>();
        for (Vendor vendor : VENDORS) {
            GraphQLSchema schema = loadSchema(vendor.schema());
            FragmentMergingDocumentSource documentSource = new FragmentMergingDocumentSource(
                List.of(new ClassPathResource(vendor.operations()), new ClassPathResource(vendor.fragments())),
                List.of(".graphql", ".gql"),
                List.of(new ClassPathResource(vendor.fragments()))
            );
            for (String name : operationNames(vendor.operations())) {
                arguments.add(Arguments.of(vendor.name() + "/" + name, schema, documentSource, name));
            }
        }
        return arguments.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operationDocuments")
    void operationDocumentIsValidAgainstTheVendorSchema(
        String label,
        GraphQLSchema schema,
        FragmentMergingDocumentSource documentSource,
        String documentName
    ) {
        String documentText = documentSource.getDocument(documentName).block();
        assertThat(documentText).as("document %s could not be loaded", label).isNotNull();

        ParseAndValidateResult result = ParseAndValidate.parseAndValidate(
            schema,
            ExecutionInput.newExecutionInput(documentText).build()
        );

        assertThat(result.getSyntaxException())
            .as("%s failed to parse: %s", label, result.getSyntaxException())
            .isNull();
        List<GraphQLError> errors = result
            .getValidationErrors()
            .stream()
            .filter(error -> !isAcceptedByTheVendor(error))
            .map(GraphQLError.class::cast)
            .toList();
        assertThat(errors).as("%s is invalid against the checked-in schema: %s", label, describe(errors)).isEmpty();
    }

    /**
     * The one validation rule this project's documents deliberately fail, because GitHub's own server does not
     * enforce it.
     *
     * <p>{@code RequestedReviewerFields} selects {@code name} on both {@code User} ({@code String}) and
     * {@code Team} ({@code String!}) inside one union selection. GraphQL §5.3.2 SameResponseShape says two
     * fields sharing a response key must share nullability, and graphql-java enforces it; GitHub does not —
     * the affected documents were executed against api.github.com and ran past static validation (to data, or
     * to a field-level scope error) with no validation error. Failing the build on a rule the target API
     * waives would make the guard cry wolf.
     *
     * <p>Scoped to that exact conflict — the {@code RequestedReviewer} union reached either through a
     * {@code reviewers} connection or a single {@code requestedReviewer}. Any other {@code FieldsConflict},
     * and any conflict on another path, still fails.
     */
    private static boolean isAcceptedByTheVendor(ValidationError error) {
        if (error.getValidationErrorType() != ValidationErrorType.FieldsConflict) {
            return false;
        }
        String message = error.getMessage();
        return (
            message.endsWith("fields have different nullability shapes") &&
            (message.contains("/reviewers/nodes/name'") || message.contains("/requestedReviewer/name'"))
        );
    }

    /**
     * Guards the guard: if the operations directory ever stops being discovered, the parameterised test above
     * would pass vacuously with zero cases. Both vendors must contribute documents.
     */
    @Test
    void everyVendorContributesDocuments() {
        for (Vendor vendor : VENDORS) {
            assertThat(operationNames(vendor.operations()))
                .as("no operation documents found for %s", vendor.name())
                .isNotEmpty();
        }
    }

    private static String describe(List<? extends GraphQLError> errors) {
        return errors.stream().map(GraphQLError::getMessage).toList().toString();
    }

    private static GraphQLSchema loadSchema(String schemaLocation) {
        // An unexecutable schema needs no data fetchers or scalar implementations — validation only reads the
        // type system, which is exactly what a client-side document check should depend on.
        TypeDefinitionRegistry registry = new SchemaParser().parse(read(new ClassPathResource(schemaLocation)));
        return UnExecutableSchemaGenerator.makeUnExecutableSchema(registry);
    }

    private static List<String> operationNames(String operationsLocation) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(
                "classpath:" + operationsLocation + "*.graphql"
            );
            return Stream.of(resources)
                .map(Resource::getFilename)
                .filter(Objects::nonNull)
                .map(filename -> filename.substring(0, filename.lastIndexOf('.')))
                .sorted(Comparator.naturalOrder())
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
