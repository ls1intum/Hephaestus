import { RefreshCwIcon } from "lucide-react";
import type { SyncJob } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";

export interface SyncNowButtonProps {
	onClick: () => void;
	isTriggering?: boolean;
	activeJob?: SyncJob | null;
	label?: string;
}

export function SyncNowButton({
	onClick,
	isTriggering = false,
	activeJob,
	label = "Sync now",
}: SyncNowButtonProps) {
	const disabled = isTriggering || activeJob != null;
	const text = activeJob != null ? "Syncing…" : isTriggering ? "Starting…" : label;

	return (
		<>
			<Button variant="outline" size="sm" onClick={onClick} disabled={disabled}>
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
