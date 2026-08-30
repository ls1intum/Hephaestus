package de.tum.cit.aet.hephaestus.core.security;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DatabaseTransportGuardTest extends BaseUnitTest {

    private static MockEnvironment prod() {
        return new MockEnvironment().withProperty("spring.profiles.active", "prod");
    }

    @Test
    void shouldRejectPlaintextRemoteDatabaseInProduction() {
        var guard = new DatabaseTransportGuard(
                prod(), "jdbc:postgresql://db.example.com/hephaestus?sslmode=disable", false);

        assertThatThrownBy(guard::assertRemoteDatabaseUsesTls)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db.example.com")
                .hasMessageContaining("HEPHAESTUS_DATABASE_ALLOW_INSECURE_REMOTE");
    }

    @Test
    void shouldAllowVerifiedRemoteTls() {
        var guard = new DatabaseTransportGuard(
                prod(), "jdbc:postgresql://db.example.com/hephaestus?sslmode=verify-full", false);

        assertThatNoException().isThrownBy(guard::assertRemoteDatabaseUsesTls);
    }

    @Test
    void shouldAllowComposeDatabaseWithoutTls() {
        var guard =
                new DatabaseTransportGuard(prod(), "jdbc:postgresql://postgres:5432/hephaestus?sslmode=disable", false);

        assertThatNoException().isThrownBy(guard::assertRemoteDatabaseUsesTls);
    }

    @Test
    void shouldRejectWhenAnyDatabaseHostIsRemoteAndTlsIsOptional() {
        var guard = new DatabaseTransportGuard(
                prod(), "jdbc:postgresql://postgres,db.example.com/hephaestus?sslmode=prefer", false);

        assertThatThrownBy(guard::assertRemoteDatabaseUsesTls)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db.example.com");
    }
}
