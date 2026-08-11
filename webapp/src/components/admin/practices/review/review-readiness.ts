import type { AgentBinding } from "@/api/types.gen";

/** The practice-review model binding as a screen knows it: the value, or why there isn't one yet. */
export interface ReviewModelState {
	binding?: AgentBinding;
	isLoading: boolean;
	isError: boolean;
}

/**
 * Whether a review could actually run on the bound model right now.
 *
 * <p>Three conditions, not one: a binding can exist, name a model that has been turned off, or name
 * one the server has not confirmed it can reach. Any of the three means no review starts, and the
 * status card and the page header had better not disagree about which — which is why this is a
 * function rather than an expression written twice.
 *
 * <p>Unknown counts as not runnable. While the binding is loading, or the request for it failed, the
 * honest answer to "will a review run" is no-as-far-as-we-know, and a header that claimed otherwise
 * would be reassuring on exactly the evidence it does not have.
 */
export function reviewModelRunnable(model: ReviewModelState): boolean {
	return (
		!model.isLoading && !model.isError && model.binding?.ready === true && model.binding.enabled
	);
}

export interface ReviewRunningState {
	/** The workspace-level switch: whether new practice reviews may start at all. */
	enabled: boolean;
	model: ReviewModelState;
}

export type ReviewRunningTone = "running" | "blocked" | "off" | "unknown";

export interface ReviewRunningSummary {
	tone: ReviewRunningTone;
	sentence: string;
}

/**
 * The one fact all three sections rest on: is this workspace reviewing anything?
 *
 * <p>It belongs in the shared header because each section is otherwise a plausible-looking set of
 * controls that does nothing. An admin can spend a long time tuning target branches and autonomy
 * tiers on a workspace where practice reviews are switched off, and every one of those screens will
 * look like it is working.
 *
 * <p>The model is named separately from the switch because they fail differently and are fixed in
 * different places — one is a toggle on this page, the other is a binding on AI models.
 */
export function reviewRunningSummary({ enabled, model }: ReviewRunningState): ReviewRunningSummary {
	if (!enabled) {
		return {
			tone: "off",
			sentence: "Practice reviews are off in this workspace, so nothing below takes effect yet.",
		};
	}
	if (model.isLoading) {
		return { tone: "unknown", sentence: "Checking whether a review model is ready…" };
	}
	if (model.isError) {
		return {
			tone: "unknown",
			sentence: "Practice reviews are on, but we couldn't check whether a review model is ready.",
		};
	}
	if (!reviewModelRunnable(model)) {
		return {
			tone: "blocked",
			sentence: "Practice reviews are on, but no review model is ready, so none can start.",
		};
	}
	return { tone: "running", sentence: "Practice reviews are running in this workspace." };
}
