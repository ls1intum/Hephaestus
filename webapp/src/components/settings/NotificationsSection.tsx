import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";

export interface NotificationsSectionProps {
	/**
	 * Whether email notifications are enabled
	 */
	receiveNotifications: boolean;
	/**
	 * Callback when notifications setting is changed
	 */
	onToggleNotifications: (checked: boolean) => void;
	/**
	 * Whether the component is in loading state
	 */
	isLoading?: boolean;
}

/**
 * NotificationsSection component for managing notification preferences
 * Allows users to toggle email notifications
 */
export function NotificationsSection({
	receiveNotifications,
	onToggleNotifications,
	isLoading = false,
}: NotificationsSectionProps) {
	const pending = Boolean(isLoading);

	return (
		<section className="space-y-4" aria-labelledby="notifications-heading">
			<div className="space-y-1">
				<h2 id="notifications-heading" className="text-xl font-semibold">
					Notifications
				</h2>
				<p className="text-sm text-muted-foreground">
					Configure how you receive updates
				</p>
			</div>

			<div className="flex items-start justify-between gap-6 py-4">
				<div className="space-y-1 flex-1">
					<Label
						htmlFor="email-notifications"
						className="text-base font-medium cursor-pointer"
					>
						Email notifications
					</Label>
					<p className="text-sm text-muted-foreground leading-relaxed">
						Receive email notifications for newly detected bad practices and
						reminders.
					</p>
				</div>
				<Switch
					id="email-notifications"
					className="mt-1"
					checked={receiveNotifications}
					onCheckedChange={onToggleNotifications}
					disabled={pending}
					aria-busy={pending}
					aria-label="Toggle email notifications"
				/>
			</div>
		</section>
	);
}
