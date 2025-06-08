package de.tum.in.www1.hephaestus;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(type = DatabaseType.H2)
class HephaestusApplicationTests {

    @Test
    void contextLoads() {
        // ContextLoads
        assert (true);
    }
}
