import { Link } from "@tanstack/react-router";
import { ArrowLeft, ClipboardPenLine, ListPlus, RotateCcw } from "lucide-react";
import { useState } from "react";
import type { CatalogEntryStatus, CuratedPracticeRequest } from "@/api/types.gen";
import {
	PracticeDefinitionForm,
	type PracticeDefinitionValue,
} from "@/components/admin/practices/PracticeDefinitionForm";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
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
import { Button, buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { canUseHephaestusVersion } from "./curated-entry-state";
import { HephaestusVersionPanel } from "./HephaestusVersionPanel";

export type CuratedPracticeFormValue = PracticeDefinitionValue;

export interface CuratedPracticeFormInitialValue extends CuratedPracticeFormValue {
	status: CatalogEntryStatus;
	shipped?: CuratedPracticeRequest;
}

interface CuratedPracticeFormBaseProps {
	areas: readonly { slug: string; name: string }[];
	isPending: boolean;
	conflict?: boolean;
	onContinueWithDraft?: () => void;
	isResetPending?: boolean;
	isKeepPending?: boolean;
	onUseHephaestusVersion?: () => void;
	onKeepOurVersion?: () => void;
}

interface CuratedPracticeFormCreateProps extends CuratedPracticeFormBaseProps {
	mode: "create";
	initialData?: never;
	onSubmit: (value: CuratedPracticeFormValue) => void;
}

interface CuratedPracticeFormEditProps extends CuratedPracticeFormBaseProps {
	mode: "edit";
	initialData: CuratedPracticeFormInitialValue;
	onSubmit: (value: CuratedPracticeFormValue) => void;
}

export type CuratedPracticeFormProps =
	| CuratedPracticeFormCreateProps
	| CuratedPracticeFormEditProps;

export function CuratedPracticeForm(props: CuratedPracticeFormProps) {
	const {
		mode,
		areas,
		isPending,
		conflict,
		onContinueWithDraft,
		isResetPending = false,
		isKeepPending = false,
		onUseHephaestusVersion,
		initialData,
		onKeepOurVersion,
	} = props;
	const [resetOpen, setResetOpen] = useState(false);
	const canReset =
		mode === "edit" && canUseHephaestusVersion(initialData.status) && onUseHephaestusVersion;
	const cancelAction = (
		<Link
			from="/admin/catalog"
			to="/admin/catalog"
			search={(previous) => previous}
			className={buttonVariants({ variant: "outline" })}
		>
			Cancel
		</Link>
	);

	return (
		<PageLayout>
			<AlertDialog open={resetOpen} onOpenChange={setResetOpen}>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Use the Hephaestus version?</AlertDialogTitle>
						<AlertDialogDescription>
							Your version and any unsaved edits are discarded. From now on this practice follows
							Hephaestus. Whether it is offered, and every workspace copy of it, stay as they are.
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
							{isResetPending ? "Using the Hephaestus version…" : "Use the Hephaestus version"}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
			<Link
				from="/admin/catalog"
				to="/admin/catalog"
				search={(previous) => previous}
				className={cn(buttonVariants({ variant: "ghost", size: "sm" }), "-ml-3 w-fit")}
			>
				<ArrowLeft className="size-4" aria-hidden />
				Practice catalog
			</Link>
			<PageHeader
				icon={mode === "create" ? <ListPlus /> : <ClipboardPenLine />}
				title={mode === "create" ? "Add practice" : `Edit: ${initialData.name}`}
				description={
					mode === "create"
						? "Define a practice every new workspace will receive."
						: "Saving replaces what this instance offers. Workspaces that already have it are unaffected."
				}
			/>

			{mode === "edit" && (
				<HephaestusVersionPanel
					status={initialData.status}
					kind="practice"
					shipped={initialData.shipped}
					isResetPending={isResetPending}
					isKeepPending={isKeepPending}
					disabled={conflict ?? false}
					onUseHephaestusVersion={canReset ? () => setResetOpen(true) : undefined}
					onKeepOurVersion={onKeepOurVersion}
				/>
			)}

			{conflict && (
				<div className="max-w-3xl space-y-2">
					<Alert variant="warning">
						<RotateCcw />
						<AlertTitle>Someone else saved this practice while you were editing</AlertTitle>
						<AlertDescription>
							Your draft is untouched. If you keep it and save, their changes are overwritten by
							everything in your draft. To see theirs instead, leave this page and open it again.
						</AlertDescription>
					</Alert>
					{onContinueWithDraft && (
						<Button type="button" variant="outline" size="sm" onClick={onContinueWithDraft}>
							Keep my draft
						</Button>
					)}
				</div>
			)}

			{mode === "create" ? (
				<PracticeDefinitionForm
					mode="create"
					areas={areas}
					isPending={isPending}
					cancelAction={cancelAction}
					onSubmit={props.onSubmit}
				/>
			) : (
				<PracticeDefinitionForm
					mode="edit"
					initialData={initialData}
					areas={areas}
					isPending={isPending}
					isSubmitDisabled={conflict || isResetPending || isKeepPending}
					cancelAction={cancelAction}
					onSubmit={props.onSubmit}
				/>
			)}
		</PageLayout>
	);
}
