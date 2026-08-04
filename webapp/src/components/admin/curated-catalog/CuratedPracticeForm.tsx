import { Link } from "@tanstack/react-router";
import { ArrowLeft, ClipboardPenLine, ListPlus, RotateCcw } from "lucide-react";
import { useState } from "react";
import type {
	CatalogEntryStatus,
	CuratedPracticeDefinition,
	PracticeEvidenceOptions,
} from "@/api/types.gen";
import {
	PracticeDefinitionForm,
	type PracticeDefinitionValue,
} from "@/components/admin/practice-catalog/PracticeDefinitionForm";
import { PracticeAutomatedReviewValidationSummary } from "@/components/admin/practice-catalog/PracticeEvidenceSummary";
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
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";
import { canUseHephaestusVersion } from "./curated-entry-state";
import { HephaestusVersionPanel } from "./HephaestusVersionPanel";

export type CuratedPracticeFormValue = PracticeDefinitionValue;

export interface CuratedPracticeFormInitialValue extends CuratedPracticeFormValue {
	status: CatalogEntryStatus;
	automatedReviewPolicy: CuratedPracticeDefinition["automatedReviewPolicy"];
	automatedReviewValidation: CuratedPracticeDefinition["automatedReviewValidation"];
	shipped?: CuratedPracticeDefinition;
}

interface CuratedPracticeFormBaseProps {
	areas: readonly { slug: string; name: string }[];
	isPending: boolean;
	conflict?: boolean;
	onContinueWithDraft?: () => void;
	isResetPending?: boolean;
	isKeepPending?: boolean;
	onUseHephaestusVersion?: () => void;
	onKeepCurrentDefinition?: () => void;
	evidenceOptions: PracticeEvidenceOptions;
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
		evidenceOptions,
		onKeepCurrentDefinition,
	} = props;
	const [resetOpen, setResetOpen] = useState(false);
	const canReset =
		mode === "edit" && canUseHephaestusVersion(initialData.status) && onUseHephaestusVersion;
	const updateAvailable = mode === "edit" && initialData.status.state === "UPDATE_WAITING";
	const formDisabled = isResetPending || isKeepPending;
	const resetLabel = updateAvailable ? "Apply Hephaestus update" : "Restore Hephaestus default";
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
						<AlertDialogTitle>{resetLabel}?</AlertDialogTitle>
						<AlertDialogDescription>
							This replaces the customization and discards unsaved changes. It does not change
							whether the practice is included in new workspaces. Existing workspaces remain
							unchanged. Future Hephaestus updates apply automatically until the practice is
							customized again.
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
							{isResetPending ? `${resetLabel}…` : resetLabel}
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
				title={mode === "create" ? "Create practice" : `Edit: ${initialData.name}`}
				description={
					mode === "create"
						? "Define a practice for the instance catalog."
						: "Saving updates the instance catalog. Existing workspaces will not change."
				}
			/>

			{mode === "edit" && (
				<HephaestusVersionPanel
					status={initialData.status}
					kind="practice"
					shipped={initialData.shipped}
					evidenceOptions={evidenceOptions}
					areaNames={Object.fromEntries(areas.map((area) => [area.slug, area.name]))}
					isResetPending={isResetPending}
					isKeepPending={isKeepPending}
					disabled={conflict ?? false}
					onUseHephaestusVersion={canReset ? () => setResetOpen(true) : undefined}
					onKeepCurrentDefinition={onKeepCurrentDefinition}
				/>
			)}

			{conflict && (
				<div className="max-w-3xl space-y-2">
					<Alert variant="warning">
						<RotateCcw />
						<AlertTitle>This practice changed while you were editing</AlertTitle>
						<AlertDescription>
							Your draft is safe. Continue with it and save to replace the latest changes, or leave
							this page and reopen the practice to see them.
						</AlertDescription>
					</Alert>
					{onContinueWithDraft && (
						<Button type="button" variant="outline" size="sm" onClick={onContinueWithDraft}>
							Continue with my draft
						</Button>
					)}
				</div>
			)}

			{mode === "create" ? (
				<PracticeDefinitionForm
					mode="create"
					areas={areas}
					isPending={isPending}
					evidenceOptions={evidenceOptions}
					disabled={formDisabled}
					cancelAction={cancelAction}
					onSubmit={props.onSubmit}
				/>
			) : (
				<PracticeDefinitionForm
					mode="edit"
					initialData={initialData}
					areas={areas}
					isPending={isPending}
					evidenceOptions={evidenceOptions}
					disabled={formDisabled}
					isSubmitDisabled={conflict || isResetPending || isKeepPending}
					cancelAction={cancelAction}
					afterFields={
						<>
							<Separator />
							<section className="space-y-4">
								<div>
									<h2 className="text-lg font-semibold">Automated review validation</h2>
									<p className="text-sm text-muted-foreground">
										The evidence requirements above are the author's declaration. This status says
										whether an independent evaluator has validated the exact practice definition.
									</p>
								</div>
								<PracticeAutomatedReviewValidationSummary
									validation={initialData.automatedReviewValidation}
								/>
							</section>
						</>
					}
					onSubmit={props.onSubmit}
				/>
			)}
		</PageLayout>
	);
}
