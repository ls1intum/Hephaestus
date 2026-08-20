import { CheckCircle2Icon, CircleMinusIcon, OctagonXIcon } from "lucide-react";
import type { DeliveryPolicyTrace as DeliveryPolicyTraceData } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import {
	deliveryCheckLabels,
	deliveryReasonLabels,
	deliveryStageLabels,
	deliveryStatusLabels,
	deliverySurfaceLabels,
} from "./delivery-policy-vocabulary";

export interface DeliveryPolicyTraceProps {
	evaluations: DeliveryPolicyTraceData[];
}

export function DeliveryPolicyTrace({ evaluations }: DeliveryPolicyTraceProps) {
	if (evaluations.length === 0) return null;

	return (
		<details className="rounded-lg border bg-card p-4 text-sm">
			<summary className="cursor-pointer font-medium">Technical delivery policy trace</summary>
			<p className="mt-2 text-xs text-muted-foreground">
				Checks run in order. After a denial, later checks are not reached.
			</p>
			<ol className="mt-4 space-y-4">
				{evaluations.map((evaluation, index) => (
					<li
						key={`${evaluation.reviewId}-${evaluation.evaluatedAt.toISOString()}-${index}`}
						className="space-y-2 rounded-md bg-muted/40 p-3"
					>
						<p className="font-medium">
							{deliverySurfaceLabels[evaluation.surface]} · {deliveryStageLabels[evaluation.stage]}{" "}
							· admitted revision {evaluation.admittedRevision}
							{evaluation.evaluatedRevision == null
								? ""
								: ` · evaluated revision ${evaluation.evaluatedRevision}`}
						</p>
						{evaluation.decisiveReason && (
							<p className="text-xs">
								Stopped because: {deliveryReasonLabels[evaluation.decisiveReason]}
							</p>
						)}
						<p className="text-xs text-muted-foreground">
							Scope: {evaluation.facts.repository ?? "no repository"}
							{evaluation.facts.baseBranch ? ` / ${evaluation.facts.baseBranch}` : ""} ·{" "}
							{evaluation.facts.repositoryMode?.toLowerCase() ?? "not applicable"} repositories ·{" "}
							{evaluation.facts.personMode?.toLowerCase() ?? "not applicable"} people · subject{" "}
							{evaluation.facts.subject?.toLowerCase().replaceAll("_", " ") ?? "not applicable"}
						</p>
						<ul className="space-y-1">
							{evaluation.checks.map((check) => {
								const Icon =
									check.status === "PASSED"
										? CheckCircle2Icon
										: check.status === "DENIED"
											? OctagonXIcon
											: CircleMinusIcon;
								return (
									<li key={check.check} className="flex items-center justify-between gap-3 text-xs">
										<span className="flex items-center gap-2">
											<Icon aria-hidden className="size-3.5" />
											{deliveryCheckLabels[check.check]}
										</span>
										<Badge
											variant={check.status === "DENIED" ? "outline" : "secondary"}
											className={
												check.status === "DENIED"
													? "border-destructive/40 text-destructive"
													: undefined
											}
										>
											{deliveryStatusLabels[check.status]}
										</Badge>
									</li>
								);
							})}
						</ul>
					</li>
				))}
			</ol>
		</details>
	);
}
