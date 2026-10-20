package de.tum.cit.aet.hephaestus.integration.core.spi;

/**
 * Who a current source permission check answers for. It is separate from the source-use purpose, which licenses what
 * a source may be used for: the audience only decides whether a person's AI choice applies to the read.
 */
public enum SourceReadAudience {
    /**
     * Review, delivery, quotes, capture, conversation and every other read by or for AI. <b>No AI</b> refuses it.
     * Every caller reads with this audience unless named below.
     */
    PROCESSING,

    /**
     * Only the count of a standing read off reviews that already ran, for a person, never for a model. <b>No AI</b>
     * stops new AI processing and new feedback, not this. Every other source control still applies.
     */
    HUMAN_RECORDED_STANDING
}
