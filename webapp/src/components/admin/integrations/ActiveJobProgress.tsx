import type { SyncJob } from "@/api/types.gen";
import { Progress } from "@/components/ui/progress";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import { JOB_TYPE_LABEL, jobCurrentStep } from "./sync-format";

export interface ActiveJobProgressProps {
	job?: SyncJob | null;
	className?: string;
}

export function ActiveJobProgress({ job, className }: ActiveJobProgressProps) {
	if (!job) return null;

	const processed = job.itemsProcessed ?? 0;
	const total = job.itemsTotal;
	const currentStep = jobCurrentStep(job);

	if (total != null && total > 0) {
		const percent = Math.min(100, Math.round((processed / total) * 100));
		return (
			<div className={cn("flex items-center gap-2 text-sm text-muted-foreground", className)}>
				<Progress
					value={percent}
					className="w-32"
					aria-label="Sync progress"
					getAriaValueText={() =>
						`${processed} of ${total} items${currentStep ? `, ${currentStep}` : ""}`
					}
				/>
				<span className="tabular-nums">
					{processed}/{total}
				</span>
				{currentStep && <span className="truncate">{currentStep}</span>}
			</div>
		);
	}

	return (
		<div className={cn("flex items-center gap-2 text-sm text-muted-foreground", className)}>
			<Spinner className="size-4" />
			<span>{currentStep ?? `${JOB_TYPE_LABEL[job.type]} running…`}</span>
		</div>
	);
}
