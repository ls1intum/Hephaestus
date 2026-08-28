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

			// oxlint-disable-next-line no-restricted-properties -- Persisted to localStorage and aged across sessions by `onRehydrateStorage` below, so it has to be an absolute instant, not a render-scoped one.
			setPendingSurvey: (survey) => set({ pendingSurvey: survey, dismissedAt: Date.now() }),

			clearPendingSurvey: () =>
				set({
					pendingSurvey: null,
					shouldShowSurvey: false,
					dismissedAt: null,
				}),

			reopenSurvey: () => {
				if (get().pendingSurvey) set({ shouldShowSurvey: true });
			},

			// Only clears the show signal - pendingSurvey remains so user can reopen from notification badge
			clearShowSignal: () => set({ shouldShowSurvey: false }),
		}),
		{
			name: "hephaestus-survey-notification",
			onRehydrateStorage: () => (state) => {
				if (
					state?.pendingSurvey &&
					state.dismissedAt &&
					// oxlint-disable-next-line no-restricted-properties -- Runs during store rehydration, before any component exists to hold a clock.
					Date.now() - state.dismissedAt > EXPIRY_MS
				) {
					state.clearPendingSurvey();
				}
			},
		},
	),
);

export const selectHasPendingSurvey = (s: SurveyNotificationState) => s.pendingSurvey !== null;
