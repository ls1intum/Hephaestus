package de.tum.in.www1.hephaestus.gitprovider.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.config.GitHubApiPatches;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;

/**
 * JUnit 5 extension that loads real GitHub webhook JSON files into test methods.
 * Automatically parses JSON to strongly-typed GHEventPayload objects.
 */
public class GitHubPayloadExtension implements ParameterResolver {

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.isAnnotated(GitHubPayload.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
        throws ParameterResolutionException {
        GitHubPayload annotation = parameterContext.findAnnotation(GitHubPayload.class).orElseThrow();
        Class<?> parameterType = parameterContext.getParameter().getType();

        try {
            String fileName = annotation.value() + ".json";
            if (GHEventPayload.class.isAssignableFrom(parameterType)) {
                return loadGhPayload(fileName, parameterType.asSubclass(GHEventPayload.class));
            }
            return loadPojoPayload(fileName, parameterType);
        } catch (Exception e) {
            throw new ParameterResolutionException("Failed to load GitHub payload", e);
        }
    }

    private <T extends GHEventPayload> T loadGhPayload(String fileName, Class<T> payloadType) throws IOException {
        String resourcePath = "github/" + fileName;
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("GitHub payload not found on classpath: " + resourcePath);
            }
            String jsonPayload = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            GitHubApiPatches.ensureApplied();
            try (StringReader reader = new StringReader(jsonPayload)) {
                return GitHub.offline().parseEventPayload(reader, payloadType);
            }
        }
    }

    private Object loadPojoPayload(String fileName, Class<?> payloadType) throws IOException {
        String resourcePath = "github/" + fileName;
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("GitHub payload not found on classpath: " + resourcePath);
            }
            String jsonPayload = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(jsonPayload, payloadType);
        }
    }
}
