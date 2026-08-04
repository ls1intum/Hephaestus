import { useBlocker } from "@tanstack/react-router";
import { ChevronRight, RotateCcw } from "lucide-react";
import { useRef, useState } from "react";
import type {
	PracticeAutomatedAssessmentPolicy,
	PracticeEvidenceOptions,
	PracticeWorkTypeEvidenceOptions,
} from "@/api/types.gen";
import {
	FOCUS_ARTIFACT_OPTIONS,
	generateSlug,
	isValidSlug,
	TRIGGER_EVENTS_BY_FOCUS,
	type WorkArtifact,
} from "@/components/admin/practice-catalog/constants";
import {
	PracticeEvidenceEditor,
	practiceEvidenceError,
} from "@/components/admin/practice-catalog/PracticeEvidenceEditor";
import { CodeEditor } from "@/components/shared/CodeEditor";
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
import { Checkbox } from "@/components/ui/checkbox";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
	Field,
	FieldDescription,
	FieldError,
	FieldGroup,
	FieldLabel,
	FieldLegend,
	FieldSet,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";

const NO_AREA = "__none__";

export interface PracticeDefinitionAreaOption {
	slug: string;
	name: string;
}

export interface PracticeDefinitionValue {
	slug: string;
	name: string;
	artifactType: WorkArtifact;
	areaSlug?: string;
	triggerEvents: string[];
	criteria: string;
	whyItMatters?: string;
	whatGoodLooksLike?: string;
	precomputeScript?: string;
	automatedAssessmentPolicy: PracticeAutomatedAssessmentPolicy;
}

interface PracticeDefinitionFormBaseProps {
	areas: readonly PracticeDefinitionAreaOption[];
	isPending: boolean;
	disabled?: boolean;
	isSubmitDisabled?: boolean;
	afterFields?: React.ReactNode;
	cancelAction: React.ReactNode;
	onSubmit: (value: PracticeDefinitionValue) => void;
	evidenceOptions: PracticeEvidenceOptions;
}

interface PracticeDefinitionFormCreateProps extends PracticeDefinitionFormBaseProps {
	mode: "create";
	initialData?: never;
}

interface PracticeDefinitionFormEditProps extends PracticeDefinitionFormBaseProps {
	mode: "edit";
	initialData: PracticeDefinitionValue;
}

export type PracticeDefinitionFormProps =
	| PracticeDefinitionFormCreateProps
	| PracticeDefinitionFormEditProps;

interface FormState {
	name: string;
	slug: string;
	artifactType: WorkArtifact;
	areaSlug: string;
	triggerEvents: string[];
	criteria: string;
	whyItMatters: string;
	whatGoodLooksLike: string;
	precomputeScript: string;
	automatedAssessmentPolicy: PracticeAutomatedAssessmentPolicy;
}

function optionsForArtifact(
	evidenceOptions: PracticeEvidenceOptions,
	artifactType: WorkArtifact,
): PracticeWorkTypeEvidenceOptions {
	const options = evidenceOptions.workTypes.find((item) => item.artifactType === artifactType);
	if (!options) throw new Error(`Missing evidence options for ${artifactType}`);
	return options;
}

function initialState(
	evidenceOptions: PracticeEvidenceOptions,
	initialData?: PracticeDefinitionValue,
): FormState {
	const artifactType = initialData?.artifactType ?? "PULL_REQUEST";
	return {
		name: initialData?.name ?? "",
		slug: initialData?.slug ?? "",
		artifactType,
		areaSlug: initialData?.areaSlug ?? NO_AREA,
		triggerEvents: [...(initialData?.triggerEvents ?? [])],
		criteria: initialData?.criteria ?? "",
		whyItMatters: initialData?.whyItMatters ?? "",
		whatGoodLooksLike: initialData?.whatGoodLooksLike ?? "",
		precomputeScript: initialData?.precomputeScript ?? "",
		automatedAssessmentPolicy:
			initialData?.automatedAssessmentPolicy ??
			optionsForArtifact(evidenceOptions, artifactType).recommendedRequirements,
	};
}

export function PracticeDefinitionForm(props: PracticeDefinitionFormProps) {
	const {
		mode,
		areas,
		isPending,
		disabled = false,
		isSubmitDisabled,
		afterFields,
		cancelAction,
		initialData,
		evidenceOptions,
	} = props;
	const formDisabled = isPending || disabled;
	const [form, setForm] = useState<FormState>(() => initialState(evidenceOptions, initialData));
	const evidenceDrafts = useRef<Partial<Record<WorkArtifact, PracticeAutomatedAssessmentPolicy>>>({
		[form.artifactType]: form.automatedAssessmentPolicy,
	});
	const triggerDrafts = useRef<Partial<Record<WorkArtifact, string[]>>>({
		[form.artifactType]: form.triggerEvents,
	});
	const [submitted, setSubmitted] = useState(false);
	const [showAdvanced, setShowAdvanced] = useState(() => Boolean(initialData?.precomputeScript));
	const isDirty =
		JSON.stringify(form) !== JSON.stringify(initialState(evidenceOptions, initialData));
	const blocker = useBlocker({
		shouldBlockFn: () => isDirty,
		enableBeforeUnload: isDirty,
		disabled: !isDirty || formDisabled,
		withResolver: true,
	});

	const handleNameChange = (name: string) => {
		setForm((previous) => {
			const slugWasEdited = mode === "create" && previous.slug !== generateSlug(previous.name);
			return {
				...previous,
				name,
				...(!slugWasEdited ? { slug: generateSlug(name) } : {}),
			};
		});
	};

	const toggleTrigger = (trigger: string, checked: boolean) => {
		setForm((previous) => {
			const triggerEvents = checked
				? [...previous.triggerEvents, trigger]
				: previous.triggerEvents.filter((value) => value !== trigger);
			triggerDrafts.current[previous.artifactType] = triggerEvents;
			return { ...previous, triggerEvents };
		});
	};

	const nameError =
		submitted && form.name.trim().length < 3 ? "Name must be at least 3 characters" : undefined;
	const slugError =
		submitted && mode === "create" && !isValidSlug(form.slug)
			? "Use 3–64 lowercase letters, numbers, and single hyphens."
			: undefined;
	const triggerError =
		submitted && form.artifactType !== "CONVERSATION_THREAD" && form.triggerEvents.length === 0
			? "Select at least one trigger event"
			: undefined;
	const criteriaError =
		submitted && form.criteria.trim().length < 3
			? "Criteria must be at least 3 characters"
			: undefined;
	const evidenceError = practiceEvidenceError(form.automatedAssessmentPolicy);

	const valid =
		form.name.trim().length >= 3 &&
		form.criteria.trim().length >= 3 &&
		(form.artifactType === "CONVERSATION_THREAD" || form.triggerEvents.length > 0) &&
		!evidenceError &&
		(mode === "edit" || isValidSlug(form.slug));

	const handleSubmit = (event: React.FormEvent) => {
		event.preventDefault();
		setSubmitted(true);
		if (!valid) {
			const firstInvalidId =
				form.name.trim().length < 3
					? "practice-name"
					: mode === "create" && !isValidSlug(form.slug)
						? "practice-slug"
						: form.artifactType !== "CONVERSATION_THREAD" && form.triggerEvents.length === 0
							? `practice-trigger-${TRIGGER_EVENTS_BY_FOCUS[form.artifactType][0]?.value}`
							: evidenceError
								? "practice-evidence-heading"
								: "practice-criteria";
			requestAnimationFrame(() => document.getElementById(firstInvalidId)?.focus());
			return;
		}

		props.onSubmit({
			slug: form.slug,
			name: form.name.trim(),
			artifactType: form.artifactType,
			triggerEvents: form.triggerEvents,
			criteria: form.criteria.trim(),
			...(form.areaSlug === NO_AREA ? {} : { areaSlug: form.areaSlug }),
			...(form.whyItMatters.trim() ? { whyItMatters: form.whyItMatters.trim() } : {}),
			...(form.whatGoodLooksLike.trim()
				? { whatGoodLooksLike: form.whatGoodLooksLike.trim() }
				: {}),
			...(form.precomputeScript.trim() ? { precomputeScript: form.precomputeScript.trim() } : {}),
			automatedAssessmentPolicy: form.automatedAssessmentPolicy,
		});
	};

	const slugWasEdited = mode === "create" && form.slug !== generateSlug(form.name);
	const selectedEvidenceOptions = optionsForArtifact(evidenceOptions, form.artifactType);

	return (
		<form onSubmit={handleSubmit} noValidate className="flex flex-col gap-8">
			<AlertDialog
				open={blocker.status === "blocked"}
				onOpenChange={(open, eventDetails) => {
					if (!open && eventDetails.reason === "escape-key") {
						blocker.reset?.();
					}
				}}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Discard unsaved changes?</AlertDialogTitle>
						<AlertDialogDescription>
							Your draft will be lost if you leave this page.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel onClick={blocker.reset}>Keep editing</AlertDialogCancel>
						<AlertDialogAction variant="destructive" onClick={blocker.proceed}>
							Discard changes
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
			<fieldset disabled={formDisabled} className="contents">
				<div className="max-w-3xl space-y-8">
					<p className="text-sm text-muted-foreground">
						Fields marked <span aria-hidden>*</span> are required.
					</p>

					<section className="space-y-4">
						<h2 className="text-lg font-semibold">General</h2>
						<FieldGroup className="gap-4">
							<Field data-invalid={nameError ? "true" : undefined}>
								<FieldLabel htmlFor="practice-name">Name *</FieldLabel>
								<Input
									id="practice-name"
									value={form.name}
									onChange={(event) => handleNameChange(event.target.value)}
									placeholder="e.g. PR description quality"
									required
									minLength={3}
									maxLength={128}
									aria-invalid={Boolean(nameError)}
									aria-describedby={nameError ? "practice-name-error" : undefined}
								/>
								{nameError && <FieldError id="practice-name-error">{nameError}</FieldError>}
							</Field>

							<Field data-invalid={slugError ? "true" : undefined}>
								<FieldLabel htmlFor="practice-slug">
									Identifier {mode === "create" && "*"}
								</FieldLabel>
								<div className="flex items-center gap-2">
									<Input
										id="practice-slug"
										value={form.slug}
										onChange={(event) =>
											setForm((previous) => ({ ...previous, slug: event.target.value }))
										}
										disabled={mode === "edit"}
										required={mode === "create"}
										minLength={3}
										maxLength={64}
										aria-invalid={Boolean(slugError)}
										aria-describedby={
											["practice-slug-description", slugError ? "practice-slug-error" : undefined]
												.filter(Boolean)
												.join(" ") || undefined
										}
									/>
									{slugWasEdited && (
										<Button
											type="button"
											variant="ghost"
											size="icon-sm"
											onClick={() =>
												setForm((previous) => ({
													...previous,
													slug: generateSlug(previous.name),
												}))
											}
											aria-label="Reset to generated identifier"
										>
											<RotateCcw className="size-3.5" aria-hidden />
										</Button>
									)}
								</div>
								<FieldDescription id="practice-slug-description">
									Used in URLs and integrations. It can't be changed later.
								</FieldDescription>
								{slugError && <FieldError id="practice-slug-error">{slugError}</FieldError>}
							</Field>

							<div className="grid gap-4 sm:grid-cols-2">
								<Field>
									<FieldLabel htmlFor="practice-artifact">Evaluates</FieldLabel>
									<Select
										value={form.artifactType}
										onValueChange={(value) =>
											setForm((previous) => {
												const artifactType = value as WorkArtifact;
												evidenceDrafts.current[previous.artifactType] =
													previous.automatedAssessmentPolicy;
												triggerDrafts.current[previous.artifactType] = previous.triggerEvents;
												return {
													...previous,
													artifactType,
													automatedAssessmentPolicy:
														evidenceDrafts.current[artifactType] ??
														optionsForArtifact(evidenceOptions, artifactType)
															.recommendedRequirements,
													triggerEvents: triggerDrafts.current[artifactType] ?? [],
												};
											})
										}
									>
										<SelectTrigger
											id="practice-artifact"
											aria-describedby="practice-artifact-description"
										>
											<SelectValue>
												{
													FOCUS_ARTIFACT_OPTIONS.find(
														(option) => option.value === form.artifactType,
													)?.label
												}
											</SelectValue>
										</SelectTrigger>
										<SelectContent>
											{FOCUS_ARTIFACT_OPTIONS.map((option) => (
												<SelectItem key={option.value} value={option.value}>
													{option.label}
												</SelectItem>
											))}
										</SelectContent>
									</Select>
									<FieldDescription id="practice-artifact-description">
										{
											FOCUS_ARTIFACT_OPTIONS.find((option) => option.value === form.artifactType)
												?.hint
										}
									</FieldDescription>
								</Field>

								<Field>
									<FieldLabel htmlFor="practice-area">Practice area</FieldLabel>
									<Select
										value={form.areaSlug}
										onValueChange={(value) =>
											setForm((previous) => ({ ...previous, areaSlug: value ?? NO_AREA }))
										}
									>
										<SelectTrigger id="practice-area" aria-describedby="practice-area-description">
											<SelectValue>
												{form.areaSlug === NO_AREA
													? "Unassigned"
													: areas.find((area) => area.slug === form.areaSlug)?.name}
											</SelectValue>
										</SelectTrigger>
										<SelectContent>
											<SelectItem value={NO_AREA}>Unassigned</SelectItem>
											{areas.map((area) => (
												<SelectItem key={area.slug} value={area.slug}>
													{area.name}
												</SelectItem>
											))}
										</SelectContent>
									</Select>
									<FieldDescription id="practice-area-description">
										Group this practice under an area.
									</FieldDescription>
								</Field>
							</div>
						</FieldGroup>
					</section>

					<Separator />

					{form.artifactType !== "CONVERSATION_THREAD" && (
						<>
							<FieldSet
								data-invalid={triggerError ? "true" : undefined}
								aria-invalid={Boolean(triggerError)}
								aria-describedby={`practice-trigger-description${triggerError ? " practice-trigger-error" : ""}`}
							>
								<FieldLegend className="text-lg">Start a review when… *</FieldLegend>
								<FieldDescription id="practice-trigger-description">
									Choose one or more events.
								</FieldDescription>
								<FieldGroup data-slot="checkbox-group" className="grid gap-3 sm:grid-cols-2">
									{TRIGGER_EVENTS_BY_FOCUS[form.artifactType].map((option) => (
										<FieldLabel
											key={option.value}
											htmlFor={`practice-trigger-${option.value}`}
											className="flex cursor-pointer items-center gap-2 text-sm font-normal"
										>
											<Checkbox
												id={`practice-trigger-${option.value}`}
												checked={form.triggerEvents.includes(option.value)}
												onCheckedChange={(checked) => toggleTrigger(option.value, checked === true)}
											/>
											{option.label}
										</FieldLabel>
									))}
								</FieldGroup>
								{triggerError && (
									<FieldError id="practice-trigger-error">{triggerError}</FieldError>
								)}
							</FieldSet>
							<Separator />
						</>
					)}

					<PracticeEvidenceEditor
						options={selectedEvidenceOptions}
						value={form.automatedAssessmentPolicy}
						disabled={formDisabled}
						onChange={(automatedAssessmentPolicy) =>
							setForm((previous) => {
								evidenceDrafts.current[previous.artifactType] = automatedAssessmentPolicy;
								return { ...previous, automatedAssessmentPolicy };
							})
						}
						error={submitted ? evidenceError : undefined}
					/>

					<Separator />

					<section>
						<Field data-invalid={criteriaError ? "true" : undefined}>
							<FieldLabel htmlFor="practice-criteria" className="text-lg font-semibold">
								Evaluation criteria *
							</FieldLabel>
							<FieldDescription id="practice-criteria-description">
								Instructions Hephaestus uses to assess this practice. Supports Markdown.
							</FieldDescription>
							<Textarea
								id="practice-criteria"
								value={form.criteria}
								onChange={(event) =>
									setForm((previous) => ({ ...previous, criteria: event.target.value }))
								}
								placeholder="## Practice name&#10;&#10;Describe what to evaluate, required elements, and anti-patterns…"
								className="min-h-64 font-mono text-sm"
								required
								minLength={3}
								maxLength={50_000}
								aria-invalid={Boolean(criteriaError)}
								aria-describedby={`practice-criteria-description${
									criteriaError ? " practice-criteria-error" : ""
								}`}
							/>
							{criteriaError && (
								<FieldError id="practice-criteria-error">{criteriaError}</FieldError>
							)}
						</Field>
					</section>

					<Separator />

					<section className="space-y-4">
						<div>
							<h2 className="text-lg font-semibold">Developer guidance</h2>
							<p className="text-sm text-muted-foreground">
								Optional guidance shown to developers. It does not change review behavior.
							</p>
						</div>
						<Field>
							<FieldLabel htmlFor="practice-why">Why it matters</FieldLabel>
							<Textarea
								id="practice-why"
								value={form.whyItMatters}
								onChange={(event) =>
									setForm((previous) => ({ ...previous, whyItMatters: event.target.value }))
								}
								placeholder="Explain why this practice is worth caring about…"
								className="min-h-24"
								maxLength={2_000}
							/>
						</Field>
						<Field>
							<FieldLabel htmlFor="practice-good">What good looks like</FieldLabel>
							<Textarea
								id="practice-good"
								value={form.whatGoodLooksLike}
								onChange={(event) =>
									setForm((previous) => ({ ...previous, whatGoodLooksLike: event.target.value }))
								}
								placeholder="Describe a concrete example of doing this well…"
								className="min-h-24"
								maxLength={2_000}
							/>
						</Field>
					</section>

					<Separator />

					<Collapsible open={showAdvanced} onOpenChange={setShowAdvanced}>
						<CollapsibleTrigger
							render={
								<Button
									type="button"
									variant="ghost"
									className="group -ml-3 text-lg font-semibold disabled:opacity-100"
								/>
							}
						>
							<ChevronRight className="size-4 transition-transform group-aria-expanded:rotate-90" />
							Precompute script
						</CollapsibleTrigger>
						<CollapsibleContent className="mt-4 space-y-4">
							<p className="text-sm text-muted-foreground">
								Optional TypeScript that runs static analysis before a review and provides
								structured context.
							</p>
							<CodeEditor
								value={form.precomputeScript}
								onChange={(value) =>
									setForm((previous) => ({ ...previous, precomputeScript: value }))
								}
								language="typescript"
								ariaLabel="Precompute script"
								className="h-[400px]"
								readOnly={formDisabled}
							/>
						</CollapsibleContent>
					</Collapsible>

					{afterFields}
				</div>
			</fieldset>

			<div className="flex max-w-3xl justify-between border-t pt-4">
				{cancelAction}
				<Button type="submit" disabled={formDisabled || isSubmitDisabled}>
					{isPending && <Spinner className="size-4" />}
					{isPending
						? mode === "create"
							? "Creating…"
							: "Saving…"
						: mode === "create"
							? "Create practice"
							: "Save changes"}
				</Button>
			</div>
		</form>
	);
}
