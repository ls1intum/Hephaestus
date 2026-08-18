/**
 * The producer for the {@code IN_APP} feedback lane. What the three lanes are and how they differ is
 * on {@link de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel}; the pipeline they sit in is
 * {@code docs/contributor/practice-review-pipeline.mdx}.
 *
 * <p><b>This lane is private.</b> An {@code IN_APP} body is the first system-authored text about a
 * named person that is not also visible to them somewhere else, and in a course deployment the workspace
 * admin is the instructor. The operator surfaces therefore show that an {@code IN_APP} unit exists, its
 * state and its reason, and never its text ({@code ReviewFeedbackQueryService}, {@code FeedbackRepository}).
 * That direction is reversible; the other one is not.
 */
package de.tum.cit.aet.hephaestus.agent.handler.inapp;
