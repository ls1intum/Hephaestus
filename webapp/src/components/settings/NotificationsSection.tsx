import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
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
	return (
		<div className="sm:w-2/3 w-full flex flex-col gap-3">
			<h2 className="text-lg font-semibold">Notifications</h2>
			<div className="flex flex-row items-center justify-between">
				{isLoading ? (
					<>
						<span className="flex-col items-start">
							<Skeleton className="h-5 w-36 mb-2" />
							<Skeleton className="h-4 w-80" />
						</span>
						<Skeleton className="h-5 w-10 rounded-full mr-2" />
					</>
				) : (
					<>
						<span className="flex-col items-start">
							<h3>Email notifications</h3>
							<Label className="font-light">
								Receive email notifications for newly detected bad practices and
								reminders.
							</Label>
						</span>
						<Switch
							className="mr-2"
							checked={receiveNotifications}
							onCheckedChange={onToggleNotifications}
							disabled={isLoading}
						/>
					</>
				)}
			</div>
		</div>
	);
}
