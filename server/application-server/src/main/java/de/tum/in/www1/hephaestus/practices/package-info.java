/**
 * Code Health Module - AI-powered detection and analysis of code quality issues.
 *
 * <h2>Bounded Context</h2>
 * <p>This module represents a distinct bounded context from the activity module. While activity
 * focuses on <em>recording developer actions</em> for gamification, code health focuses on
 * <em>analyzing code quality</em> through AI-powered detection.
 *
 * <h2>Architecture</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │                          CODE HEALTH MODULE                                  │
 * │                                                                              │
 * │  Domain Events → BadPracticeEventListener → PullRequestBadPracticeDetector  │
 * │                        ↓                              ↓                      │
 * │              BadPracticeDetectorScheduler    intelligence-service (AI)      │
 * │                                                       ↓                      │
 * │                              ┌────────────────────────────────────┐          │
 * │                              │    BadPracticeDetection (Model)    │          │
 * │                              │    PullRequestBadPractice (Model)  │          │
 * │                              └────────────────────────────────────┘          │
 * │                                           ↓                                  │
 * │                    BadPracticeNotificationSender (SPI) → notification module │
 * │                                           ↓                                  │
 * │                    BadPracticeFeedbackService → Langfuse (LLM observability) │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link de.tum.in.www1.hephaestus.practices.detection.PullRequestBadPracticeDetector} - Core detection logic using AI</li>
 *   <li>{@link de.tum.in.www1.hephaestus.practices.detection.BadPracticeDetectorScheduler} - Scheduled detection for all workspaces</li>
 *   <li>{@link de.tum.in.www1.hephaestus.practices.detection.BadPracticeEventListener} - Listens to PR events for real-time detection</li>
 *   <li>{@link de.tum.in.www1.hephaestus.practices.feedback.BadPracticeFeedbackService} - User feedback collection with Langfuse integration</li>
 *   <li>{@link de.tum.in.www1.hephaestus.practices.model.PullRequestBadPractice} - Detection result entity</li>
 * </ul>
 *
 * <h2>SPI (Service Provider Interfaces)</h2>
 * <ul>
 *   <li>{@link de.tum.in.www1.hephaestus.practices.spi.BadPracticeNotificationSender} - Notification sending abstraction</li>
 *   <li>{@link de.tum.in.www1.hephaestus.practices.spi.UserRoleChecker} - User role verification abstraction</li>
 * </ul>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><strong>Bounded Context Isolation</strong>: Separate from activity module (event log vs. analysis)</li>
 *   <li><strong>Infrastructure Abstraction</strong>: AI service calls via anti-corruption layer</li>
 *   <li><strong>SPI Pattern</strong>: Decoupled from notification infrastructure</li>
 *   <li><strong>Feedback Loop</strong>: User feedback improves AI model via Langfuse</li>
 * </ul>
 *
 * @see de.tum.in.www1.hephaestus.activity Activity module (event log for gamification)
 */
package de.tum.in.www1.hephaestus.practices;
