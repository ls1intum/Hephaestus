package de.tum.in.www1.hephaestus.gitprovider.common;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;

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
            return loadPayload(fileName, parameterType.asSubclass(GHEventPayload.class));
        } catch (Exception e) {
            throw new ParameterResolutionException("Failed to load GitHub payload", e);
        }
    }

    private <T extends GHEventPayload> T loadPayload(String fileName, Class<T> payloadType) throws Exception {
        String jsonPayload = Files.readString(Paths.get("src/test/resources/github/" + fileName));
        return GitHub.offline().parseEventPayload(new StringReader(jsonPayload), payloadType);
    }
}
