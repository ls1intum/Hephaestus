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

export interface ReviewActionsProps {
	isCancelling: boolean;
	onCancel: () => void;
}

/** Cancel for a review that still runs: it asks first, because a cancelled review cannot resume. */
export function ReviewActions({ isCancelling, onCancel }: ReviewActionsProps) {
	return (
		<AlertDialog>
			<AlertDialogTrigger
				render={
					<Button variant="outline" disabled={isCancelling}>
						{isCancelling ? "Canceling…" : "Cancel review"}
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
	);
}
