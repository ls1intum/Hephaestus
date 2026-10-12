/**
 * Result persistence + per-developer history — repositories and projections that the
 * agent delivery layer and downstream views read from. Feedback DTOs live one level
 * deeper under {@code feedback.dto}.
 */
@org.springframework.modulith.NamedInterface("result")
@org.jspecify.annotations.NullMarked
package de.tum.cit.aet.hephaestus.practices.result;
