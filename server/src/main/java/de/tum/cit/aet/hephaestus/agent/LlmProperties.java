package de.tum.cit.aet.hephaestus.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Instance-wide LLM knobs, bound from {@code hephaestus.llm.*}.
 *
 * @param displayCurrency the code USD spend figures are additionally shown in, or empty for USD only
 * @param egress          what the SSRF guard will let an admin point a provider connection at
 * @param fx              where the daily reference rates come from
 */
@ConfigurationProperties(prefix = "hephaestus.llm")
@Validated
public record LlmProperties(
    @Pattern(
        regexp = "|" + SUPPORTED_DISPLAY_CURRENCIES,
        flags = Pattern.Flag.CASE_INSENSITIVE,
        message = "must be empty or one of: " + SUPPORTED_DISPLAY_CURRENCIES
    )
    @DefaultValue("")
    String displayCurrency,
    @Valid @DefaultValue Egress egress,
    @Valid @DefaultValue Fx fx
) {
    /**
     * Supported display currencies as a regex alternation. Widening it means widening {@code fx_rate}
     * first: that table holds a single {@code usd_per_eur} scalar.
     */
    public static final String SUPPORTED_DISPLAY_CURRENCIES = "EUR";

    public static final String ECB_DAILY_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml";

    /**
     * @param allowLoopback whether {@code http://localhost}-style provider base URLs are accepted.
     *                      SECURITY: enabling this lets a workspace admin aim a "provider" at
     *                      host-local services, so it belongs in local/dev profiles only. The
     *                      private/link-local/CGNAT check is unconditional and this flag does not
     *                      relax it
     */
    public record Egress(@DefaultValue("false") boolean allowLoopback) {}

    /** @param dailyUrl overridable so an air-gapped instance can point at a local ECB mirror */
    public record Fx(@NotBlank @DefaultValue(ECB_DAILY_URL) String dailyUrl) {}
}
