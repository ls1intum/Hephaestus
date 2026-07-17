import { useState } from "react";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";

export interface DeleteWorkspaceAlertDialogProps {
	open: boolean;
	onOpenChange: (open: boolean) => void;
	workspaceSlug: string;
	isDeleting: boolean;
	onConfirm: () => void;
}

export function DeleteWorkspaceAlertDialog({
	open,
	onOpenChange,
	workspaceSlug,
	isDeleting,
	onConfirm,
}: DeleteWorkspaceAlertDialogProps) {
	const [confirmText, setConfirmText] = useState("");
	const [mismatch, setMismatch] = useState(false);

	function handleOpenChange(next: boolean) {
		if (!next) {
			setConfirmText("");
			setMismatch(false);
		}
		onOpenChange(next);
	}

	function confirm() {
		// Validate on submit so a mismatch can state why; a disabled button states no reason.
		if (confirmText.trim() !== workspaceSlug) {
			setMismatch(true);
			return;
		}
		onConfirm();
	}

	return (
		<AlertDialog open={open} onOpenChange={handleOpenChange}>
			<AlertDialogContent>
				<AlertDialogHeader>
					<AlertDialogTitle>
						Delete <span className="font-mono">{workspaceSlug}</span> and purge its data?
					</AlertDialogTitle>
					{/* Consequences live in Description: it is what aria-describedby resolves to. */}
					<AlertDialogDescription render={<div />} className="space-y-3 text-left">
						<p>This cannot be undone. Deleting the workspace permanently erases:</p>
						<ul className="list-disc space-y-1 pl-5">
							<li>every membership — all members lose access immediately;</li>
							<li>monitored repositories and workspace settings;</li>
							<li>collected activity, practice detections, and feedback history;</li>
							<li>mentor conversations and collected Slack messages;</li>
							<li>stored integration credentials — every connection is disconnected.</li>
						</ul>
						<p>These survive:</p>
						<ul className="list-disc space-y-1 pl-5">
							<li>
								comments Hephaestus posted on GitHub or GitLab — they live on your git provider, and
								deleting the workspace does not remove them;
							</li>
							<li>
								the name <span className="font-mono">{workspaceSlug}</span>, which stays reserved
								and can never be used for a new workspace.
							</li>
						</ul>
					</AlertDialogDescription>
				</AlertDialogHeader>

				<Field data-invalid={mismatch}>
					<FieldLabel htmlFor="delete-workspace-confirm">
						Type <span className="font-mono font-medium">{workspaceSlug}</span> to confirm
					</FieldLabel>
					<Input
						id="delete-workspace-confirm"
						value={confirmText}
						disabled={isDeleting}
						onChange={(e) => {
							setConfirmText(e.target.value);
							setMismatch(false);
						}}
						autoComplete="off"
						autoCapitalize="off"
						spellCheck={false}
						aria-invalid={mismatch}
					/>
					{mismatch && (
						<FieldError>That does not match. Type the workspace slug exactly.</FieldError>
					)}
				</Field>

				<AlertDialogFooter>
					<AlertDialogCancel disabled={isDeleting}>Keep workspace</AlertDialogCancel>
					<AlertDialogAction variant="destructive" disabled={isDeleting} onClick={confirm}>
						{isDeleting ? "Deleting…" : "Delete workspace"}
					</AlertDialogAction>
				</AlertDialogFooter>
			</AlertDialogContent>
		</AlertDialog>
	);
}
