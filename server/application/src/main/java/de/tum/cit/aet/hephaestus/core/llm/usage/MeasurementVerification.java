package de.tum.cit.aet.hephaestus.core.llm.usage;

import org.springframework.modulith.NamedInterface;

/** Whether source selection accepted the whole measured token vector, independently of its price. */
@NamedInterface("llm-usage")
public enum MeasurementVerification {
    /** Source selection accepted this vector; this does not certify that every provider call was observed. */
    VERIFIED,
    /** Source selection could not accept a measured vector as the usage of the whole operation. */
    UNVERIFIABLE,
    /** Historical or imported usage without a recorded source-selection decision. */
    UNKNOWN,
}
