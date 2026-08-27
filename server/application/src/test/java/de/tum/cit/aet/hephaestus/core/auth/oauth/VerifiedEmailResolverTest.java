package de.tum.cit.aet.hephaestus.core.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Pins the email-verification trust model: an email is VERIFIED only on explicit IdP attestation
 * (OIDC {@code email_verified==true} or GitHub's injected {@code email_verified} attribute).
 */
class VerifiedEmailResolverTest extends BaseUnitTest {

    private final VerifiedEmailResolver resolver = new VerifiedEmailResolver();

    private static OAuth2User oauth2User(Map<String, Object> attrs) {
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attrs, "id");
    }

    private static OAuth2User oidcUser(Map<String, Object> claims) {
        OidcIdToken idToken = OidcIdToken.withTokenValue("tok")
            .subject("sub-1")
            .claims(c -> c.putAll(claims))
            .build();
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
    }

    @Test
    void gitlabOidc_emailVerifiedTrue_returnsVerified() {
        OAuth2User user = oidcUser(Map.of("sub", "sub-1", "email", "a@b.de", "email_verified", true));

        VerifiedEmailResolver.ResolvedEmail r = resolver.resolve("gitlab-lrz", user);

        assertThat(r.email()).isEqualTo("a@b.de");
        assertThat(r.verified()).isTrue();
    }

    @Test
    void gitlabOidc_emailVerifiedFalse_returnsUnverified() {
        OAuth2User user = oidcUser(Map.of("sub", "sub-1", "email", "a@b.de", "email_verified", false));

        assertThat(resolver.resolve("gitlab-lrz", user).verified()).isFalse();
    }

    @Test
    void gitlabOidc_emailVerifiedAbsent_returnsUnverified() {
        OAuth2User user = oidcUser(Map.of("sub", "sub-1", "email", "a@b.de"));

        assertThat(resolver.resolve("gitlab-lrz", user).verified()).isFalse();
    }

    @Test
    void github_injectedVerifiedAttribute_returnsVerified() {
        OAuth2User user = oauth2User(Map.of("id", 7, "email", "p@x.de", "email_verified", true));

        VerifiedEmailResolver.ResolvedEmail r = resolver.resolve("github", user);

        assertThat(r.email()).isEqualTo("p@x.de");
        assertThat(r.verified()).isTrue();
    }

    @Test
    void github_plainEmailNoVerifiedAttr_returnsUnverified() {
        OAuth2User user = oauth2User(Map.of("id", 7, "email", "p@x.de"));

        assertThat(resolver.resolve("github", user).verified()).isFalse();
    }

    @Test
    void nullEmail_neverVerified() {
        OAuth2User user = oauth2User(Map.of("id", 7, "email_verified", true));

        VerifiedEmailResolver.ResolvedEmail r = resolver.resolve("github", user);
        assertThat(r.email()).isNull();
        assertThat(r.verified()).isFalse();
    }
}
