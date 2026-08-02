package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.config.ApplicationProperties;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Component
class PracticeFeedbackCommentFormatter {

    private final String preferencesUrl;

    PracticeFeedbackCommentFormatter(ApplicationProperties applicationProperties) {
        this.preferencesUrl = UriComponentsBuilder.fromUriString(applicationProperties.webapp().url())
            .pathSegment("settings")
            .fragment("practice-feedback")
            .build()
            .encode()
            .toUriString();
    }

    String format(String sanitizedBody, AgentJob job) {
        var sb = new StringBuilder(sanitizedBody.length() + 640);
        sb.append(PullRequestCommentPoster.summaryMarkerFor(job)).append("\n");
        sb.append(sanitizedBody).append("\n\n");
        sb.append("---\n");
        appendMetadataFooter(sb, job);
        appendDeliverySettingsLink(sb);
        return sb.toString();
    }

    String appendSettingsNotice(String sanitizedBody) {
        var sb = new StringBuilder(sanitizedBody.length() + 180);
        sb.append(sanitizedBody).append("\n\n");
        appendDeliverySettingsLink(sb);
        return sb.toString();
    }

    private void appendDeliverySettingsLink(StringBuilder sb) {
        sb.append("<sub>[Manage comments and Slack reminders](").append(preferencesUrl).append(")</sub>\n");
    }

    private static void appendMetadataFooter(StringBuilder sb, AgentJob job) {
        sb.append("<sub>Hephaestus Agent");

        String modelName = snapshotModelName(job.getConfigSnapshot());
        if (modelName != null && !modelName.isBlank()) {
            sb.append(" &middot; ").append(HtmlUtils.htmlEscape(modelName));
        }
        if (job.getStartedAt() != null && job.getCompletedAt() != null) {
            sb.append(" &middot; ").append(formatDuration(Duration.between(job.getStartedAt(), job.getCompletedAt())));
        }

        sb.append("</sub>\n");
        sb.append("<sub>AI-generated feedback can be inaccurate. React with 👍 or 👎 to give feedback.</sub>\n");
    }

    @Nullable
    private static String snapshotModelName(@Nullable JsonNode configSnapshot) {
        if (configSnapshot == null) {
            return null;
        }
        JsonNode model = configSnapshot.path("upstreamModelId");
        return model.isString() ? model.asString() : null;
    }

    private static String formatDuration(Duration duration) {
        long totalSeconds = Math.max(0, duration.toSeconds());
        if (totalSeconds < 60) {
            return totalSeconds + "s";
        }
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes + "m " + seconds + "s";
    }
}
