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
 * │  Domain Events → AgentJobEventListener → Agent Pipeline (Docker sandbox)    │
 * │                                                       ↓                      │
 * │                    PracticeDetectionDeliveryService → PracticeFinding        │
 * │                                                       ↓                      │
 * │                    FindingFeedbackController → FindingFeedback               │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link de.tum.in.www1.hephaestus.practices.finding.PracticeFindingController} - Contributor findings API</li>
 *   <li>{@link de.tum.in.www1.hephaestus.practices.finding.FindingFeedbackController} - Contributor feedback API</li>
 *   <li>{@link de.tum.in.www1.hephaestus.practices.PracticeCatalogController} - Practice catalog CRUD</li>
 * </ul>
 *
 * <h2>SPI (Service Provider Interfaces)</h2>
 * <ul>
 *   <li>{@link de.tum.in.www1.hephaestus.practices.spi.UserRoleChecker} - User role verification abstraction</li>
 * </ul>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><strong>Bounded Context Isolation</strong>: Separate from activity module (event log vs. analysis)</li>
 *   <li><strong>Infrastructure Abstraction</strong>: AI service calls via anti-corruption layer</li>
 *   <li><strong>SPI Pattern</strong>: Decoupled via service provider interfaces (e.g. UserRoleChecker)</li>
 * </ul>
 *
 * @see de.tum.in.www1.hephaestus.activity Activity module (event log for gamification)
 */
package de.tum.in.www1.hephaestus.practices;
