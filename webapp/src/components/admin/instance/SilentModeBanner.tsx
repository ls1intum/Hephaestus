import { Link } from "@tanstack/react-router";
import { VolumeX } from "lucide-react";
import type { InstanceSettings } from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import { Alert, AlertAction, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";

interface SilentModeBannerProps {
	settings: InstanceSettings;
}

/**
 * The prominent state banner shown across the whole admin console while the instance-wide silent
 * mode is engaged (#1386/#1357) — an engaged emergency brake must be impossible to miss. The parent
 * decides visibility; this renders the engaged state only.
 */
export function SilentModeBanner({ settings }: SilentModeBannerProps) {
	return (
		<Alert variant="destructive">
			<VolumeX aria-hidden />
			<AlertTitle>Silent mode is engaged — nothing is being delivered</AlertTitle>
			<AlertDescription>
				Hephaestus is not posting practice feedback or Slack messages anywhere on this instance.
				{settings.silentModeChangedBy || settings.silentModeChangedAt ? (
					<>
						{" Engaged"}
						{settings.silentModeChangedBy ? ` by ${settings.silentModeChangedBy}` : ""}
						{settings.silentModeChangedAt ? (
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
				<Button variant="outline" size="sm" render={<Link to="/admin/settings" />}>
					Manage
				</Button>
			</AlertAction>
		</Alert>
	);
}
