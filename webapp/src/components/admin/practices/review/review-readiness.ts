import type { AgentBinding } from "@/api/types.gen";

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

export type ReviewRunningTone = "running" | "blocked" | "off" | "unknown";

export interface ReviewRunningSummary {
	tone: ReviewRunningTone;
	sentence: string;
}

/**
 * Is this workspace reviewing anything? Every review control is otherwise a plausible-looking set of
 * settings that does nothing, and an admin can tune target branches and autonomy tiers for a long
 * time before finding that out.
 *
 * <p>The switch and the model are named separately because they are fixed in different places.
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
