import { Link } from "@tanstack/react-router";
import { ArrowLeft, ClipboardPenLine, ListPlus, RotateCcw } from "lucide-react";
import { useState } from "react";
import type { CatalogEntryStatus } from "@/api/types.gen";
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
	shipped?: Record<string, unknown> | null;
}

interface CuratedPracticeFormBaseProps {
	areas: readonly { slug: string; name: string }[];
	isPending: boolean;
	conflict?: boolean;
	onContinueWithDraft?: () => void;
	isResetPending?: boolean;
	onUseBundledVersion?: () => void;
	onKeepOurs?: () => void;
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
		onUseBundledVersion,
		initialData,
		onKeepOurs,
	} = props;
	const [resetOpen, setResetOpen] = useState(false);
	const canReset =
		mode === "edit" && canUseHephaestusVersion(initialData.status) && onUseBundledVersion;
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
							This replaces the instance override and any unsaved edits with the latest Hephaestus
							definition. The practice's availability and existing workspace copies are unaffected.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel disabled={isResetPending}>Keep ours</AlertDialogCancel>
						<AlertDialogAction
							disabled={isResetPending}
							onClick={() => {
								setResetOpen(false);
								onUseBundledVersion?.();
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
				Curated catalog
			</Link>
			<PageHeader
				icon={mode === "create" ? <ListPlus /> : <ClipboardPenLine />}
				title={mode === "create" ? "Create curated practice" : `Edit: ${initialData.name}`}
				description={
					mode === "create"
						? "Define a practice for the shared instance catalog."
						: "Saving replaces what this instance offers. Workspaces that already have it are unaffected."
				}
			/>

			{mode === "edit" && (
				<HephaestusVersionPanel
					status={initialData.status}
					kind="practice"
					shipped={initialData.shipped}
					isResetPending={isResetPending}
					disabled={conflict ?? false}
					onUseHephaestusVersion={canReset ? () => setResetOpen(true) : undefined}
					onKeepOurs={onKeepOurs}
				/>
			)}

			{conflict && (
				<div className="max-w-3xl space-y-2">
					<Alert variant="warning">
						<RotateCcw />
						<AlertTitle>A newer version was saved while you were editing</AlertTitle>
						<AlertDescription>
							Your draft is unchanged. Continuing refreshes the version check; saving afterward
							replaces the latest definition with this entire draft.
						</AlertDescription>
					</Alert>
					{onContinueWithDraft && (
						<Button type="button" variant="outline" size="sm" onClick={onContinueWithDraft}>
							Continue with this draft
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
					isSubmitDisabled={conflict || isResetPending}
					cancelAction={cancelAction}
					onSubmit={props.onSubmit}
				/>
			)}
		</PageLayout>
	);
}
