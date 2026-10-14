package de.tum.cit.aet.hephaestus.practices;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PracticeReviewDigest {
    private final JsonMapper mapper;

    @PersistenceContext
    private @Nullable EntityManager entityManager;

    public String of(PracticeDefinition definition) {
        return of(mapper.valueToTree(definition));
    }

    public String of(JsonNode definition) {
        return (String) Objects.requireNonNull(entityManager)
                .createNativeQuery("SELECT practice_review_digest(CAST(:definition AS jsonb))", String.class)
                .setParameter("definition", mapper.writeValueAsString(definition))
                .getSingleResult();
    }
}
