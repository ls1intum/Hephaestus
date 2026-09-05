/**
 * Servlet filters shared by the request surfaces that need them, so a rule the whole instance owes —
 * such as the payload cap — is written once rather than per surface.
 */
@org.springframework.modulith.NamedInterface("web")
@org.jspecify.annotations.NullMarked
package de.tum.cit.aet.hephaestus.core.web;
