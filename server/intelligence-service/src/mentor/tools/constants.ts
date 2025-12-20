/**
 * Tool Constants
 *
 * Centralized configuration for all mentor tools.
 * All constants here are actively used - no dead code.
 *
 * @see assigned-work.tool.ts, feedback.tool.ts, etc. for usage
 */

// ─────────────────────────────────────────────────────────────────────────────
// Query Limits - Used in Zod schema .max() validators
// ─────────────────────────────────────────────────────────────────────────────

/** Maximum sessions to retrieve in session history */
export const MAX_SESSIONS = 20;

/** Maximum documents to retrieve */
export const MAX_DOCUMENTS = 20;

/** Maximum issues to retrieve */
export const MAX_ISSUES = 50;

/** Maximum pull requests to retrieve */
export const MAX_PULL_REQUESTS = 50;

/** Maximum reviews to retrieve */
export const MAX_REVIEWS = 30;

/** Maximum review requests to retrieve in assigned work */
export const MAX_REVIEW_REQUESTS = 10;

// ─────────────────────────────────────────────────────────────────────────────
// Time Windows
// ─────────────────────────────────────────────────────────────────────────────

/** Maximum days to look back for activity queries */
export const MAX_LOOKBACK_DAYS = 90;

/** Milliseconds in a day - avoid magic number 86400000 */
export const MS_PER_DAY = 24 * 60 * 60 * 1000;

// ─────────────────────────────────────────────────────────────────────────────
// Display Limits - Used in toModelOutput formatters
// ─────────────────────────────────────────────────────────────────────────────

/** Maximum reviews to show in formatted output */
export const MAX_DISPLAY_REVIEWS = 8;

/** Maximum authors to show in top reviewers/helpers lists */
export const MAX_TOP_AUTHORS = 5;

/** Maximum characters for message preview in session history */
export const MESSAGE_PREVIEW_LENGTH = 150;

/** Maximum characters for document content preview (SQL) */
export const DOCUMENT_PREVIEW_LENGTH = 200;

/** Maximum characters for inline content preview (JS) */
export const CONTENT_PREVIEW_LENGTH = 100;

// ─────────────────────────────────────────────────────────────────────────────
// Insight Thresholds - Used in activity-summary insights generation
// ─────────────────────────────────────────────────────────────────────────────

/** Number of open PRs that triggers a "focus" insight */
export const OPEN_PR_WARNING_THRESHOLD = 3;

/** Days waiting for review that triggers urgency signal */
export const REVIEW_WAIT_URGENCY_DAYS = 2;

// ─────────────────────────────────────────────────────────────────────────────
// Error Messages - Consistent user-facing messages
// ─────────────────────────────────────────────────────────────────────────────

/** Error message shown when data fetch fails - tells LLM something went wrong */
export const DATA_FETCH_ERROR = "Data temporarily unavailable. Results may be incomplete.";
