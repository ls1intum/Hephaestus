import { RotateCcw } from "lucide-react";
import { useState } from "react";

import type { CatalogPractice, PracticeDefinitionOptions } from "@/api/types.gen";
import {
	PracticeDefinitionForm,
	type PracticeDefinitionSubmission,
} from "@/components/admin/practice-editor/PracticeDefinitionForm";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
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
import { Button } from "@/components/ui/button";

import { canUseHephaestusVersion } from "./catalog-entry-state";
import { practiceVersionFields } from "./hephaestus-version-fields";
import { HephaestusVersionPanel } from "./HephaestusVersionPanel";

interface CatalogPracticeFormBaseProps {
	groups: readonly { slug: string; name: string }[];
	isPending: boolean;
	/** Holds every field while the host replaces the practice from outside the form. */
	disabled?: boolean;
	conflict?: boolean;
	onContinueWithDraft?: () => void;
	isResetPending?: boolean;
	isKeepPending?: boolean;
	onUseHephaestusVersion?: () => void;
	onKeepCurrentDefinition?: () => void;
	releaseReview?: React.ReactNode;
	/** The host's alert for a read that failed to refresh while the draft stays open. */
	refreshAlert?: React.ReactNode;
	definitionOptions: PracticeDefinitionOptions;
	/** What "leave without saving" does. The host owns it, because only the host knows where back is. */
	cancel: React.ReactNode;
}

interface CatalogPracticeFormCreateProps extends CatalogPracticeFormBaseProps {
	mode: "create";
	practice?: never;
	onSubmit: (value: PracticeDefinitionSubmission) => void;
}

interface CatalogPracticeFormEditProps extends CatalogPracticeFormBaseProps {
	mode: "edit";
	/** The saved entry; the form edits its definition and group, never its kind. */
	practice: CatalogPractice;
	onSubmit: (value: PracticeDefinitionSubmission) => void;
}

export type CatalogPracticeFormProps =
	| CatalogPracticeFormCreateProps
	| CatalogPracticeFormEditProps;

export function CatalogPracticeForm(props: CatalogPracticeFormProps) {
	const {
		mode,
		groups,
		isPending,
		disabled = false,
		conflict,
		onContinueWithDraft,
		isResetPending = false,
		isKeepPending = false,
		onUseHephaestusVersion,
		practice,
		definitionOptions,
		onKeepCurrentDefinition,
		releaseReview,
		refreshAlert,
		cancel,
	} = props;
	const [resetOpen, setResetOpen] = useState(false);
	const canReset =
		mode === "edit" && canUseHephaestusVersion(practice) && onUseHephaestusVersion !== undefined;
	const formDisabled = disabled || isResetPending || isKeepPending;
	/** The host's own banners, handed to the form so they land inside its padded, scrolling body. */
	const banners = (
		<>
			{refreshAlert}
			{mode === "edit" && practice.state === "UPDATE_WAITING" && releaseReview}
			{mode === "edit" && practice.state !== "UPDATE_WAITING" && (
				<HephaestusVersionPanel
					status={practice}
					kind="practice"
					shipped={
						practice.shipped === null
							? undefined
							: practiceVersionFields(practice.shipped, practice.kind, definitionOptions)
					}
					isResetPending={isResetPending}
					isKeepPending={isKeepPending}
					disabled={conflict ?? false}
					onUseHephaestusVersion={canReset ? () => setResetOpen(true) : undefined}
					onKeepCurrentDefinition={onKeepCurrentDefinition}
				/>
			)}

			{conflict === true && (
				<div className="space-y-2">
					<Alert variant="warning" role="alert">
						<RotateCcw />
						<AlertTitle>This practice changed while you were editing</AlertTitle>
						<AlertDescription>
							Your draft is safe. Continue with it and save to replace the latest changes, or leave
							this page and reopen the practice to see them.
						</AlertDescription>
					</Alert>
					{onContinueWithDraft && (
						<Button type="button" variant="outline" size="sm" onClick={onContinueWithDraft}>
							Continue with your draft
						</Button>
					)}
				</div>
			)}
		</>
	);

	const resetLabel = "Restore Hephaestus default";

	return (
		<>
			<AlertDialog open={resetOpen} onOpenChange={setResetOpen}>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>{resetLabel}?</AlertDialogTitle>
						<AlertDialogDescription>
							This replaces the customization and discards unsaved changes. It does not change
							whether workspace admins can add the practice. Existing workspace copies remain
							unchanged. Future updates apply automatically until the practice is customized again.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel disabled={isResetPending}>Cancel</AlertDialogCancel>
						<AlertDialogAction
							disabled={isResetPending}
							onClick={() => {
								setResetOpen(false);
								onUseHephaestusVersion?.();
							}}
						>
							{isResetPending ? "Restoring default…" : resetLabel}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>

			{mode === "create" ? (
				<PracticeDefinitionForm
					mode="create"
					beforeFields={banners}
					groups={groups}
					isPending={isPending}
					definitionOptions={definitionOptions}
					disabled={formDisabled}
					cancelAction={cancel}
					onSubmit={props.onSubmit}
				/>
			) : (
				<PracticeDefinitionForm
					mode="edit"
					beforeFields={banners}
					initialData={{
						slug: practice.slug,
						kind: practice.kind,
						groupSlug: practice.groupSlug,
						definition: practice.definition,
					}}
					groups={groups}
					isPending={isPending}
					definitionOptions={definitionOptions}
					disabled={formDisabled}
					isSubmitDisabled={(conflict ?? false) || isResetPending || isKeepPending}
					cancelAction={cancel}
					onSubmit={props.onSubmit}
				/>
			)}
		</>
	);
}
