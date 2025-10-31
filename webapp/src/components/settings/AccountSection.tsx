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
	const pending = Boolean(isLoading);
	const processing = Boolean(isDeleting);
	return (
		<section className="space-y-4" aria-labelledby="account-heading">
			<div className="space-y-1">
				<h2 id="account-heading" className="text-xl font-semibold">
					Danger Zone
				</h2>
				<p className="text-sm text-muted-foreground">Irreversible account actions</p>
			</div>

			<div className="flex items-start justify-between gap-6 py-4">
				<div className="space-y-1 flex-1">
					<h3 className="text-base font-medium">Delete account</h3>
					<p className="text-sm text-muted-foreground leading-relaxed">
						Permanently delete your account and remove your data from our servers.
					</p>
					{pending && (
						<p className="text-xs text-muted-foreground">Preparing account actions…</p>
					)}
				</div>
				<AlertDialog>
					<AlertDialogTrigger asChild>
						<Button
							variant="destructive"
							disabled={pending || processing}
							className="mt-1"
						>
							{processing ? "Deleting…" : "Delete"}
						</Button>
					</AlertDialogTrigger>
					<AlertDialogContent>
						<AlertDialogHeader>
							<AlertDialogTitle>Are you absolutely sure?</AlertDialogTitle>
							<AlertDialogDescription>
								This action cannot be undone. This will permanently delete your
								account and remove your data from our servers.
							</AlertDialogDescription>
						</AlertDialogHeader>
						<AlertDialogFooter>
							<AlertDialogCancel>Cancel</AlertDialogCancel>
							<AlertDialogAction
								onClick={onDeleteAccount}
								disabled={processing}
								className="bg-destructive hover:bg-destructive/90"
							>
								{processing ? "Deleting…" : "Delete account"}
							</AlertDialogAction>
						</AlertDialogFooter>
					</AlertDialogContent>
				</AlertDialog>
			</div>
		</section>
	);
}
