import type { Meta, StoryObj } from "@storybook/react";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import Header from "@/components/core/Header";
import { useSurveyNotificationStore } from "@/stores/survey-notification-store";
import type { PostHogSurvey } from "@/types/survey";
import { SurveyContainer } from "./survey-container";
import { SURVEY_LAYOUT_ID, SurveyNotificationButton } from "./survey-notification-button";

const meta = {
	title: "Surveys/Survey notification button",
	component: SurveyNotificationButton,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
} satisfies Meta<typeof SurveyNotificationButton>;

export default meta;
type Story = StoryObj<typeof meta>;

const mockSurvey: PostHogSurvey = {
	id: "demo",
	name: "Quick Feedback",
	type: "api",
	description: "Help us improve",
	questions: [
		{
			id: "q1",
			type: "rating",
			question: "How would you rate your experience?",
			display: "number",
			scale: 10,
			lowerBoundLabel: "Poor",
			upperBoundLabel: "Excellent",
			required: false,
		},
	],
	conditions: null,
	start_date: new Date().toISOString(),
	end_date: null,
	enable_partial_responses: true,
	current_iteration: 1,
	current_iteration_start_date: new Date().toISOString(),
};

/**
 * Survey morphs into notification badge when dismissed.
 * Persists across page refresh (try it!).
 */
export const FullPagePreview: Story = {
	render: () => {
		const Demo = () => {
			const [visible, setVisible] = useState(false);
			const pending = useSurveyNotificationStore((s) => s.pendingSurvey);
			const setPending = useSurveyNotificationStore((s) => s.setPendingSurvey);
			const clear = useSurveyNotificationStore((s) => s.clearPendingSurvey);

			useEffect(() => {
				if (!pending) {
					const t = setTimeout(() => setVisible(true), 400);
					return () => clearTimeout(t);
				}
			}, [pending]);

			// Mirrors the widget: the badge raises a one-shot reopen request through the store.
			useEffect(() => {
				const consumeShowSignal = () => {
					const { shouldShowSurvey, clearShowSignal } = useSurveyNotificationStore.getState();
					if (!shouldShowSurvey) {
						return;
					}
					clearShowSignal();
					setVisible(true);
				};

				consumeShowSignal();
				return useSurveyNotificationStore.subscribe(consumeShowSignal);
			}, []);

			return (
				<div className="min-h-screen bg-background">
					<Header
						version="demo"
						environmentName="Local"
						isProduction={false}
						isAuthenticated
						isLoading={false}
						name="Demo"
						username="demo"
						onLogin={() => {}}
						onLogout={() => {}}
					/>
					<main className="p-8 max-w-2xl mx-auto">
						<h1 className="text-2xl font-bold mb-4">Dashboard</h1>
						<p className="text-muted-foreground">
							{pending ? "Click the badge to reopen." : visible ? "Click X to dismiss." : "Done."}
						</p>
					</main>

					<AnimatePresence mode="wait">
						{visible && (
							<motion.div className="fixed bottom-6 right-6 z-[100] max-w-sm">
								<motion.div
									layoutId={SURVEY_LAYOUT_ID}
									layout
									className="overflow-hidden border bg-background shadow-2xl"
									style={{ borderRadius: 12 }}
									transition={{ type: "spring", stiffness: 400, damping: 30 }}
								>
									<motion.div
										initial={{ opacity: 0 }}
										animate={{ opacity: 1 }}
										exit={{ opacity: 0 }}
										transition={{ duration: 0.15 }}
									>
										<SurveyContainer
											survey={mockSurvey}
											onComplete={() => {
												clear();
												setVisible(false);
											}}
											onDismiss={() => {
												setPending(mockSurvey);
												setVisible(false);
											}}
											onProgress={() => {}}
										/>
									</motion.div>
								</motion.div>
							</motion.div>
						)}
					</AnimatePresence>
				</div>
			);
		};
		return <Demo />;
	},
};
