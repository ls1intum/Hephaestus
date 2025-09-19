package de.tum.in.www1.hephaestus.config;

import jakarta.annotation.PostConstruct;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Applies runtime patches to github-api to avoid offline exceptions on getUser().
 */
@Configuration
public class GitHubApiPatches {

    private static final Logger logger = LoggerFactory.getLogger(GitHubApiPatches.class);

    @PostConstruct
    public void applyPatches() {
        try {
            ByteBuddyAgent.install();

            patchGetUser("org.kohsuke.github.GHPullRequestReview");
            patchGetUser("org.kohsuke.github.GHPullRequestReviewComment");
        } catch (Throwable t) {
            logger.warn("Failed to apply Byte Buddy patches for github-api. Proceeding without patches.", t);
        }
    }

    private void patchGetUser(String className) throws Exception {
        Class<?> target = Class.forName(className);
        new ByteBuddy()
            .redefine(target)
            .method(ElementMatchers.named("getUser"))
            .intercept(MethodDelegation.to(GetUserInterceptor.class))
            .make()
            .load(target.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());

        logger.info("Patched {}#getUser via Byte Buddy", className);
    }

    /**
     * Interceptor implementing: return owner == null || owner.isOffline() ? user : owner.root().getUser(user.login)
     */
    public static class GetUserInterceptor {

        @RuntimeType
        public static Object intercept(@This Object self) throws Exception {
            try {
                // Reflectively access fields: owner, user
                var clazz = self.getClass();
                var ownerField = clazz.getDeclaredField("owner");
                ownerField.setAccessible(true);
                Object owner = ownerField.get(self);

                var userField = clazz.getDeclaredField("user");
                userField.setAccessible(true);
                Object user = userField.get(self);

                if (owner == null) {
                    return user;
                }

                // owner has isOffline()
                var isOfflineMethod = owner.getClass().getMethod("isOffline");
                boolean isOffline = (Boolean) isOfflineMethod.invoke(owner);
                if (isOffline) {
                    return user;
                }

                if (user == null) {
                    return null;
                }

                // user has login field (package-private), fallback to getLogin()
                String login = null;
                try {
                    var loginField = user.getClass().getDeclaredField("login");
                    loginField.setAccessible(true);
                    Object v = loginField.get(user);
                    login = v != null ? v.toString() : null;
                } catch (NoSuchFieldException ignore) {
                    try {
                        var getLogin = user.getClass().getMethod("getLogin");
                        Object v = getLogin.invoke(user);
                        login = v != null ? v.toString() : null;
                    } catch (ReflectiveOperationException ex) {
                        // ignore
                    }
                }

                if (login == null) {
                    return user;
                }

                // owner.root().getUser(login)
                var rootMethod = owner.getClass().getMethod("root");
                Object root = rootMethod.invoke(owner);
                if (root == null) {
                    return user;
                }
                var getUserMethod = root.getClass().getMethod("getUser", String.class);
                return getUserMethod.invoke(root, login);
            } catch (Throwable t) {
                // On any unexpected error, return the embedded user to be safe
                try {
                    var userField = self.getClass().getDeclaredField("user");
                    userField.setAccessible(true);
                    return userField.get(self);
                } catch (Throwable inner) {
                    return null;
                }
            }
        }
    }
}
