/**
 * The facts a weekly digest lists: feedback delivered to the developer on the work or in conversation in one local
 * week, what changed on their Practice profile in it, and how many pieces of feedback wait unread on their Practice
 * page now ({@link de.tum.cit.aet.hephaestus.practices.weeklydigest.WeeklyDigestFacts}). Nothing here writes,
 * delivers or composes words: the weekly digest is not a destination and creates no feedback copy
 * ({@code docs/contributor/practice-review-glossary.mdx}).
 */
@org.springframework.modulith.NamedInterface("weekly-digest")
@org.jspecify.annotations.NullMarked
package de.tum.cit.aet.hephaestus.practices.weeklydigest;
