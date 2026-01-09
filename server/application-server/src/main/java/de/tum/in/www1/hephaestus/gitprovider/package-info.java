/**
 * Git provider integration layer for Hephaestus.
 * <p>
 * This module handles all interactions with external Git hosting providers (currently GitHub,
 * with GitLab planned). It provides a clean abstraction that isolates provider-specific details
 * from the rest of the application.
 *
 * <h2>Architecture Overview</h2>
 *
 * <pre>
 * gitprovider/
 * ├── common/                    # Shared infrastructure
 * │   ├── events/                # Domain events for reactive features
 * │   ├── exception/             # Exceptions definitions for gitprovider
 * │   ├── github/                # GitHub-specific utilities (GraphQL client, parsers)
 * │   └── spi/                   # Service Provider Interfaces for module isolation
 * │
 * ├── {domain}/                    # Provider-agnostic domain modules
 * │   ├── {Entity}.java            # JPA entity (e.g., PullRequest, Issue)
 * │   ├── {Entity}Repository.java  # Spring Data repository
 * │   ├── {Entity}InfoDTO.java     # API response DTO
 * │   └── github/                  # GitHub-specific implementation
 * │       ├── dto/                 # GitHub webhook/GraphQL DTOs
 * │       ├── GitHub{Entity}MessageHandler.java  # Webhook handler
 * │       ├── GitHub{Entity}Processor.java       # Entity processing logic
 * │       └── GitHub{Entity}SyncService.java     # GraphQL sync service
 * │
 * └── sync/                             # Orchestration layer
 *     ├── GitHubDataSyncScheduler.java  # Scheduled sync jobs
 *     ├── GitHubDataSyncService.java    # Coordinates full workspace sync
 *     └── NatsConsumerService.java      # Webhook event consumption
 * </pre>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>Provider Isolation</b>: Domain entities are provider-agnostic. Provider-specific code
 *       lives in {@code github/} (or future {@code gitlab/}) subdirectories.</li>
 *   <li><b>Single Processing Path</b>: Processors handle both webhook events and GraphQL sync,
 *       ensuring consistent entity creation/updates.</li>
 *   <li><b>Idempotent Operations</b>: All operations are idempotent via upsert patterns.</li>
 *   <li><b>Domain Events</b>: Processors publish events for reactive features (activity tracking,
 *       leaderboard updates, etc.).</li>
 *   <li><b>Module Boundaries</b>: SPI interfaces in {@code common/spi/} define contracts with
 *       workspace/leaderboard modules, preventing circular dependencies.</li>
 * </ul>
 *
 * <h2>Adding GitLab Support</h2>
 * <p>
 * To add GitLab support:
 * <ol>
 *   <li>Create {@code gitlab/} subdirectories in each domain module</li>
 *   <li>Implement GitLab-specific DTOs matching webhook payloads</li>
 *   <li>Create GitLab processors that convert DTOs to domain entities</li>
 *   <li>Add GitLab GraphQL client in {@code common/gitlab/}</li>
 *   <li>Implement GitLab message handlers for webhook events</li>
 * </ol>
 * <p>
 * The domain entities, repositories, and SPI interfaces remain unchanged.
 *
 * <h2>Security Considerations</h2>
 * <ul>
 *   <li>All user-controllable data is sanitized via
 *       {@link de.tum.in.www1.hephaestus.core.LoggingUtils#sanitizeForLog} before logging</li>
 *   <li>String fields are sanitized via
 *       {@link de.tum.in.www1.hephaestus.gitprovider.common.PostgresStringUtils} before
 *       database storage</li>
 *   <li>GraphQL queries use {@code fullDatabaseId} (BigInt) to avoid integer overflow</li>
 * </ul>
 *
 * @see de.tum.in.www1.hephaestus.gitprovider.common.spi Service Provider Interfaces
 * @see de.tum.in.www1.hephaestus.gitprovider.common.events Domain Events
 * @see de.tum.in.www1.hephaestus.gitprovider.sync Sync Orchestration
 */
package de.tum.in.www1.hephaestus.gitprovider;
