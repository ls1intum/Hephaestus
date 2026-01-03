package de.tum.in.www1.hephaestus.shared.badpractice;

/**
 * Immutable DTO for a single bad practice.
 */
public record BadPracticeInfo(Long id, String title, String description, String state) {}
