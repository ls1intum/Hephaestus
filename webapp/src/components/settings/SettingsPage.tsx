import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { optionalIntegrationsAvailable } from "@/integrations/consent";
import { AiReviewSection, type AiReviewSectionProps } from "./AiReviewSection";
import { CookiePreferencesSection } from "./CookiePreferencesSection";
import { DangerZoneSection } from "./DangerZoneSection";
import { LinkedAccountsSection, type LinkedAccountsSectionProps } from "./LinkedAccountsSection";
import {
	ResearchParticipationSection,
	type ResearchParticipationSectionProps,
} from "./ResearchParticipationSection";
import { SessionsSection } from "./SessionsSection";
import {
	SlackPreferencesSection,
	type SlackPreferencesSectionProps,
} from "./SlackPreferencesSection";

export interface SettingsPageProps {
	/**
	 * Props for the AiReviewSection component (only rendered when showAiReviewSection is true)
	 */
	aiReviewProps: AiReviewSectionProps;
	/**
	 * Whether to show the AI review section (feature-flagged via account_feature flag)
	 */
	showAiReviewSection: boolean;
	/**
	 * Props for the ResearchParticipationSection component
	 */
	researchProps: ResearchParticipationSectionProps;
	/**
	 * Whether to show the research participation section (requires PostHog)
	 */
	showResearchSection: boolean;
	/**
	 * Props for the LinkedAccountsSection component
	 */
	linkedAccountsProps: LinkedAccountsSectionProps;
	/**
	 * Props for Slack account and message-use preferences.
	 */
	slackPreferencesProps: SlackPreferencesSectionProps;
	/** Whether to show Slack account and message-use preferences. */
	showSlackPreferencesSection?: boolean;
	/**
	 * Called after the account is deleted (logout + redirect).
	 */
	onAccountDeleted: () => void | Promise<void>;
	/**
	 * Whether the settings are still loading
	 */
	isLoading?: boolean;
	/**
	 * Whether the user-settings query failed. When true the settings-backed sections (AI review,
	 * research participation) show an error + retry instead of a fabricated default, so a privacy
	 * toggle is never shown as "on" just because the load failed.
	 */
	settingsError?: boolean;
	/** Retry loading the user settings. */
	onRetrySettings?: () => void;
}

/**
 * SettingsPage component combining all settings sections
 * Provides a consistent layout for the settings page
 */
export function SettingsPage({
	aiReviewProps,
	showAiReviewSection,
	researchProps,
	showResearchSection,
	linkedAccountsProps,
	slackPreferencesProps,
	showSlackPreferencesSection = true,
	onAccountDeleted,
	isLoading = false,
	settingsError = false,
	onRetrySettings,
}: SettingsPageProps) {
	const { isLoading: aiReviewLoading = false, ...aiReviewRest } = aiReviewProps;
	const { isLoading: researchLoading = false, ...researchRest } = researchProps;
	const { isLoading: linkedLoading = false, ...linkedRest } = linkedAccountsProps;
	const { isLoading: slackLoading = false, ...slackRest } = slackPreferencesProps;

	const aiReviewPending = isLoading || aiReviewLoading;
	const researchPending = isLoading || researchLoading;

	return (
		<div className="w-full max-w-3xl mx-auto space-y-8">
			<div className="space-y-1">
				<h1 className="text-3xl font-bold tracking-tight">Settings</h1>
				<p className="text-muted-foreground text-balance">
					Manage your account preferences and settings
				</p>
			</div>

			{settingsError ? (
				(showAiReviewSection || showResearchSection) && (
					<>
						<Separator />
						<section className="space-y-2" aria-labelledby="settings-error-heading">
							<h2 id="settings-error-heading" className="text-xl font-semibold">
								Preferences
							</h2>
							<p className="text-sm text-destructive" role="alert">
								We couldn't load your preferences, so your AI-review and research-participation
								settings aren't shown — we won't display a guessed value for a privacy choice.
							</p>
							{onRetrySettings && (
								<Button variant="outline" size="sm" onClick={onRetrySettings}>
									Retry
								</Button>
							)}
						</section>
					</>
				)
			) : (
				<>
					{showAiReviewSection && (
						<>
							<Separator />
							<AiReviewSection {...aiReviewRest} isLoading={aiReviewPending} />
						</>
					)}

					{showResearchSection && (
						<>
							<Separator />
							<ResearchParticipationSection {...researchRest} isLoading={researchPending} />
						</>
					)}
				</>
			)}

			<Separator />
			<LinkedAccountsSection {...linkedRest} isLoading={isLoading || linkedLoading} />

			{showSlackPreferencesSection && (
				<>
					<Separator />
					<SlackPreferencesSection {...slackRest} isLoading={isLoading || slackLoading} />
				</>
			)}

			<Separator />
			<SessionsSection />

			{optionalIntegrationsAvailable && (
				<>
					<Separator />
					<CookiePreferencesSection />
				</>
			)}

			<Separator />
			<DangerZoneSection onAccountDeleted={onAccountDeleted} />
		</div>
	);
}
