package de.tum.in.www1.hephaestus;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@AutoConfigureEmbeddedDatabase
class HephaestusApplicationTests {

    @Test
    void contextLoads() {
        // ContextLoads
        assert (true);
    }
}
