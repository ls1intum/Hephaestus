import type { AgentJob } from "@/api/types.gen";
import { isCancellable, isDeliveryRetryable } from "@/components/admin/ai/job-utils";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
	AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";

export interface ReviewRunActionsProps {
	job: AgentJob;
	isCancelling: boolean;
	isRetrying: boolean;
	onCancel: () => void;
	onRetry: () => void;
}

export function ReviewRunActions({
	job,
	isCancelling,
	isRetrying,
	onCancel,
	onRetry,
}: ReviewRunActionsProps) {
	if (!isCancellable(job.status) && !isDeliveryRetryable(job)) return null;
	return (
		<div className="flex gap-2">
			{isCancellable(job.status) && (
				<AlertDialog>
					<AlertDialogTrigger
						render={
							<Button variant="outline" disabled={isCancelling}>
								{isCancelling ? "Cancelling…" : "Cancel review"}
							</Button>
						}
					/>
					<AlertDialogContent>
						<AlertDialogHeader>
							<AlertDialogTitle>Cancel this review?</AlertDialogTitle>
							<AlertDialogDescription>
								The running review stops and cannot be resumed.
							</AlertDialogDescription>
						</AlertDialogHeader>
						<AlertDialogFooter>
							<AlertDialogCancel>Keep review running</AlertDialogCancel>
							<AlertDialogAction variant="destructive" disabled={isCancelling} onClick={onCancel}>
								Cancel review
							</AlertDialogAction>
						</AlertDialogFooter>
					</AlertDialogContent>
				</AlertDialog>
			)}
			{isDeliveryRetryable(job) && (
				<AlertDialog>
					<AlertDialogTrigger
						render={
							<Button disabled={isRetrying}>
								{isRetrying ? "Retrying…" : "Retry feedback comment"}
							</Button>
						}
					/>
					<AlertDialogContent>
						<AlertDialogHeader>
							<AlertDialogTitle>Retry the feedback comment?</AlertDialogTitle>
							<AlertDialogDescription>
								Hephaestus will try to post the failed comment again.
							</AlertDialogDescription>
						</AlertDialogHeader>
						<AlertDialogFooter>
							<AlertDialogCancel>Cancel</AlertDialogCancel>
							<AlertDialogAction disabled={isRetrying} onClick={onRetry}>
								Retry feedback comment
							</AlertDialogAction>
						</AlertDialogFooter>
					</AlertDialogContent>
				</AlertDialog>
			)}
		</div>
	);
}
