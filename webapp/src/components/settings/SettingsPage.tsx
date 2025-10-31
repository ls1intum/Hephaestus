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

	return (
		<div className="flex flex-col gap-4">
			<h1 className="text-3xl font-bold">Settings</h1>
			<NotificationsSection
				{...notificationsRest}
				isLoading={isLoading || notificationsLoading}
			/>
			{isPosthogEnabled && (
				<ResearchParticipationSection
					{...researchRest}
					isLoading={isLoading || researchLoading}
				/>
			)}
			<AccountSection
				{...accountRest}
				isLoading={isLoading || accountLoading}
			/>
		</div>
	);
}
