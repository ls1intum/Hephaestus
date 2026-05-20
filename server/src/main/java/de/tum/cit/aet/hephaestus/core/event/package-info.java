/**
 * Shared events that cross feature-module boundaries. Each event in this package is a
 * dependency-inversion point: declaring it in either feature module would close a cycle.
 */
@org.springframework.modulith.NamedInterface("event")
package de.tum.cit.aet.hephaestus.core.event;
