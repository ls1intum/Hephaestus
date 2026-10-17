import type { ItemEvent, ReviewRequestOutcome } from "@/api/types.gen";
import type { ReviewSectionId } from "@/components/admin/practices/review/review-sections";
import { REVIEW_ORIGIN_DEFS } from "@/components/practice-vocabulary/review-origin-defs";

export type FoundBy = ItemEvent["foundBy"];
export type RefusalReason = NonNullable<ReviewRequestOutcome["reason"]>;

/**
 * The longest free-text filter a URL keeps for the trace: a kind of work, or the practice table's
 * filters. The table's input stops there, so only a hand-edited address reaches a schema's fallback.
 */
export const REVIEW_FILTER_MAX_LENGTH = 120;

// Event and request refusals share vocabulary but have separately generated wire unions.
type EventReason = NonNullable<ItemEvent["reason"]>;

type Agree<A, B> = [A] extends [B] ? ([B] extends [A] ? true : false) : false;

export type ReasonVocabulariesAgree<T extends true = Agree<RefusalReason, EventReason>> = T;

/** Names the destination on its own: a link is read out of its sentence (WCAG 2.4.4). */
interface RefusalFixLabel {
	label: string;
}

/**
 * The two shapes a destination has: a plain admin route taking only `workspaceSlug`, or a section
 * of the Review page, which is one route plus a search param. A union rather than an optional
 * `search`, so a fix cannot name a section of a page that has none.
 */
export type RefusalFix = RefusalFixLabel &
	(
		| {
				to:
					| "/w/$workspaceSlug/admin/models"
					| "/w/$workspaceSlug/admin/practices"
					| "/w/$workspaceSlug/admin/settings"
					| "/w/$workspaceSlug/admin/usage";
				section?: never;
		  }
		| { to?: never; section: ReviewSectionId }
	);

/**
 * Where each refusal is undone, for the readers who can undo it.
 *
 * <p>Partial on purpose: a reason earns an entry only when a workspace admin can go somewhere and
 * change the answer. The rest are self-healing, already running, terminal, or fixable only by the
 * author on their own account. Offering a settings page that cannot affect what the reader just
 * read is worse than offering nothing, because they will change something to make it stop.
 */
export const REFUSAL_FIXES: Partial<Record<RefusalReason, RefusalFix>> = {
	GATE_WITHHELD: { section: "automated-review", label: "Open Review: Automated review" },
	OUT_OF_REVIEW_SCOPE: { section: "automated-review", label: "Open Review: Automated review" },
	PRACTICES_DISABLED: { section: "automated-review", label: "Open Review: Automated review" },
	FEEDBACK_MODE_OFF: { section: "feedback-mode", label: "Open Review: Feedback mode" },
	NO_ACTIVE_PRACTICE: { to: "/w/$workspaceSlug/admin/practices", label: "Open Practice setup" },
	REVIEW_MODEL_UNBOUND: { to: "/w/$workspaceSlug/admin/models", label: "Set up a review model" },
	MODEL_UNAVAILABLE: { to: "/w/$workspaceSlug/admin/models", label: "Open AI models" },
	BUDGET_EXHAUSTED: { to: "/w/$workspaceSlug/admin/usage", label: "Open AI usage" },
};

export const FOUND_BY_LABELS: Record<FoundBy, string> = {
	WEBHOOK: "Live event",
	SYNC: "Noticed during a sync",
	REQUEST: REVIEW_ORIGIN_DEFS.REQUEST.label,
	BACKFILL: REVIEW_ORIGIN_DEFS.BACKFILL.label,
	SCHEDULE: REVIEW_ORIGIN_DEFS.SCHEDULE.label,
};

export const FOUND_BY_DESCRIPTIONS: Record<FoundBy, string> = {
	WEBHOOK: "Reported by the provider as it happened, so the time is exact.",
	SYNC: "Spotted by a scheduled sync, so the time is only as precise as the sync.",
	REQUEST: REVIEW_ORIGIN_DEFS.REQUEST.description,
	BACKFILL: REVIEW_ORIGIN_DEFS.BACKFILL.description,
	SCHEDULE: REVIEW_ORIGIN_DEFS.SCHEDULE.description,
};
