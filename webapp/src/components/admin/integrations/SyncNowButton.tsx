import { RefreshCwIcon } from "lucide-react";
import type { SyncJob } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { JOB_TYPE_LABEL, type SyncTriggerType } from "./sync-format";

export interface SyncNowButtonProps {
	onClick: () => void;
	/**
	 * The operation this button just started, or `null` when none is in flight.
	 *
	 * A type rather than a boolean because the surface has one trigger for two operations: a backfill
	 * launched from the split menu must announce itself as a backfill, and a flag could only say that
	 * *something* started.
	 */
	triggeringType?: SyncTriggerType | null;
	activeJob?: SyncJob | null;
}

/**
 * The manual-sync trigger, and the only one on its card.
 *
 * There used to be two — Sync and Backfill, side by side, sharing one mutation — which forced a
 * protocol between them so that a button disabled by its neighbour's run did not claim to be starting
 * work nobody had asked it for. Making Backfill a menu item on this button deleted the protocol along
 * with the second button: there is one visible trigger, so pending means *this* trigger.
 */
export function SyncNowButton({ onClick, triggeringType = null, activeJob }: SyncNowButtonProps) {
	const disabled = triggeringType != null || activeJob != null;
	const text = activeJob != null ? "Syncing…" : triggeringType != null ? "Starting…" : "Sync now";

	return (
		<>
			<Button variant="outline" size="sm" onClick={onClick} disabled={disabled}>
				{disabled ? (
					<Spinner className="size-4" aria-hidden />
				) : (
					<RefreshCwIcon className="size-4" />
				)}
				{text}
			</Button>
			{/* Announce sync start/progress to assistive tech; a plain button-label swap is not reliably
			announced. Empty when idle, so nothing is spoken until a run begins. Once the server has a job,
			the announcement names *that* job rather than the trigger's intent — a backfill request that
			landed behind a running reconciliation must not report the reconciliation as a backfill. */}
			<span role="status" aria-live="polite" className="sr-only">
				{activeJob != null
					? `${JOB_TYPE_LABEL[activeJob.type]} in progress`
					: triggeringType != null
						? `Starting ${JOB_TYPE_LABEL[triggeringType].toLowerCase()}`
						: ""}
			</span>
		</>
	);
}
