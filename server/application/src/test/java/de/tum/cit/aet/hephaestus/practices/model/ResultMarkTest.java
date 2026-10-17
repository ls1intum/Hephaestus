package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@NullMarked
@Tag("unit")
class ResultMarkTest {

    private static final Instant CREATED = Instant.parse("2026-10-14T12:00:00Z");

    @Test
    void shouldPreserveFirstRemovalWhenMarkIsRemovedAgain() {
        ResultMark mark = mark(ResultMarkKind.ACCEPTED);
        Instant removed = CREATED.plusSeconds(1);
        mark.remove(4, "No longer accepted", removed);
        mark.remove(5, "Later request", CREATED.plusSeconds(2));
        assertThat(mark.getRemovedAt()).isEqualTo(removed);
        assertThat(mark.getRemovedById()).isEqualTo(4L);
        assertThat(mark.getRemovalReason()).isEqualTo("No longer accepted");
        assertThat(mark.getKind()).isEqualTo(ResultMarkKind.ACCEPTED);
        assertThat(mark.getCreatedAt()).isEqualTo(CREATED);
    }

    @Test
    void shouldRefuseBlankReasonWhenRemovingMark() {
        ResultMark mark = mark(ResultMarkKind.INCORRECT);
        assertThatThrownBy(() -> mark.remove(4, "   ", CREATED.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(mark.getRemovedAt()).isNull();
    }

    @Test
    void shouldRefuseRemovalBeforeCreation() {
        ResultMark mark = mark(ResultMarkKind.INCORRECT);
        assertThatThrownBy(() -> mark.remove(4, "Correction", CREATED.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(mark.getRemovedAt()).isNull();
    }

    private static ResultMark mark(ResultMarkKind kind) {
        return ResultMark.builder()
                .id(UUID.randomUUID())
                .workspaceId(1)
                .resultId(UUID.randomUUID())
                .kind(kind)
                .reason("Recorded reason")
                .createdById(3L)
                .createdAt(CREATED)
                .build();
    }
}
