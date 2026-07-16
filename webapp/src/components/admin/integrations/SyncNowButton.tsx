import { RefreshCwIcon } from "lucide-react";
import type { SyncJob } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { JOB_TYPE_LABEL } from "./sync-format";

export interface SyncNowButtonProps {
	onClick: () => void;
	/**
	 * *This* button's own operation is being started. Only the button whose operation is in flight may
	 * pass true — a caller that drives two triggers off one mutation must discriminate first, or both
	 * buttons claim to be starting and the UI lies about which one the admin pressed.
	 */
	isTriggering?: boolean;
	/**
	 * A *different* operation is in flight. Disables this button without claiming it started: the
	 * server takes one job at a time, so the trigger is genuinely unavailable, but saying "Starting…"
	 * here would name the wrong operation.
	 */
	isBusy?: boolean;
	activeJob?: SyncJob | null;
	label?: string;
	/**
	 * The operation named in the screen-reader announcement ("Starting backfill"). Separate from
	 * {@link label}, which is button copy and does not read as a noun mid-sentence ("Sync now").
	 */
	operationLabel?: string;
}

export function SyncNowButton({
	onClick,
	isTriggering = false,
	isBusy = false,
	activeJob,
	label = "Sync now",
	operationLabel = "sync",
}: SyncNowButtonProps) {
	const disabled = isTriggering || isBusy || activeJob != null;
	const text = activeJob != null ? "Syncing…" : isTriggering ? "Starting…" : label;
	// The spinner is a claim that *this* operation is moving, so it follows `isTriggering`/`activeJob`
	// and never `isBusy`: a button disabled by someone else's run keeps its idle icon and idle label.
	const isOwnWorkInFlight = isTriggering || activeJob != null;

	return (
		<>
			<Button variant="outline" size="sm" onClick={onClick} disabled={disabled}>
				{isOwnWorkInFlight ? (
					<Spinner className="size-4" aria-hidden />
				) : (
					<RefreshCwIcon className="size-4" />
				)}
				{text}
			</Button>
			{/* Announce sync start/progress to assistive tech; a plain button-label swap is not
			reliably announced. Empty when idle, so nothing is spoken until a run begins. A run in
			flight is named after the job the server is actually running, not after this button's own
			operation — every trigger sharing the card sees the same job, so naming it "backfill" from
			the Backfill button would misreport a reconciliation. */}
			<span role="status" aria-live="polite" className="sr-only">
				{activeJob != null
					? `${JOB_TYPE_LABEL[activeJob.type]} in progress`
					: isTriggering
						? `Starting ${operationLabel}`
						: ""}
			</span>
		</>
	);
}
