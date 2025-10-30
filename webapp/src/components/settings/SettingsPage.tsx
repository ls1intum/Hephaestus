import { AccountSection, type AccountSectionProps } from "./AccountSection";
import {
	NotificationsSection,
	type NotificationsSectionProps,
} from "./NotificationsSection";
import { ResearchSection, type ResearchSectionProps } from "./ResearchSection";

export interface SettingsPageProps {
	/**
	 * Props for the NotificationsSection component
	 */
	notificationsProps: NotificationsSectionProps;
	/**
	 * Props for the ResearchSection component
	 */
	researchProps: ResearchSectionProps;
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
	return (
		<div className="flex flex-col gap-4">
			<h1 className="text-3xl font-bold">Settings</h1>
			<NotificationsSection {...notificationsProps} isLoading={isLoading} />
			<ResearchSection {...researchProps} isLoading={isLoading} />
			<AccountSection {...accountProps} isLoading={isLoading} />
		</div>
	);
}
