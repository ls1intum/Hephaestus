/**
 * Internal, append-only storage for a recipient's combined response to delivered feedback. A response can
 * capture perceived usefulness (HELPFUL / UNHELPFUL), resolution (ADDRESSED / DISPUTED / NOT_APPLICABLE), or
 * both. The public API lives in the sibling {@code feedback} package; this package retains the historical
 * {@code reaction} persistence name. Suppression follows a concern through the observations bound to its
 * delivered feedback.
 */
@org.springframework.modulith.NamedInterface("reaction")
@org.jspecify.annotations.NullMarked
package de.tum.cit.aet.hephaestus.practices.observation.reaction;
