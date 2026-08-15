import { Link } from "@tanstack/react-router";
import { ArrowLeft, ClipboardPenLine, ListPlus } from "lucide-react";
import type {
	CreatePracticeRequest,
	Practice,
	PracticeArea,
	PracticeDefinitionOptions,
	PracticeEvidenceOutcome,
	UpdatePracticeRequest,
} from "@/api/types.gen";
import { soleBinding } from "@/components/admin/practice-catalog/bindings";
import {
	PracticeDefinitionForm,
	type PracticeDefinitionValue,
} from "@/components/admin/practice-catalog/PracticeDefinitionForm";
import { PracticeAutomatedReviewValidationSummary } from "@/components/admin/practice-catalog/PracticeEvidenceSummary";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { buttonVariants } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";

interface PracticeFormCreateProps {
	mode: "create";
	workspaceSlug: string;
	areas: PracticeArea[];
	/** Rejects when the save failed, which is what keeps the unsaved-changes guard honest. */
	onSubmit: (data: CreatePracticeRequest, areaSlug: string | null) => void | Promise<void>;
	isPending: boolean;
	definitionOptions: PracticeDefinitionOptions;
	initialData?: never;
}

interface PracticeFormEditProps {
	mode: "edit";
	workspaceSlug: string;
	initialData: Practice;
	areas: PracticeArea[];
	/** Rejects when the save failed, which is what keeps the unsaved-changes guard honest. */
	onSubmit: (
		slug: string,
		data: UpdatePracticeRequest,
		areaSlug: string | null,
	) => void | Promise<void>;
	isPending: boolean;
	definitionOptions: PracticeDefinitionOptions;
	/** Absent until the practice has been reviewed at least once. */
	evidenceOutcome?: PracticeEvidenceOutcome;
}

export type PracticeFormProps = PracticeFormCreateProps | PracticeFormEditProps;

interface PracticeFormShellProps {
	mode: "create" | "edit";
	workspaceSlug: string;
	practiceName?: string;
	children: React.ReactNode;
}

function asDefinitionValue(practice: Practice): PracticeDefinitionValue {
	return {
		slug: practice.slug,
		name: practice.name,
		bindings: [soleBinding(practice.bindings)],
		criteria: practice.criteria,
		...(practice.areaSlug ? { areaSlug: practice.areaSlug } : {}),
		...(practice.whyItMatters ? { whyItMatters: practice.whyItMatters } : {}),
		...(practice.whatGoodLooksLike ? { whatGoodLooksLike: practice.whatGoodLooksLike } : {}),
		...(practice.precomputeScript ? { precomputeScript: practice.precomputeScript } : {}),
		automatedReviewPolicy: practice.automatedReviewPolicy,
	};
}

export function PracticeForm(props: PracticeFormProps) {
	const { mode, workspaceSlug, areas, isPending, initialData, definitionOptions } = props;
	const cancelAction = (
		<Link
			to="/w/$workspaceSlug/admin/practices"
			params={{ workspaceSlug }}
			search={(previous) => previous}
			className={buttonVariants({ variant: "outline" })}
		>
			Cancel
		</Link>
	);
	const submit = (value: PracticeDefinitionValue) => {
		const { areaSlug, ...definition } = value;
		if (props.mode === "create") {
			return props.onSubmit(definition, areaSlug ?? null);
		}

		const clear: NonNullable<UpdatePracticeRequest["clear"]> = [];
		if (!definition.precomputeScript) clear.push("PRECOMPUTE_SCRIPT");
		if (!definition.whyItMatters) clear.push("WHY_IT_MATTERS");
		if (!definition.whatGoodLooksLike) clear.push("WHAT_GOOD_LOOKS_LIKE");
		return props.onSubmit(
			props.initialData.slug,
			{
				name: definition.name,
				criteria: definition.criteria,
				bindings: definition.bindings,
				whyItMatters: definition.whyItMatters,
				whatGoodLooksLike: definition.whatGoodLooksLike,
				precomputeScript: definition.precomputeScript,
				automatedReviewPolicy: definition.automatedReviewPolicy,
				clear: clear.length > 0 ? clear : undefined,
			},
			areaSlug ?? null,
		);
	};
	const reviewResults =
		mode === "edit" ? (
			<>
				<Separator />
				<section className="space-y-4">
					<div>
						<h2 className="text-lg font-semibold">What the author declared</h2>
						<p className="text-sm text-muted-foreground">
							The requirements above are the author's own claim about this practice. Nobody has
							checked them independently, and nothing here says the observations recorded under it
							are correct. The digests record the exact rules that were declared, so a later change
							to them is visible rather than silent.
						</p>
					</div>
					<PracticeAutomatedReviewValidationSummary
						validation={initialData.automatedReviewValidation}
					/>
				</section>
				<Separator />
				<section className="space-y-4">
					<div>
						<h2 className="text-lg font-semibold">What the reviews observed</h2>
						<p className="text-sm text-muted-foreground">
							Every observation recorded for this practice across the workspace.
						</p>
					</div>
					<Link
						to="/w/$workspaceSlug/admin/practices/reviews/observations"
						params={{ workspaceSlug }}
						search={{ practiceSlug: [initialData.slug] }}
						className={cn(buttonVariants({ variant: "outline" }), "w-full sm:w-auto")}
					>
						View observations
					</Link>
				</section>
			</>
		) : undefined;

	return (
		<PracticeFormShell mode={mode} workspaceSlug={workspaceSlug} practiceName={initialData?.name}>
			{mode === "create" ? (
				<PracticeDefinitionForm
					mode="create"
					areas={areas}
					isPending={isPending}
					definitionOptions={definitionOptions}
					cancelAction={cancelAction}
					onSubmit={submit}
				/>
			) : (
				<PracticeDefinitionForm
					mode="edit"
					initialData={asDefinitionValue(initialData)}
					areas={areas}
					isPending={isPending}
					definitionOptions={definitionOptions}
					cancelAction={cancelAction}
					afterFields={reviewResults}
					evidenceOutcome={props.mode === "edit" ? props.evidenceOutcome : undefined}
					onSubmit={submit}
				/>
			)}
		</PracticeFormShell>
	);
}

export function PracticeFormShell({
	mode,
	workspaceSlug,
	practiceName,
	children,
}: PracticeFormShellProps) {
	return (
		<PageLayout>
			<Link
				to="/w/$workspaceSlug/admin/practices"
				params={{ workspaceSlug }}
				search={(previous) => previous}
				className={cn(buttonVariants({ variant: "ghost", size: "sm" }), "-ml-3 w-fit")}
			>
				<ArrowLeft className="size-4" aria-hidden />
				Practices
			</Link>
			<PageHeader
				icon={mode === "create" ? <ListPlus /> : <ClipboardPenLine />}
				title={mode === "create" ? "Create practice" : `Edit: ${practiceName ?? "practice"}`}
				description={
					mode === "create"
						? "Define a way of working and choose how it is reviewed."
						: "Update this practice's review rules and developer guidance."
				}
			/>
			{children}
		</PageLayout>
	);
}
