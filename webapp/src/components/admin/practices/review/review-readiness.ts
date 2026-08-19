import {
	CircleCheckIcon,
	CircleHelpIcon,
	CircleSlashIcon,
	LoaderIcon,
	TriangleAlertIcon,
} from "lucide-react";
import type { AgentBinding } from "@/api/types.gen";
import type { StatusDefs } from "@/components/practice-vocabulary/status-def";

export interface ReviewModelState {
	binding?: AgentBinding;
	isLoading: boolean;
	isError: boolean;
}

/**
 * Whether a review could actually run on the bound model right now.
 *
 * <p>Unknown counts as not runnable: while the binding is loading, or the request for it failed, a
 * screen claiming otherwise would be reassuring on exactly the evidence it does not have.
 */
export function reviewModelRunnable(model: ReviewModelState): boolean {
	return (
		!model.isLoading && !model.isError && model.binding?.ready === true && model.binding.enabled
	);
}

export interface ReviewRunningState {
	enabled: boolean;
	model: ReviewModelState;
}

/**
 * One state per distinguishable answer, so every state owns exactly one set of words: *checking* and
 * *unconfirmed* were one tone reading two contradictory sentences, and a banner cannot title itself
 * "Checking…" while its body says the check failed.
 */
export type ReviewRunningTone = "running" | "checking" | "unconfirmed" | "blocked" | "off";

/**
 * The standing state of reviewing in one workspace, as a status registry on the shared `StatusDef`
 * contract: a headline, a distinct icon, and a tone. Colour is never the only signal — the icon shape
 * and the headline carry the same state (WCAG 2.2 SC 1.4.1) — and the tones deliberately split, so a
 * healthy workspace is affirmed once, quietly, while only the states that stop reviews escalate.
 *
 * <p>Declaration order is the order a reader meets them: working, then unsure, then stopped.
 */
export const REVIEW_RUNNING_DEFS: StatusDefs<ReviewRunningTone> = {
	running: {
		label: "Reviews are running",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "Practice reviews are on and a review model is ready, so new work gets reviewed.",
	},
	checking: {
		label: "Checking reviews",
		icon: LoaderIcon,
		badgeVariant: "secondary",
		description: "Looking up whether a review model is ready…",
	},
	unconfirmed: {
		label: "Reviews can't be confirmed",
		icon: CircleHelpIcon,
		badgeVariant: "warning",
		description:
			"Practice reviews are on, but whether a review model is ready couldn't be checked just now.",
	},
	blocked: {
		label: "Reviews can't start",
		icon: TriangleAlertIcon,
		badgeVariant: "warning",
		description: "Practice reviews are on, but no review model is ready, so none can start.",
	},
	off: {
		label: "Reviews are off",
		icon: CircleSlashIcon,
		badgeVariant: "warning",
		description: "Practice reviews are off in this workspace, so nothing below takes effect yet.",
	},
};

/**
 * Is this workspace reviewing anything? Every review control is otherwise a plausible-looking set of
 * settings that does nothing, and an admin can tune target branches and autonomy settings for a long
 * time before finding that out.
 *
 * <p>The switch and the model are checked separately because they are fixed in different places. The
 * words for each answer live in `REVIEW_RUNNING_DEFS`, not here: one state, one place it is worded.
 */
export function reviewRunningTone({ enabled, model }: ReviewRunningState): ReviewRunningTone {
	if (!enabled) return "off";
	if (model.isLoading) return "checking";
	if (model.isError) return "unconfirmed";
	if (!reviewModelRunnable(model)) return "blocked";
	return "running";
}
