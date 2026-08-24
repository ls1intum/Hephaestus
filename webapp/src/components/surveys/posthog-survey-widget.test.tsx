import { act, render, screen } from "@testing-library/react";
import type { Survey } from "posthog-js";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useSurveyNotificationStore } from "@/stores/survey-notification-store";

type RenderReason = { visible: boolean; disabledReason?: string };

let availableSurveys: Survey[] = [];
const pendingRenderChecks = new Map<string, (reason: RenderReason) => void>();

/** Only the surface the widget calls; `usePostHogClient` is mocked, so nothing wider is needed. */
const client = {
	onSurveysLoaded: vi.fn((callback: (surveys: Survey[]) => void) => {
		void callback;
		return () => {};
	}),
	getSurveys: vi.fn((callback: (surveys: Survey[]) => void) => {
		callback(availableSurveys);
	}),
	canRenderSurveyAsync: vi.fn(
		(surveyId: string) =>
			new Promise<RenderReason>((resolve) => {
				pendingRenderChecks.set(surveyId, resolve);
			}),
	),
	capture: vi.fn(),
	getActiveMatchingSurveys: vi.fn(),
};

vi.mock("@/integrations/posthog/use-posthog-client", () => ({
	usePostHogClient: () => client,
}));

vi.mock("motion/react", () => ({
	AnimatePresence: ({ children }: { children?: ReactNode }) => children,
	motion: {
		div: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
	},
}));

vi.mock("./survey-container", () => ({
	SurveyContainer: ({ survey }: { survey: { id: string } }) => <output>{survey.id}</output>,
}));

import { PostHogSurveyWidget } from "./posthog-survey-widget";

function apiSurvey(id: string): Survey {
	return {
		id,
		name: `Survey ${id}`,
		type: "api",
		feature_flag_keys: null,
		linked_flag_key: null,
		targeting_flag_key: null,
		internal_targeting_flag_key: null,
		questions: [],
		appearance: null,
		conditions: null,
		start_date: null,
		end_date: null,
		current_iteration: null,
		current_iteration_start_date: null,
	};
}

/** The widget polls PostHog 100ms after mount, then reveals the survey 5s after it becomes visible. */
const INIT_DELAY_MS = 100;
const REVEAL_DELAY_MS = 5000;

async function settleRenderCheck(surveyId: string, reason: RenderReason) {
	const resolve = pendingRenderChecks.get(surveyId);
	if (!resolve) {
		throw new Error(`canRenderSurveyAsync was never called for ${surveyId}`);
	}
	pendingRenderChecks.delete(surveyId);
	await act(async () => {
		resolve(reason);
	});
}

describe("PostHogSurveyWidget", () => {
	beforeEach(() => {
		vi.useFakeTimers({ shouldAdvanceTime: true });
		availableSurveys = [apiSurvey("survey-a"), apiSurvey("survey-b")];
		pendingRenderChecks.clear();
		useSurveyNotificationStore.getState().clearPendingSurvey();
	});

	afterEach(() => {
		vi.useRealTimers();
		vi.clearAllMocks();
	});

	it("shows the survey PostHog says is renderable", async () => {
		render(<PostHogSurveyWidget surveyId="survey-a" />);

		await act(async () => {
			vi.advanceTimersByTime(INIT_DELAY_MS);
		});
		await settleRenderCheck("survey-a", { visible: true });
		await act(async () => {
			vi.advanceTimersByTime(REVEAL_DELAY_MS);
		});

		expect(screen.getByRole("status").textContent).toBe("survey-a");
		expect(client.capture).toHaveBeenCalledWith(
			"survey shown",
			expect.objectContaining({ $survey_id: "survey-a" }),
		);
	});

	// The cancellation signal is the only thing standing between an abandoned lookup and the screen:
	// each effect run carries its own request counter, so a stale resolution passes that check.
	it("drops a lookup that was still in flight when the requested survey changed", async () => {
		const { rerender } = render(<PostHogSurveyWidget surveyId="survey-a" />);

		await act(async () => {
			vi.advanceTimersByTime(INIT_DELAY_MS);
		});
		expect(client.canRenderSurveyAsync).toHaveBeenCalledWith("survey-a");

		rerender(<PostHogSurveyWidget surveyId="survey-b" />);
		await act(async () => {
			vi.advanceTimersByTime(INIT_DELAY_MS);
		});

		// The superseded lookup answers last, and answers "yes" — the widget must still ignore it.
		await settleRenderCheck("survey-b", { visible: true });
		await settleRenderCheck("survey-a", { visible: true });
		await act(async () => {
			vi.advanceTimersByTime(REVEAL_DELAY_MS);
		});

		expect(screen.getByRole("status").textContent).toBe("survey-b");
	});

	it("renders nothing when no survey is renderable", async () => {
		render(<PostHogSurveyWidget surveyId="survey-a" />);

		await act(async () => {
			vi.advanceTimersByTime(INIT_DELAY_MS);
		});
		await settleRenderCheck("survey-a", { visible: false });
		await act(async () => {
			vi.advanceTimersByTime(REVEAL_DELAY_MS);
		});

		expect(screen.queryByRole("status")).toBeNull();
	});

	it("does not look for a survey when autoOpen is off", () => {
		render(<PostHogSurveyWidget surveyId="survey-a" autoOpen={false} />);

		act(() => {
			vi.advanceTimersByTime(INIT_DELAY_MS);
		});

		expect(client.getSurveys).not.toHaveBeenCalled();
	});
});
