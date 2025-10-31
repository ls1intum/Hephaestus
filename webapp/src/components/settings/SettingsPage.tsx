import { Separator } from "@/components/ui/separator";
import { isPosthogEnabled } from "@/integrations/posthog/config";
import { AccountSection, type AccountSectionProps } from "./AccountSection";
import {
	NotificationsSection,
	type NotificationsSectionProps,
} from "./NotificationsSection";
import {
	ResearchParticipationSection,
	type ResearchParticipationSectionProps,
} from "./ResearchParticipationSection";

export interface SettingsPageProps {
	/**
	 * Props for the NotificationsSection component
	 */
	notificationsProps: NotificationsSectionProps;
	/**
	 * Props for the ResearchParticipationSection component
	 */
	researchProps: ResearchParticipationSectionProps;
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
	notificationsProps,
	researchProps,
	accountProps,
	isLoading = false,
}: SettingsPageProps) {
	const { isLoading: notificationsLoading = false, ...notificationsRest } =
		notificationsProps;
	const { isLoading: researchLoading = false, ...researchRest } = researchProps;
	const { isLoading: accountLoading = false, ...accountRest } = accountProps;

	const notificationsPending = isLoading || notificationsLoading;
	const researchPending = isLoading || researchLoading;
	const accountPending = isLoading || accountLoading;
	const showResearchSection = isPosthogEnabled;

	return (
		<div className="w-full max-w-3xl mx-auto space-y-8">
			<div className="space-y-1">
				<h1 className="text-3xl font-bold tracking-tight">Settings</h1>
				<p className="text-muted-foreground text-balance">
					Manage your account preferences and settings
				</p>
			</div>

			<Separator />

			<NotificationsSection
				{...notificationsRest}
				isLoading={notificationsPending}
			/>

			{showResearchSection && (
				<>
					<Separator />
					<ResearchParticipationSection
						{...researchRest}
						isLoading={researchPending}
					/>
				</>
			)}

			<Separator />

			<AccountSection
				{...accountRest}
				isLoading={accountPending}
			/>
		</div>
	);
}
