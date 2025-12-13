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
import {
	SlackConnectionSection,
	type SlackConnectionSectionProps,
} from "./SlackConnectionSection";

export interface SettingsPageProps {
	notificationsProps: NotificationsSectionProps;
	researchProps: ResearchParticipationSectionProps;
	accountProps: AccountSectionProps;
	slackProps: SlackConnectionSectionProps;
	isLoading?: boolean;
}

export function SettingsPage({
	notificationsProps,
	researchProps,
	slackProps,
	accountProps,
	isLoading = false,
}: SettingsPageProps) {
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
				{...notificationsProps}
				isLoading={isLoading || notificationsProps.isLoading}
			/>

			{isPosthogEnabled && (
				<>
					<Separator />
					<ResearchParticipationSection
						{...researchProps}
						isLoading={isLoading || researchProps.isLoading}
					/>
				</>
			)}

			<Separator />
			<SlackConnectionSection
				{...slackProps}
				isLoading={isLoading || slackProps.isLoading}
			/>

			<Separator />
			<AccountSection
				{...accountProps}
				isLoading={isLoading || accountProps.isLoading}
			/>
		</div>
	);
}
