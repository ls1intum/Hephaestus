package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import jakarta.persistence.Column;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class MembershipAbsorptionContractTest extends BaseUnitTest {
    @Test
    void shouldPreserveEveryMappedMembershipColumnDuringAbsorption() throws NoSuchMethodException {
        var method = MembershipRepository.class.getMethod("absorbAccount", long.class, long.class);
        var sql = Objects.requireNonNull(method.getAnnotation(Query.class)).value();
        var matcher = Pattern.compile("INSERT INTO membership\\s*\\(([^)]+)\\)").matcher(sql);
        assertThat(matcher.find()).isTrue();
        Set<String> inserted =
                Arrays.stream(matcher.group(1).split(",")).map(String::strip).collect(Collectors.toSet());
        Set<String> mapped = Stream.concat(
                        Arrays.stream(Membership.class.getDeclaredFields()),
                        Arrays.stream(Membership.Id.class.getDeclaredFields()))
                .map(field -> field.getAnnotation(Column.class))
                .filter(Objects::nonNull)
                .map(Column::name)
                .collect(Collectors.toSet());
        assertThat(inserted).containsExactlyInAnyOrderElementsOf(mapped);
    }
}
