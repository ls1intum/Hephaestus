package de.tum.cit.aet.hephaestus.testconfig;

import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtSigningKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@Tag("integration")
public abstract class RealAuthIntegrationTest {

    @Autowired
    private DatabaseTestUtils databaseTestUtils;

    @Autowired
    private JwtSigningKeyService signingKeyService;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        RealAuthDatasource.register(registry);
    }

    @BeforeEach
    void cleanDatabase() {
        databaseTestUtils.cleanDatabase();
        signingKeyService.ensureActiveKey();
    }
}
