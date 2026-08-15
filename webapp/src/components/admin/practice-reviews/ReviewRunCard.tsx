import { CopyIcon } from "lucide-react";
import { toast } from "sonner";
import type { AgentJob } from "@/api/types.gen";
import { formatTokens, modelLabel } from "@/components/admin/ai/job-utils";
import { JOB_TYPE_LABELS } from "@/components/admin/usage/usage-utils";
import { RelativeTime } from "@/components/common/RelativeTime";
import { Button } from "@/components/ui/button";
import { ReviewFact, ReviewFactGrid } from "./ReviewDetailHeader";

export interface ReviewRunCardProps {
	job: AgentJob;
}

/**
 * How the review ran: what it read the work with, how much of it there was, and when it finished.
 *
 * <p>On the page rather than behind a disclosure: the model and the token count are the two facts an
 * operator checks when a review costs more than it should or answers worse than it used to, and the
 * review's own id is already in the address bar.
 *
 * <p>The configuration snapshot is copied and never rendered. It is a machine artefact, so the useful
 * action on it is putting it where a machine can read it.
 */
export function ReviewRunCard({ job }: ReviewRunCardProps) {
	const copyConfiguration = async () => {
		try {
			await navigator.clipboard.writeText(JSON.stringify(job.configSnapshot, null, 2));
			toast.success("Configuration copied");
		} catch {
			toast.error("Could not copy to clipboard");
		}
	};

	return (
		<section aria-labelledby="run-card-heading" className="space-y-3">
			<div className="flex flex-wrap items-center justify-between gap-2">
				<h3 id="run-card-heading" className="text-lg font-semibold">
					How this review ran
				</h3>
				<Button variant="outline" size="sm" onClick={copyConfiguration}>
					<CopyIcon aria-hidden />
					Copy configuration
				</Button>
			</div>
			<ReviewFactGrid>
				<ReviewFact label="Reviewed as">{JOB_TYPE_LABELS[job.jobType]}</ReviewFact>
				<ReviewFact label="Model">{modelLabel(job)}</ReviewFact>
				<ReviewFact label="Finished">
					{job.completedAt ? <RelativeTime value={job.completedAt} /> : "Not yet"}
				</ReviewFact>
				<ReviewFact label="Model calls">{formatTokens(job.llmTotalCalls)}</ReviewFact>
				<ReviewFact label="Tokens read">{formatTokens(job.llmTotalInputTokens)}</ReviewFact>
				<ReviewFact label="Tokens written">
					{/* Reasoning tokens are billed as output and were shown on their own row, which read as
					    a third, separate quantity. Naming the whole and the part says what the bill is. */}
					{formatTokens(job.llmTotalOutputTokens)}
					{job.llmTotalReasoningTokens != null && job.llmTotalReasoningTokens > 0 && (
						<span className="text-muted-foreground">
							{" "}
							({formatTokens(job.llmTotalReasoningTokens)} of it reasoning)
						</span>
					)}
				</ReviewFact>
			</ReviewFactGrid>
			{job.errorMessage && (
				<div className="space-y-1 rounded-lg border border-destructive/40 p-3">
					<p className="text-sm font-medium">What went wrong</p>
					<pre className="max-h-48 overflow-auto whitespace-pre-wrap break-words text-xs text-muted-foreground">
						{job.errorMessage}
					</pre>
				</div>
			)}
		</section>
	);
}
