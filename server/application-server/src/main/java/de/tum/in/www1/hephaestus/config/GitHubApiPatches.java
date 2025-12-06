package de.tum.in.www1.hephaestus.config;

import jakarta.annotation.PostConstruct;
import java.lang.instrument.Instrumentation;
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

        logger.info("Patched {}#getUser via Byte Buddy", className);
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
}
