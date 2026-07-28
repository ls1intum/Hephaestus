package de.tum.cit.aet.hephaestus.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class DataIntegrityViolationConstraintsTest extends BaseUnitTest {

    @Test
    void matchesNamedConstraintInCauseChain() {
        var cause = new ConstraintViolationException("duplicate", new SQLException(), "UK_PRACTICE_WORKSPACE_SLUG");
        var exception = new DataIntegrityViolationException("save failed", new RuntimeException(cause));

        assertThat(DataIntegrityViolationConstraints.hasName(exception, "uk_practice_workspace_slug")).isTrue();
    }

    @Test
    void rejectsDifferentOrUnnamedIntegrityViolations() {
        var cause = new ConstraintViolationException("duplicate", new SQLException(), "some_other_constraint");

        assertThat(
            DataIntegrityViolationConstraints.hasName(
                new DataIntegrityViolationException("save failed", cause),
                "uk_practice_workspace_slug"
            )
        ).isFalse();
        assertThat(
            DataIntegrityViolationConstraints.hasName(
                new DataIntegrityViolationException("save failed"),
                "uk_practice_workspace_slug"
            )
        ).isFalse();
    }
}
