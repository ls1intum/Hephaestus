/**
 * Internal, append-only storage for a recipient's combined response to delivered feedback. A response can
 * capture perceived usefulness (HELPFUL / UNHELPFUL), resolution (ADDRESSED / DISPUTED / NOT_APPLICABLE), or
 * both. The public API lives in the sibling {@code feedback} package; this package retains the historical
 * {@code reaction} persistence name. Suppression follows a concern through the results bound to its
 * delivered feedback.
 */
@NamedInterface("reaction")
@NullMarked
package de.tum.cit.aet.hephaestus.practices.result.reaction;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.NamedInterface;
