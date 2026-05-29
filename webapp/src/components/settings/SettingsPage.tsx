import { Separator } from "@/components/ui/separator";
import { AiReviewSection, type AiReviewSectionProps } from "./AiReviewSection";
import { DangerZoneSection } from "./DangerZoneSection";
import { LinkedAccountsSection, type LinkedAccountsSectionProps } from "./LinkedAccountsSection";
import {
	ResearchParticipationSection,
	type ResearchParticipationSectionProps,
} from "./ResearchParticipationSection";
import { SessionsSection } from "./SessionsSection";

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
	 * Called after the account is deleted (logout + redirect).
	 */
	onAccountDeleted: () => void | Promise<void>;
	/**
	 * Whether the settings are still loading
	 */
	isLoading?: boolean;
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
	onAccountDeleted,
	isLoading = false,
}: SettingsPageProps) {
	const { isLoading: aiReviewLoading = false, ...aiReviewRest } = aiReviewProps;
	const { isLoading: researchLoading = false, ...researchRest } = researchProps;
	const { isLoading: linkedLoading = false, ...linkedRest } = linkedAccountsProps;

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

			<Separator />
			<LinkedAccountsSection {...linkedRest} isLoading={isLoading || linkedLoading} />

			<Separator />
			<SessionsSection />

			<Separator />
			<DangerZoneSection onAccountDeleted={onAccountDeleted} />
		</div>
	);
}
