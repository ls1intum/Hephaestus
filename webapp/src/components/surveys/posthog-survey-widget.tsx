import { AnimatePresence, motion } from "framer-motion";
import type { Survey as PostHogSurveyRaw } from "posthog-js";
import { usePostHog } from "posthog-js/react";
import { useEffect, useRef, useState } from "react";

import {
	normalisePostHogSurvey,
	type PostHogSurvey,
	type SurveyQuestion,
	type SurveyResponse,
} from "@/types/survey";
import { SurveyContainer } from "./survey-container";

/**
 * Check if a URL matches the survey's URL condition
 * Replicates PostHog's internal URL matching logic
 */
function checkUrlMatch(
	currentUrl: string,
	pattern: string,
	matchType?: string,
): boolean {
	const type = matchType || "icontains";

	switch (type) {
		case "icontains":
			return currentUrl.toLowerCase().includes(pattern.toLowerCase());
		case "regex":
			try {
				return new RegExp(pattern).test(currentUrl);
			} catch {
				return false;
			}
		case "exact":
			return currentUrl === pattern;
		case "is_not":
			return currentUrl !== pattern;
		case "not_icontains":
			return !currentUrl.toLowerCase().includes(pattern.toLowerCase());
		case "not_regex":
			try {
				return !new RegExp(pattern).test(currentUrl);
			} catch {
				return false;
			}
		default:
			return currentUrl.toLowerCase().includes(pattern.toLowerCase());
	}
}

interface PostHogSurveyWidgetProps {
	surveyId?: string;
	autoOpen?: boolean;
	reloadOnComplete?: boolean;
}

export function PostHogSurveyWidget({
	surveyId,
	autoOpen = true,
	reloadOnComplete = false,
}: PostHogSurveyWidgetProps) {
	const posthog = usePostHog();
	const [survey, setSurvey] = useState<PostHogSurvey | null>(null);
	const [isVisible, setIsVisible] = useState(false);
	const [showWithDelay, setShowWithDelay] = useState(false);
	const [submissionId, setSubmissionId] = useState<string | null>(null);
	const hasTrackedShown = useRef(false);
	const currentUrl = useRef(window.location.href);

	// Monitor URL changes and re-check survey conditions
	useEffect(() => {
		const checkUrlChange = () => {
			const newUrl = window.location.href;
			if (newUrl !== currentUrl.current) {
				currentUrl.current = newUrl;

				// If we have a survey with URL conditions, re-check eligibility
				if (survey?.conditions?.url) {
					const urlMatches = checkUrlMatch(
						newUrl,
						survey.conditions.url,
						survey.conditions.urlMatchType,
					);

					if (!urlMatches) {
						// URL no longer matches - hide survey
						setIsVisible(false);
						setShowWithDelay(false);
					} else if (!isVisible) {
						// URL now matches - show survey with delay
						setIsVisible(true);
					}
				}
			}
		};

		// Listen to popstate (back/forward buttons)
		window.addEventListener("popstate", checkUrlChange);

		// Listen to pushState and replaceState (client-side navigation)
		const originalPushState = window.history.pushState;
		const originalReplaceState = window.history.replaceState;

		window.history.pushState = (...args) => {
			originalPushState.apply(window.history, args);
			checkUrlChange();
		};

		window.history.replaceState = (...args) => {
			originalReplaceState.apply(window.history, args);
			checkUrlChange();
		};

		return () => {
			window.removeEventListener("popstate", checkUrlChange);
			window.history.pushState = originalPushState;
			window.history.replaceState = originalReplaceState;
		};
	}, [survey, isVisible]);

	// Delay showing the survey by 5 seconds for a better UX
	useEffect(() => {
		if (!isVisible) {
			setShowWithDelay(false);
			return;
		}

		const timer = setTimeout(() => {
			setShowWithDelay(true);
		}, 5000);

		return () => clearTimeout(timer);
	}, [isVisible]);

	useEffect(() => {
		if (!posthog || !autoOpen) {
			return;
		}

		let isActive = true;
		let latestRequestId = 0;

		const resolveSurvey = async (surveys: PostHogSurveyRaw[]) => {
			if (!isActive) {
				return;
			}

			const eligible = surveys.filter((candidate) => candidate.type === "api");
			const candidates = surveyId
				? eligible.filter((candidate) => candidate.id === surveyId)
				: eligible;

			if (candidates.length === 0) {
				if (!isActive) {
					return;
				}
				setIsVisible(false);
				setSurvey(null);
				setSubmissionId(null);
				hasTrackedShown.current = false;
				return;
			}

			const requestId = ++latestRequestId;

			for (const candidate of candidates) {
				try {
					const reason = await posthog.canRenderSurveyAsync(candidate.id);

					if (!isActive || requestId !== latestRequestId) {
						return;
					}

					if (!reason.visible) {
						continue;
					}

					// Check URL/selector conditions for API surveys
					if (candidate.conditions?.url) {
						const urlMatches = checkUrlMatch(
							window.location.href,
							candidate.conditions.url,
							candidate.conditions.urlMatchType,
						);
						if (!urlMatches) {
							continue;
						}
					}

					if (candidate.conditions?.selector) {
						const selectorExists = document.querySelector(
							candidate.conditions.selector,
						);
						if (!selectorExists) {
							continue;
						}
					}

					const normalized = normalisePostHogSurvey(candidate);
					setSurvey(normalized);
					setIsVisible(true);
					hasTrackedShown.current = false;
					return;
				} catch (error) {
					console.error("[PostHogSurveyWidget] Error checking survey:", error);

					if (!isActive || requestId !== latestRequestId) {
						return;
					}
				}
			}

			if (!isActive || requestId !== latestRequestId) {
				return;
			}

			setIsVisible(false);
			setSurvey(null);
			setSubmissionId(null);
			hasTrackedShown.current = false;
		};

		const unsubscribe = posthog.onSurveysLoaded(() => {
			if (!isActive) {
				return;
			}
			posthog.getSurveys((updatedSurveys) => {
				void resolveSurvey(updatedSurveys as PostHogSurveyRaw[]);
			}, false);
		});

		const initTimeout = setTimeout(() => {
			if (!isActive) {
				return;
			}

			posthog.getSurveys((surveys) => {
				void resolveSurvey(surveys as PostHogSurveyRaw[]);
			}, false);
		}, 100);

		return () => {
			isActive = false;
			clearTimeout(initTimeout);
			unsubscribe?.();
		};
	}, [autoOpen, posthog, surveyId]);
	useEffect(() => {
		if (!posthog || !survey || !isVisible || hasTrackedShown.current) {
			return;
		}

		posthog.capture("survey shown", {
			$survey_id: survey.id,
			$survey_name: survey.name,
		});

		// CRITICAL: Update lastSeenSurveyDate for wait period logic
		// PostHog's wait period check uses this value to prevent surveys from showing too frequently
		// For API surveys (manual rendering), we must update this ourselves
		// Popover/widget surveys do this automatically in usePopupVisibility
		localStorage.setItem("lastSeenSurveyDate", new Date().toISOString());

		hasTrackedShown.current = true;
	}, [isVisible, posthog, survey]);

	const ensureSubmissionId = () => {
		if (submissionId) {
			return submissionId;
		}
		const generated =
			typeof crypto !== "undefined" && crypto.randomUUID
				? crypto.randomUUID()
				: `${Date.now()}`;
		setSubmissionId(generated);
		return generated;
	};

	const handleDismiss = (step: number) => {
		if (!survey || !posthog) {
			return;
		}
		posthog.capture("survey dismissed", {
			$survey_id: survey.id,
			$survey_name: survey.name,
			$current_step: step,
		});
		setIsVisible(false);
		setSurvey(null);
		setSubmissionId(null);
	};

	const handleProgress = (responses: Record<string, SurveyResponse>) => {
		if (!survey || !posthog) {
			return;
		}
		if (
			survey.enable_partial_responses === false ||
			Object.keys(responses).length === 0
		) {
			return;
		}
		const id = ensureSubmissionId();
		posthog.capture(
			"survey partial",
			buildSurveyEventPayload({
				survey,
				responses,
				submissionId: id,
				completed: false,
			}),
		);
	};

	const handleComplete = (responses: Record<string, SurveyResponse>) => {
		if (!survey || !posthog) {
			return;
		}
		const id = ensureSubmissionId();
		posthog.capture(
			"survey sent",
			buildSurveyEventPayload({
				survey,
				responses,
				submissionId: id,
				completed: true,
			}),
		);
		setIsVisible(false);
		setSurvey(null);
		setSubmissionId(null);

		if (reloadOnComplete) {
			posthog.getActiveMatchingSurveys(() => {}, true);
		}
	};

	if (!survey || !isVisible) {
		return null;
	}

	return (
		<AnimatePresence>
			{showWithDelay && (
				<motion.div
					initial={{ opacity: 0, y: 100, scale: 0.95 }}
					animate={{ opacity: 1, y: 0, scale: 1 }}
					exit={{ opacity: 0, y: 100, scale: 0.95 }}
					transition={{
						type: "spring",
						stiffness: 300,
						damping: 30,
						opacity: { duration: 0.3 },
					}}
					className="fixed inset-x-0 bottom-0 z-[100] w-full px-4 pb-4 sm:inset-auto sm:bottom-6 sm:right-6 sm:w-auto sm:px-0 sm:pb-0"
				>
					<div className="mx-auto w-full sm:max-w-md">
						<motion.div
							initial={{ scale: 0.95 }}
							animate={{ scale: 1 }}
							transition={{ delay: 0.1, type: "spring", stiffness: 400 }}
							className="overflow-hidden rounded-lg border bg-background shadow-2xl"
						>
							<SurveyContainer
								survey={survey}
								onComplete={handleComplete}
								onDismiss={handleDismiss}
								onProgress={handleProgress}
							/>
						</motion.div>
					</div>
				</motion.div>
			)}
		</AnimatePresence>
	);
}

const buildSurveyEventPayload = ({
	survey,
	responses,
	submissionId,
	completed,
}: {
	survey: PostHogSurvey;
	responses: Record<string, SurveyResponse>;
	submissionId: string;
	completed: boolean;
}) => {
	const payload: Record<string, unknown> = {
		$survey_id: survey.id,
		$survey_name: survey.name,
		$survey_submission_id: submissionId,
		$survey_completed: completed,
		$survey_partially_completed: !completed,
		$survey_questions: survey.questions.map((question) => ({
			id: question.id,
			question: question.question,
			type: question.type,
		})),
	};

	if (typeof survey.current_iteration === "number") {
		payload.$survey_iteration = survey.current_iteration;
	}
	if (survey.current_iteration_start_date) {
		payload.$survey_iteration_start_date = survey.current_iteration_start_date;
	}

	survey.questions.forEach((question) => {
		const response = responses[question.id];
		if (response === undefined || response === null) {
			return;
		}
		const key = `$survey_response_${question.id}`;
		const value = transformResponseValue(question, response);
		if (value !== undefined) {
			payload[key] = value;
		}
	});

	return payload;
};

const transformResponseValue = (
	question: SurveyQuestion,
	response: SurveyResponse,
) => {
	if (response === null || response === undefined) {
		return undefined;
	}

	switch (question.type) {
		case "multiple_choice":
			return Array.isArray(response) ? response : [String(response)];
		case "rating": {
			if (typeof response === "number") {
				return response.toString();
			}
			const numeric = Number(response);
			return Number.isFinite(numeric) ? numeric.toString() : String(response);
		}
		default:
			if (Array.isArray(response)) {
				return response.map((value) => value.toString());
			}
			return String(response);
	}
};
