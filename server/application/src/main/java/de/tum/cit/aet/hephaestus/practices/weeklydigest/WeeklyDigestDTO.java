package de.tum.cit.aet.hephaestus.practices.weeklydigest;

import de.tum.cit.aet.hephaestus.practices.profile.dto.ProfileChangeDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * A weekly digest as it reads now. Feedback that can no longer be shown is left out, and the count of unread Practice
 * page feedback is the one the Practice page shows. Facts only: the app writes the sentences.
 */
@Schema(description = "A weekly digest as it reads now, from the live facts")
public record WeeklyDigestDTO(
        @NonNull @Schema(description = "The Monday the week starts on, in the developer's time zone")
        LocalDate weekStart,

        @NonNull @Schema(description = "Monday 00:00 of the week in the developer's time zone")
        Instant windowStart,

        @NonNull @Schema(description = "The next Monday 00:00 in the developer's time zone")
        Instant windowEnd,

        @NonNull @Schema(description = "The IANA time zone the week was read in")
        String timeZone,

        @NonNull
        @Schema(
                description = "Feedback delivered to the developer on the work or in conversation in the week, by"
                        + " practice group and practice order")
        List<WeeklyDigestFeedbackDTO> feedback,

        @NonNull
        @Schema(
                description = "What changed on the Practice profile in the week; never a change of Practice page"
                        + " feedback, which the waiting count covers")
        List<ProfileChangeDTO> changes,

        @Schema(
                requiredMode = RequiredMode.REQUIRED,
                description = "Pieces of Practice page feedback prepared in the week that still wait unread now")
        int waiting) {}
