package de.tum.in.www1.hephaestus.observability;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ReplicaIdentityFilterTest extends BaseUnitTest {

    @Test
    void setsReplicaHeader() throws Exception {
        var response = new MockHttpServletResponse();
        new ReplicaIdentityFilter().doFilter(new MockHttpServletRequest(), response, (req, res) -> {});
        assertThat(response.getHeader(ReplicaIdentityFilter.HEADER_NAME)).isNotBlank();
    }
}
