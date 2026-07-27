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
 * <pre>{@code
 * hephaestus.llm:
 *   display-currency: EUR
 *   egress: { allow-loopback: false }
 *   fx:     { daily-url: "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml" }
 * }</pre>
 *
 * <p>One binding, one default, one place an operator can read them. {@code allow-loopback} in
 * particular is read by two collaborators that must agree exactly — {@code EgressPolicy} decides
 * whether a URL may be saved, the proxy's connect-time resolver whether it may be dialled — so it
 * has to have a single owner rather than a default repeated at each reader.
 *
 * @param displayCurrency the code the USD spend figures are additionally shown in, or empty (the
 *                        default) to leave every response byte-for-byte what it was before the
 *                        feature existed. Anything outside {@link #SUPPORTED_DISPLAY_CURRENCIES}
 *                        fails startup: the set is fixed and known here, so a value that could only
 *                        ever have produced USD-only output is a deploy-time typo, not a
 *                        configuration this instance can honour
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
     * Every display currency the stored rates can express, as a regex alternation. One entry, because
     * {@code fx_rate} holds a single {@code usd_per_eur} scalar — widening this means widening that
     * table first.
     */
    public static final String SUPPORTED_DISPLAY_CURRENCIES = "EUR";

    /** The canonical ECB daily file — free, key-less, ~10 KB, published on TARGET working days. */
    public static final String ECB_DAILY_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml";

    /**
     * @param allowLoopback whether {@code http://localhost}-style provider base URLs are accepted.
     *                      Default {@code false}: an unconditional loopback allowance is an SSRF hole
     *                      that lets a workspace admin aim their "provider" at host-local services.
     *                      Turn it on only in a local/dev profile, to test against an Ollama or vLLM
     *                      running beside the app. The private/link-local/CGNAT address check is
     *                      unconditional and this flag does not relax it
     */
    public record Egress(@DefaultValue("false") boolean allowLoopback) {}

    /**
     * @param dailyUrl overridable only so an air-gapped instance can point at a local mirror (and so
     *                 tests can serve a fixture). Unset — the normal case — it is the ECB's own URL
     */
    public record Fx(@NotBlank @DefaultValue(ECB_DAILY_URL) String dailyUrl) {}
}
