import { CircleAlertIcon, CircleDashedIcon, PlugZapIcon } from "lucide-react";
import type * as React from "react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import type { ConnectionState } from "./sync-format";

interface StateCopy {
	icon: React.ReactNode;
	title: string;
	/** Written per state, and phrased around what the reader does next rather than what the enum is. */
	describe: (displayName: string) => string;
	variant: "default" | "warning";
}

/**
 * What each non-ACTIVE connection state means, in the admin's terms.
 *
 * `connectionState` is a wire enum. Lowercasing it produced "Slack is uninstalled." — a machine token
 * dressed as a sentence, which names the state but explains neither why sync stopped nor what to do
 * about it. Every state that blocks syncing gets written copy and a next action instead, exactly as
 * `HEALTH_LABEL` does for `ConnectionHealth`.
 *
 * ACTIVE is deliberately absent: the steady state has nothing to announce, and the lookup returning
 * `undefined` is what makes {@link ConnectionStateNotice} render nothing.
 */
const STATE_COPY: Partial<Record<ConnectionState, StateCopy>> = {
	PENDING: {
		icon: <CircleDashedIcon />,
		title: "Finishing setup",
		describe: (name) => `${name} isn't live yet. Sync controls unlock once setup completes.`,
		// Benign and self-resolving — no action is owed, so this must not shout.
		variant: "default",
	},
	SUSPENDED: {
		icon: <CircleAlertIcon />,
		title: "Syncing is paused",
		describe: (name) =>
			`${name} was suspended by the provider, so nothing is syncing. Reconnect to resume.`,
		variant: "warning",
	},
	UNINSTALLED: {
		icon: <PlugZapIcon />,
		title: "The app was removed",
		describe: (name) =>
			`${name} was uninstalled from this workspace, so nothing is syncing. Reconnect to resume.`,
		variant: "warning",
	},
};

export interface ConnectionStateNoticeProps {
	connectionState?: ConnectionState;
	/** The integration as the admin knows it — "Slack", "GitHub", "Outline". */
	displayName: string;
	className?: string;
}

/**
 * The one place a non-ACTIVE connection state is explained to an admin.
 *
 * Every integration renders this, so a suspended Slack and a suspended Outline read identically —
 * previously the overview card said "Connection is suspended." and the Slack page said "Slack is
 * suspended." for the same fact. SUSPENDED/UNINSTALLED mean *sync is stopped*, which is a warning and
 * not a muted footnote; PENDING resolves on its own and stays quiet.
 *
 * Renders nothing for ACTIVE or for a connection that doesn't exist — neither has anything to explain.
 */
export function ConnectionStateNotice({
	connectionState,
	displayName,
	className,
}: ConnectionStateNoticeProps) {
	const copy = connectionState ? STATE_COPY[connectionState] : undefined;
	if (!copy) {
		return null;
	}

	return (
		<Alert variant={copy.variant} className={className}>
			{copy.icon}
			<AlertTitle>{copy.title}</AlertTitle>
			<AlertDescription>{copy.describe(displayName)}</AlertDescription>
		</Alert>
	);
}
