import { Link } from "@tanstack/react-router";
import { VolumeX } from "lucide-react";
import type { InstanceSettings } from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import { Alert, AlertAction, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { buttonVariants } from "@/components/ui/button";
import { asDate } from "@/lib/dates";

export interface SilentModeBannerProps {
	settings: InstanceSettings;
}

export function SilentModeBanner({ settings }: SilentModeBannerProps) {
	const engagedAt = asDate(settings.silentModeChangedAt);

	return (
		<Alert variant="destructive">
			<VolumeX aria-hidden />
			<AlertTitle>Silent mode is engaged — workspace delivery is blocked</AlertTitle>
			<AlertDescription>
				Practice feedback and workspace Slack messages are suppressed across this instance.
				{settings.silentModeChangedBy || engagedAt ? (
					<>
						{" Engaged"}
						{settings.silentModeChangedBy ? ` by ${settings.silentModeChangedBy}` : ""}
						{engagedAt ? (
							<>
								{" "}
								<RelativeTime value={settings.silentModeChangedAt} tooltip={false} />
							</>
						) : null}
						{settings.silentModeReason ? ` — “${settings.silentModeReason}”` : ""}.
					</>
				) : null}
			</AlertDescription>
			<AlertAction>
				<Link to="/admin/settings" className={buttonVariants({ variant: "outline", size: "sm" })}>
					Manage
				</Link>
			</AlertAction>
		</Alert>
	);
}
