import type { AgentJob } from "@/api/types.gen";
import { formatTokens, modelLabel } from "@/components/admin/ai/job-utils";
import { JOB_TYPE_LABELS } from "@/components/admin/usage/usage-utils";
import { DetailRow } from "@/components/common/DetailRow";
import { RelativeTime } from "@/components/common/RelativeTime";
import { ReviewTechnicalDetails } from "./ReviewTechnicalDetails";

export function ReviewRunTechnicalDetails({ job }: { job: AgentJob }) {
	return (
		<ReviewTechnicalDetails className="space-y-5">
			<dl className="divide-y">
				<DetailRow label="Review ID">
					<code>{job.id}</code>
				</DetailRow>
				<DetailRow label="Review type">{JOB_TYPE_LABELS[job.jobType]}</DetailRow>
				<DetailRow label="Model">{modelLabel(job)}</DetailRow>
				{job.completedAt && (
					<DetailRow label="Completed">
						<RelativeTime value={job.completedAt} />
					</DetailRow>
				)}
				<DetailRow label="Input tokens">{formatTokens(job.llmTotalInputTokens)}</DetailRow>
				<DetailRow label="Output tokens">{formatTokens(job.llmTotalOutputTokens)}</DetailRow>
				<DetailRow label="Reasoning tokens">{formatTokens(job.llmTotalReasoningTokens)}</DetailRow>
				<DetailRow label="Model calls">{formatTokens(job.llmTotalCalls)}</DetailRow>
				{job.errorMessage && (
					<DetailRow label="Failure detail">
						<pre className="max-h-48 overflow-auto whitespace-pre-wrap break-words text-xs">
							{job.errorMessage}
						</pre>
					</DetailRow>
				)}
			</dl>
			<div>
				<h4 className="mb-2 text-sm font-medium">Configuration snapshot</h4>
				<pre className="max-h-80 overflow-auto rounded-md bg-muted p-3 text-xs">
					{JSON.stringify(job.configSnapshot, null, 2)}
				</pre>
			</div>
		</ReviewTechnicalDetails>
	);
}
