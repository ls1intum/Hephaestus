package de.tum.in.www1.hephaestus.gitprovider.project;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for ProjectFieldValue entities.
 */
public interface ProjectFieldValueRepository extends JpaRepository<ProjectFieldValue, Long> {
    /**
     * Finds a field value by item ID and field ID.
     * This is the canonical lookup using the unique constraint fields.
     *
     * @param itemId the item's ID
     * @param fieldId the field's ID
     * @return the field value if found
     */
    Optional<ProjectFieldValue> findByItemIdAndFieldId(Long itemId, String fieldId);

    /**
     * Finds all field values for a given item.
     *
     * @param itemId the item's ID
     * @return list of field values for the item
     */
    @Query(
        """
        SELECT fv
        FROM ProjectFieldValue fv
        JOIN FETCH fv.field
        WHERE fv.item.id = :itemId
        """
    )
    List<ProjectFieldValue> findAllByItemIdWithField(@Param("itemId") Long itemId);

    /**
     * Finds all field values for a given field.
     *
     * @param fieldId the field's ID
     * @return list of field values for the field
     */
    List<ProjectFieldValue> findAllByFieldId(String fieldId);

    /**
     * Deletes all field values for an item that are not for fields in the given list.
     * Used during sync to remove values for fields that were cleared or no longer exist.
     *
     * @param itemId the item's ID
     * @param fieldIds the field IDs to keep
     * @return number of deleted values
     */
    @Modifying
    @Query("DELETE FROM ProjectFieldValue fv WHERE fv.item.id = :itemId AND fv.field.id NOT IN :fieldIds")
    int deleteByItemIdAndFieldIdNotIn(@Param("itemId") Long itemId, @Param("fieldIds") List<String> fieldIds);

    /**
     * Deletes all field values for an item.
     *
     * @param itemId the item's ID
     * @return number of deleted values
     */
    @Modifying
    @Query("DELETE FROM ProjectFieldValue fv WHERE fv.item.id = :itemId")
    int deleteAllByItemId(@Param("itemId") Long itemId);

    /**
     * Atomically inserts or updates a field value (race-condition safe).
     * <p>
     * Uses PostgreSQL's ON CONFLICT to handle concurrent inserts on the unique constraint
     * (item_id, field_id).
     *
     * @return 1 if inserted, 1 if updated (always 1 on success due to DO UPDATE)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(
        value = """
        INSERT INTO project_field_value (
            item_id, field_id, text_value, number_value, date_value,
            single_select_option_id, iteration_id, updated_at
        )
        VALUES (
            :itemId, :fieldId, :textValue, :numberValue, :dateValue,
            :singleSelectOptionId, :iterationId, :updatedAt
        )
        ON CONFLICT (item_id, field_id) DO UPDATE SET
            text_value = EXCLUDED.text_value,
            number_value = EXCLUDED.number_value,
            date_value = EXCLUDED.date_value,
            single_select_option_id = EXCLUDED.single_select_option_id,
            iteration_id = EXCLUDED.iteration_id,
            updated_at = EXCLUDED.updated_at
        """,
        nativeQuery = true
    )
    int upsertCore(
        @Param("itemId") Long itemId,
        @Param("fieldId") String fieldId,
        @Param("textValue") String textValue,
        @Param("numberValue") Double numberValue,
        @Param("dateValue") LocalDate dateValue,
        @Param("singleSelectOptionId") String singleSelectOptionId,
        @Param("iterationId") String iterationId,
        @Param("updatedAt") Instant updatedAt
    );
}
