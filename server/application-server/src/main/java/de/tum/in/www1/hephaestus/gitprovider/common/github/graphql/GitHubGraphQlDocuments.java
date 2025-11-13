package de.tum.in.www1.hephaestus.gitprovider.common.github.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Loads GraphQL documents from the classpath and caches them in-memory.
 */
@Component
public class GitHubGraphQlDocuments {

    private final ResourceLoader resourceLoader;
    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();

    public GitHubGraphQlDocuments(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String load(String location) {
        return cache.computeIfAbsent(location, this::readDocument);
    }

    private String readDocument(String location) {
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                throw new IllegalArgumentException("GraphQL document not found: " + location);
            }
            try (InputStream inputStream = resource.getInputStream()) {
                return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load GraphQL document: " + location, e);
        }
    }
}