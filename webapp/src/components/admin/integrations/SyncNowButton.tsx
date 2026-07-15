import { RefreshCwIcon } from "lucide-react";
import type { SyncJob } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";

export interface SyncNowButtonProps {
	onClick: () => void;
	/** The mutation is in flight (request sent, response not yet back). */
	isTriggering?: boolean;
	/** The connection's current active job, if any — disables the button (one-active-job guard) and
	 * relabels it, matching the server's idempotent "200 + active job" response. */
	activeJob?: SyncJob | null;
	/** Defaults to "Sync now"; the Backfill entry point passes "Backfill" instead. */
	label?: string;
	variant?: React.ComponentProps<typeof Button>["variant"];
	size?: React.ComponentProps<typeof Button>["size"];
}

/**
 * The one "trigger a sync job" control. Disabled while a job is already running for the
 * connection (server-side one-active-job guard mirrored client-side) and while the trigger
 * mutation itself is in flight.
 */
export function SyncNowButton({
	onClick,
	isTriggering = false,
	activeJob,
	label = "Sync now",
	variant = "outline",
	size = "sm",
}: SyncNowButtonProps) {
	const disabled = isTriggering || activeJob != null;
	const text = activeJob != null ? "Syncing…" : isTriggering ? "Starting…" : label;

	return (
		<>
			<Button variant={variant} size={size} onClick={onClick} disabled={disabled}>
				{isTriggering || activeJob != null ? (
					<Spinner className="size-4" />
				) : (
					<RefreshCwIcon className="size-4" />
				)}
				{text}
			</Button>
			{/* Announce sync start/progress to assistive tech; a plain button-label swap is not
			reliably announced. Empty when idle, so nothing is spoken until a run begins. */}
			<span role="status" aria-live="polite" className="sr-only">
				{activeJob != null ? "Sync in progress" : isTriggering ? "Starting sync" : ""}
			</span>
		</>
	);
}
