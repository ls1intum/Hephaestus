import { Separator } from "@/components/ui/separator";
import { AccountSection, type AccountSectionProps } from "./AccountSection";
import { AiReviewSection, type AiReviewSectionProps } from "./AiReviewSection";
import { LinkedAccountsSection, type LinkedAccountsSectionProps } from "./LinkedAccountsSection";
import {
	ResearchParticipationSection,
	type ResearchParticipationSectionProps,
} from "./ResearchParticipationSection";

export interface SettingsPageProps {
	/**
	 * Props for the AiReviewSection component (only rendered when showAiReviewSection is true)
	 */
	aiReviewProps: AiReviewSectionProps;
	/**
	 * Whether to show the AI review section (feature-flagged via Keycloak role)
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
	 * Props for the AccountSection component
	 */
	accountProps: AccountSectionProps;
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
	accountProps,
	isLoading = false,
}: SettingsPageProps) {
	const { isLoading: aiReviewLoading = false, ...aiReviewRest } = aiReviewProps;
	const { isLoading: researchLoading = false, ...researchRest } = researchProps;
	const { isLoading: linkedLoading = false, ...linkedRest } = linkedAccountsProps;
	const { isLoading: accountLoading = false, ...accountRest } = accountProps;

	const aiReviewPending = isLoading || aiReviewLoading;
	const researchPending = isLoading || researchLoading;
	const accountPending = isLoading || accountLoading;
	const showLinkedAccounts =
		linkedRest.isError || linkedLoading || isLoading || linkedRest.accounts.length > 1;

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

			{showLinkedAccounts && (
				<>
					<Separator />
					<LinkedAccountsSection {...linkedRest} isLoading={isLoading || linkedLoading} />
				</>
			)}

			<Separator />

			<AccountSection {...accountRest} isLoading={accountPending} />
		</div>
	);
}
