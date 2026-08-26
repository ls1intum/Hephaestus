package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.config.ApplicationProperties;
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
        appendWhyAndSettingsLink(sb);
        return sb.toString();
    }

    String appendDisclosure(String sanitizedBody, AgentJob job) {
        var sb = new StringBuilder(sanitizedBody.length() + 420);
        sb.append(sanitizedBody).append("\n\n");
        appendMetadataFooter(sb, job);
        appendWhyAndSettingsLink(sb);
        return sb.toString();
    }

    String appendInlineFeedbackPrompt(String sanitizedBody) {
        var sb = new StringBuilder(sanitizedBody.length() + 120);
        sb.append(sanitizedBody).append("\n\n");
        sb.append("<sub>React with 👍 or 👎, or reply, to give feedback.</sub>\n");
        return sb.toString();
    }

    private void appendWhyAndSettingsLink(StringBuilder sb) {
        sb.append("<sub>[Why you're seeing this and how to stop it](").append(preferencesUrl).append(")</sub>\n");
    }

    private static void appendMetadataFooter(StringBuilder sb, AgentJob job) {
        sb.append("<sub>Practice review");

        String modelName = snapshotModelName(job.getConfigSnapshot());
        if (modelName != null && !modelName.isBlank()) {
            sb.append(" &middot; ").append(HtmlUtils.htmlEscape(modelName));
        }
        sb.append(
            " &middot; AI-generated and can be inaccurate. React with 👍 or 👎, or reply, to give feedback.</sub>\n"
        );
    }

    @Nullable
    private static String snapshotModelName(@Nullable JsonNode configSnapshot) {
        if (configSnapshot == null) {
            return null;
        }
        JsonNode model = configSnapshot.path("upstreamModelId");
        return model.isString() ? model.asString() : null;
    }
}
