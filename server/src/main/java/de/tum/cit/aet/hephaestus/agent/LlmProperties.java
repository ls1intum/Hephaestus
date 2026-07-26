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
 * <p>These three values were previously four separate {@code @Value} expressions spread across
 * {@code agent.catalog} and {@code agent.usage.fx}, each repeating its own default string. That is
 * how {@code EgressPolicy}'s SSRF validation and the probe client's connect-time resolver came to
 * hold two independent copies of the {@code allow-loopback} default — a pair that must agree
 * exactly, since one decides whether a URL may be saved and the other whether it may be dialled.
 * One binding, one default, one place an operator can read them.
 *
 * @param displayCurrency ISO 4217 code the USD spend figures are additionally shown in. Empty (the
 *                        default) turns the whole display-currency feature off and every response is
 *                        byte-for-byte what it was before the feature existed. Only {@code EUR} is
 *                        honoured today — the ECB reference feed quotes per 1 EUR — and any other
 *                        well-formed code degrades to USD-only with a warning rather than failing
 *                        boot; see {@code FxRateLookup}. The pattern here catches the different
 *                        mistake: a value that is not a currency code at all
 *                        ({@code "euro"}, {@code "€"}, a stray quote), which is a deploy-time typo
 *                        and should surface as a failed startup, not as a feature that silently
 *                        never appears
 * @param egress          what the SSRF guard will let an admin point a provider connection at
 * @param fx              where the daily reference rates come from
 */
@ConfigurationProperties(prefix = "hephaestus.llm")
@Validated
public record LlmProperties(
    @Pattern(regexp = "|[A-Za-z]{3}", message = "must be an ISO 4217 currency code, e.g. EUR")
    @DefaultValue("")
    String displayCurrency,
    @Valid @DefaultValue Egress egress,
    @Valid @DefaultValue Fx fx
) {
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
