package de.tum.in.www1.hephaestus.config;

import jakarta.annotation.PostConstruct;
import java.lang.instrument.Instrumentation;
import java.time.Instant;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Applies runtime patches to github-api to avoid offline exceptions on getUser().
 */
@Configuration
public class GitHubApiPatches {

    private static final Logger logger = LoggerFactory.getLogger(GitHubApiPatches.class);
    private static volatile boolean patchApplied;

    @PostConstruct
    public void applyPatches() {
        ensureApplied();
    }

    public static void ensureApplied() {
        if (patchApplied) {
            return;
        }
        synchronized (GitHubApiPatches.class) {
            if (patchApplied) {
                return;
            }
            try {
                installPatches();
                patchApplied = true;
            } catch (Throwable t) {
                logger.warn("Failed to apply Byte Buddy patches for github-api. Proceeding without patches.", t);
            }
        }
    }

    private static void installPatches() {
        Instrumentation instrumentation = ByteBuddyAgent.install();
        logger.info(
            "ByteBuddy instrumentation supports retransformation: {}",
            instrumentation.isRetransformClassesSupported()
        );

        patchGetUser(instrumentation, "org.kohsuke.github.GHPullRequestReview");
        patchGetUser(instrumentation, "org.kohsuke.github.GHPullRequestReviewComment");
        patchParseInstant(instrumentation);
    }

    private static void patchGetUser(Instrumentation instrumentation, String className) {
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .ignore(ElementMatchers.none())
            .type(ElementMatchers.named(className))
            .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                builder.visit(Advice.to(GetUserAdvice.class).on(ElementMatchers.named("getUser")))
            )
            .installOn(instrumentation);

        retransformClass(instrumentation, className);
        logger.info("Patched {}#getUser via Byte Buddy", className);
    }

    private static void patchParseInstant(Instrumentation instrumentation) {
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .ignore(ElementMatchers.none())
            .type(ElementMatchers.named("org.kohsuke.github.GitHubClient"))
            .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                builder.visit(
                    Advice.to(ParseInstantAdvice.class).on(
                        ElementMatchers.named("parseInstant")
                            .and(ElementMatchers.takesArguments(String.class))
                            .and(ElementMatchers.isStatic())
                    )
                )
            )
            .installOn(instrumentation);

        retransformClass(instrumentation, "org.kohsuke.github.GitHubClient");
        logger.info("Patched org.kohsuke.github.GitHubClient#parseInstant via Byte Buddy");
    }

    private static void retransformClass(Instrumentation instrumentation, String className) {
        if (instrumentation.isRetransformClassesSupported()) {
            try {
                Class<?> targetClass = Class.forName(className, false, GitHubApiPatches.class.getClassLoader());
                if (instrumentation.isModifiableClass(targetClass)) {
                    instrumentation.retransformClasses(targetClass);
                }
            } catch (ClassNotFoundException ignored) {
                // Class will be transformed on first load.
            } catch (UnsupportedOperationException | java.lang.instrument.UnmodifiableClassException ex) {
                logger.warn("Instrumentation cannot retransform {}: {}", className, ex.getMessage());
            }
        }
    }

    /**
     * Advice returning the embedded user whenever possible to avoid remote API calls.
     */
    public static class GetUserAdvice {

        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static GHUser enter(@Advice.This Object self) {
            try {
                var clazz = self.getClass();

                var userField = clazz.getDeclaredField("user");
                userField.setAccessible(true);
                GHUser user = (GHUser) userField.get(self);

                var ownerField = clazz.getDeclaredField("owner");
                ownerField.setAccessible(true);
                Object owner = ownerField.get(self);

                if (owner == null) {
                    return user;
                }

                Object offline = owner.getClass().getMethod("isOffline").invoke(owner);
                if (Boolean.TRUE.equals(offline)) {
                    return user;
                }

                if (user != null) {
                    return user;
                }
            } catch (Throwable t) {
                try {
                    var field = self.getClass().getDeclaredField("user");
                    field.setAccessible(true);
                    return (GHUser) field.get(self);
                } catch (Throwable ignored) {
                    return null;
                }
            }

            return null;
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(
            @Advice.Enter GHUser resolved,
            @Advice.Return(readOnly = false) GHUser returnValue,
            @Advice.Thrown(readOnly = false) Throwable throwable
        ) {
            if (resolved != null) {
                returnValue = resolved;
                throwable = null;
            }
        }
    }

    /**
     * Advice to normalize GitHub timestamps that occasionally arrive as epoch integers instead of ISO strings.
     */
    public static class ParseInstantAdvice {

        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static Instant enter(@Advice.Argument(0) String timestamp) {
            return tryParseEpochTimestamp(timestamp);
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(
            @Advice.Enter Instant parsed,
            @Advice.Return(readOnly = false) Instant returnValue,
            @Advice.Thrown(readOnly = false) Throwable throwable
        ) {
            if (parsed != null) {
                returnValue = parsed;
                throwable = null;
            }
        }

        public static Instant tryParseEpochTimestamp(String timestamp) {
            if (timestamp == null) {
                return null;
            }
            String trimmed = timestamp.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            int start = trimmed.startsWith("-") || trimmed.startsWith("+") ? 1 : 0;
            for (int i = start; i < trimmed.length(); i++) {
                if (!Character.isDigit(trimmed.charAt(i))) {
                    return null;
                }
            }
            try {
                long epochValue = Long.parseLong(trimmed);
                if (Math.abs(epochValue) >= 1_000_000_000_000L) {
                    return Instant.ofEpochMilli(epochValue);
                }
                return Instant.ofEpochSecond(epochValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }
}
