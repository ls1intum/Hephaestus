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
	aiReviewProps: AiReviewSectionProps;
	researchProps: ResearchParticipationSectionProps;
	showResearchSection: boolean;
	linkedAccountsProps: LinkedAccountsSectionProps;
	slackPreferencesProps: SlackPreferencesSectionProps;
	showSlackPreferencesSection?: boolean;
	onAccountDeleted: () => void | Promise<void>;
	isLoading?: boolean;
	settingsError?: boolean;
	onRetrySettings?: () => void;
}

export function SettingsPage({
	aiReviewProps,
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
				<>
					<Separator />
					<section className="space-y-2" aria-labelledby="settings-error-heading">
						<h2 id="settings-error-heading" className="text-xl font-semibold">
							Preferences
						</h2>
						<p className="text-sm text-destructive" role="alert">
							We couldn't load your preferences, so your feedback and research settings aren't
							shown.
						</p>
						{onRetrySettings && (
							<Button variant="outline" size="sm" onClick={onRetrySettings}>
								Retry
							</Button>
						)}
					</section>
				</>
			) : (
				<>
					<Separator />
					<AiReviewSection {...aiReviewRest} isLoading={aiReviewPending} />

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
