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
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";

export interface AccountSectionProps {
	/**
	 * Callback when account deletion is confirmed
	 */
	onDeleteAccount: () => void;
	/**
	 * Whether the delete action is in progress
	 */
	isDeleting?: boolean;
	/**
	 * Whether the component is in loading state
	 */
	isLoading?: boolean;
}

/**
 * AccountSection component for account management
 * Provides account deletion functionality with confirmation dialog
 */
export function AccountSection({
	onDeleteAccount,
	isDeleting = false,
	isLoading = false,
}: AccountSectionProps) {
	return (
		<div className="sm:w-2/3 w-full flex flex-col gap-3">
			<h2 className="text-lg font-semibold">Account</h2>
			<div className="flex flex-row items-center justify-between">
				{isLoading ? (
					<>
						<span className="flex-col items-start">
							<Skeleton className="h-5 w-32 mb-2" />
							<Skeleton className="h-4 w-80" />
						</span>
						<Skeleton className="h-9 w-16" />
					</>
				) : (
					<>
						<span className="flex-col items-start">
							<h3>Delete account</h3>
							<Label className="font-light">
								Permanently delete your account and remove your data from our
								servers.
							</Label>
						</span>
						<AlertDialog>
							<AlertDialogTrigger asChild>
								<Button variant="outline" disabled={isDeleting}>
									Delete
								</Button>
							</AlertDialogTrigger>
							<AlertDialogContent>
								<AlertDialogHeader>
									<AlertDialogTitle>Are you absolutely sure?</AlertDialogTitle>
									<AlertDialogDescription>
										This action cannot be undone. This will permanently delete
										your account and remove your data from our servers.
									</AlertDialogDescription>
								</AlertDialogHeader>
								<AlertDialogFooter>
									<AlertDialogCancel>Cancel</AlertDialogCancel>
									<AlertDialogAction
										onClick={onDeleteAccount}
										disabled={isDeleting}
									>
										Delete account
									</AlertDialogAction>
								</AlertDialogFooter>
							</AlertDialogContent>
						</AlertDialog>
					</>
				)}
			</div>
		</div>
	);
}
