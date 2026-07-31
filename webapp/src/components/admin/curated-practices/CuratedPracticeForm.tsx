import { Link } from "@tanstack/react-router";
import { ArrowLeft, ClipboardPenLine, ListPlus, RotateCcw } from "lucide-react";
import { useState } from "react";
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
import type {
	CuratedPracticeSourceKind,
	CuratedPracticeSyncStatus,
} from "./CuratedPracticeCatalog";

export type CuratedPracticeFormValue = PracticeDefinitionValue;

export interface CuratedPracticeFormInitialValue extends CuratedPracticeFormValue {
	revisionNumber: number;
	status: "AVAILABLE" | "RETIRED";
	sourceKind: CuratedPracticeSourceKind;
	syncStatus: CuratedPracticeSyncStatus;
	latestBundledCatalogRevision?: number | null;
}

interface CuratedPracticeFormBaseProps {
	areas: readonly { slug: string; name: string }[];
	isPending: boolean;
	conflict?: boolean;
	onContinueWithDraft?: () => void;
	isResetPending?: boolean;
	onUseBundledVersion?: () => void;
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
	} = props;
	const [resetOpen, setResetOpen] = useState(false);
	const canUseBundledVersion =
		mode === "edit" &&
		(initialData.syncStatus === "OVERRIDDEN" || initialData.syncStatus === "UPDATE_AVAILABLE") &&
		onUseBundledVersion;
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
						<AlertDialogCancel disabled={isResetPending}>Keep instance version</AlertDialogCancel>
						<AlertDialogAction
							disabled={isResetPending}
							onClick={() => {
								setResetOpen(false);
								onUseBundledVersion?.();
							}}
						>
							{isResetPending ? "Using Hephaestus version…" : "Use Hephaestus version"}
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
						: `Editing revision ${initialData.revisionNumber}. Saving a semantic change creates the next revision.`
				}
			/>

			{mode === "edit" && initialData.sourceKind === "BUNDLED" && (
				<Alert
					variant={initialData.syncStatus === "SYNCED" ? "default" : "warning"}
					className="max-w-3xl"
				>
					<RotateCcw />
					<AlertTitle>
						{initialData.syncStatus === "SYNCED"
							? "Hephaestus-managed practice"
							: initialData.syncStatus === "UPDATE_AVAILABLE"
								? "Hephaestus update available"
								: initialData.syncStatus === "SOURCE_REMOVED"
									? "No longer shipped by Hephaestus"
									: "Instance override"}
					</AlertTitle>
					<AlertDescription>
						{initialData.syncStatus === "SYNCED" ? (
							<p>
								Saving changes creates an instance override. Later Hephaestus updates won't replace
								it until you use the Hephaestus version again.
							</p>
						) : initialData.syncStatus === "UPDATE_AVAILABLE" ? (
							<p>
								{initialData.latestBundledCatalogRevision
									? `Hephaestus catalog revision ${initialData.latestBundledCatalogRevision} is available. `
									: "A Hephaestus update is available. "}
								Saving keeps this instance override.
							</p>
						) : initialData.syncStatus === "SOURCE_REMOVED" ? (
							<p>
								Hephaestus no longer ships this practice. Its history and existing workspace copies
								are unchanged. Saving changes stores its definition as an instance override.
							</p>
						) : (
							<p>
								Saving keeps this instance override. Use the Hephaestus version to discard instance
								changes and resume Hephaestus updates.
							</p>
						)}
						{canUseBundledVersion && (
							<Button
								type="button"
								variant="outline"
								size="sm"
								className="mt-2"
								disabled={isResetPending || conflict}
								onClick={() => setResetOpen(true)}
							>
								Use Hephaestus version
							</Button>
						)}
					</AlertDescription>
				</Alert>
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
