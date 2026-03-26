/**
 * Code Health Module - AI-powered practice detection and contributor feedback.
 *
 * <h2>Bounded Context</h2>
 * <p>This module represents a distinct bounded context from the activity module. While activity
 * focuses on <em>recording developer actions</em> for gamification, code health focuses on
 * <em>analyzing code quality</em> through agent-based practice detection.
 *
 * <h2>Architecture</h2>
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │                        CODE HEALTH MODULE                               │
 * │                                                                         │
 * │  PR Events → AgentJobEventListener → NATS → intelligence-service (AI)  │
 * │                                                       ↓                 │
 * │              PracticeDetectionDeliveryService ← agent result            │
 * │                          ↓                                              │
 * │              ┌──────────────────────────────────┐                       │
 * │              │  Practice (catalog definition)   │                       │
 * │              │  PracticeFinding (per-PR result)  │                       │
 * │              │  FindingFeedback (contributor)    │                       │
 * │              └──────────────────────────────────┘                       │
 * │                          ↓                                              │
 * │              PracticeReviewDeliveryGate → PR comment feedback           │
 * └──────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link de.tum.in.www1.hephaestus.practices.PracticeCatalogController} - CRUD for practice definitions</li>
 *   <li>{@link de.tum.in.www1.hephaestus.practices.finding.PracticeFindingController} - Contributor findings API</li>
 *   <li>{@link de.tum.in.www1.hephaestus.practices.finding.feedback.FindingFeedbackController} - Contributor feedback</li>
 *   <li>{@link de.tum.in.www1.hephaestus.practices.review.PracticeReviewDetectionGate} - Controls when agent jobs are submitted</li>
 *   <li>{@link de.tum.in.www1.hephaestus.practices.review.PracticeReviewDeliveryGate} - Controls when PR comments are posted</li>
 * </ul>
 *
 * <h2>SPI (Service Provider Interfaces)</h2>
 * <ul>
 *   <li>{@link de.tum.in.www1.hephaestus.practices.spi.UserRoleChecker} - User role verification abstraction</li>
 *   <li>{@link de.tum.in.www1.hephaestus.practices.spi.AgentConfigChecker} - Agent configuration validation</li>
 * </ul>
 *
 * @see de.tum.in.www1.hephaestus.activity Activity module (event log for gamification)
 */
package de.tum.in.www1.hephaestus.practices;
