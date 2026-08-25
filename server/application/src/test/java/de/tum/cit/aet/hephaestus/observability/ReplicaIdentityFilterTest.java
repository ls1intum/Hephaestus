package de.tum.cit.aet.hephaestus.observability;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
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
