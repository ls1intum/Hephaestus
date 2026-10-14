package de.tum.cit.aet.hephaestus.core.llm.usage;

import org.jspecify.annotations.Nullable;
import org.springframework.modulith.NamedInterface;

/**
 * Whose credential paid for a unit of LLM usage, and so which of the two caps it counts against.
 */
@NamedInterface("llm-usage")
public enum UsageFundingSource {
    INSTANCE,
    WORKSPACE,
    ;

    /**
     * The {@code cap} tag of a budget refusal counter for this purse: {@code instance} or {@code
     * byo}. A purse that is not known is the instance's. Dashboards read the same values on {@code
     * llm.budget.blocked} and {@code llm.proxy.budget.blocked}.
     */
    public static String capTag(@Nullable UsageFundingSource purse) {
        return purse == WORKSPACE ? "byo" : "instance";
    }
}
