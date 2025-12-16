import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { PostHogSurvey } from "@/types/survey";

interface SurveyNotificationState {
	pendingSurvey: PostHogSurvey | null;
	shouldShowSurvey: boolean;
	dismissedAt: number | null;
	setPendingSurvey: (survey: PostHogSurvey) => void;
	clearPendingSurvey: () => void;
	reopenSurvey: () => void;
	clearShowSignal: () => void;
}

const EXPIRY_MS = 7 * 24 * 60 * 60 * 1000; // 7 days

/**
 * Persisted store for dismissed survey notifications.
 * Stores full survey object for smooth morph animations.
 * Auto-expires after 7 days.
 */
export const useSurveyNotificationStore = create<SurveyNotificationState>()(
	persist(
		(set, get) => ({
			pendingSurvey: null,
			shouldShowSurvey: false,
			dismissedAt: null,

			setPendingSurvey: (survey) =>
				set({ pendingSurvey: survey, dismissedAt: Date.now() }),

			clearPendingSurvey: () =>
				set({
					pendingSurvey: null,
					shouldShowSurvey: false,
					dismissedAt: null,
				}),

			reopenSurvey: () => {
				if (get().pendingSurvey) set({ shouldShowSurvey: true });
			},

			clearShowSignal: () =>
				set({ shouldShowSurvey: false, pendingSurvey: null }),
		}),
		{
			name: "hephaestus-survey-notification",
			onRehydrateStorage: () => (state) => {
				if (state?.pendingSurvey && state.dismissedAt) {
					if (Date.now() - state.dismissedAt > EXPIRY_MS) {
						state.clearPendingSurvey();
					}
				}
			},
		},
	),
);

export const selectHasPendingSurvey = (s: SurveyNotificationState) =>
	s.pendingSurvey !== null;
