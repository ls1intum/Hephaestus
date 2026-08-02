import { Link } from "@tanstack/react-router";
import { VolumeX } from "lucide-react";
import type { InstanceSettings } from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import { Alert, AlertAction, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { asDate } from "@/lib/dates";

const STALE_AFTER_MS = 24 * 60 * 60 * 1000;

interface SilentModeBannerProps {
	settings: InstanceSettings;
}

/** The parent decides visibility; this renders the engaged state only. */
export function SilentModeBanner({ settings }: SilentModeBannerProps) {
	const engagedAt = asDate(settings.silentModeChangedAt);
	// A brake left on is the likelier incident than a brake released early, so an old one asks to be re-decided.
	const stale = engagedAt != null && Date.now() - engagedAt.getTime() > STALE_AFTER_MS;

	return (
		<Alert variant="destructive">
			<VolumeX aria-hidden />
			<AlertTitle>
				{stale
					? "Silent mode is still engaged — is that still intentional?"
					: "Silent mode is engaged — nothing is being delivered"}
			</AlertTitle>
			<AlertDescription>
				Hephaestus is not posting practice feedback or Slack messages anywhere on this instance.
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
				<Button variant="outline" size="sm" render={<Link to="/admin/settings" />}>
					Manage
				</Button>
			</AlertAction>
		</Alert>
	);
}
