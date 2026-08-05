import { useBlocker } from "@tanstack/react-router";
import deepEqual from "fast-deep-equal";
import { ChevronRight, RotateCcw } from "lucide-react";
import { useRef, useState } from "react";
import type {
	PracticeAutomatedReviewPolicy,
	PracticeDefinitionOptions,
	PracticeEvidenceOutcome,
	PracticeWorkTypeDefinitionOptions,
} from "@/api/types.gen";
import {
	FOCUS_ARTIFACT_OPTIONS,
	generateSlug,
	isValidSlug,
	type WorkArtifact,
} from "@/components/admin/practice-catalog/constants";
import { canAttemptAutomatedReview } from "@/components/admin/practice-catalog/evidence-presentation";
import {
	PracticeEvidenceEditor,
	practiceEvidenceError,
	practiceEvidenceErrorTarget,
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
	FieldContent,
	FieldDescription,
	FieldError,
	FieldGroup,
	FieldLabel,
	FieldLegend,
	FieldSet,
	FieldTitle,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
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
	automatedReviewPolicy: PracticeAutomatedReviewPolicy;
}

interface PracticeDefinitionFormBaseProps {
	areas: readonly PracticeDefinitionAreaOption[];
	isPending: boolean;
	disabled?: boolean;
	isSubmitDisabled?: boolean;
	afterFields?: React.ReactNode;
	cancelAction: React.ReactNode;
	onSubmit: (value: PracticeDefinitionValue) => void;
	definitionOptions: PracticeDefinitionOptions;
	/** How this practice's evidence requirements have turned out on recent reviews, when it has any. */
	evidenceOutcome?: PracticeEvidenceOutcome;
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
	automatedReviewPolicy: PracticeAutomatedReviewPolicy;
}

function definitionOptionsFor(
	definitionOptions: PracticeDefinitionOptions,
	artifactType: WorkArtifact,
): PracticeWorkTypeDefinitionOptions {
	const options = definitionOptions.workTypes.find((item) => item.artifactType === artifactType);
	if (!options) throw new Error(`Missing definition options for ${artifactType}`);
	return options;
}

function initialState(
	definitionOptions: PracticeDefinitionOptions,
	initialData?: PracticeDefinitionValue,
): FormState {
	const artifactType = initialData?.artifactType ?? "PULL_REQUEST";
	return {
		name: initialData?.name ?? "",
		slug: initialData?.slug ?? "",
		artifactType,
		areaSlug: initialData?.areaSlug ?? NO_AREA,
		triggerEvents: initialData
			? [...initialData.triggerEvents]
			: definitionOptionsFor(definitionOptions, artifactType)
					.triggerEvents.filter((event) => event.recommended)
					.map((event) => event.event),
		criteria: initialData?.criteria ?? "",
		whyItMatters: initialData?.whyItMatters ?? "",
		whatGoodLooksLike: initialData?.whatGoodLooksLike ?? "",
		precomputeScript: initialData?.precomputeScript ?? "",
		automatedReviewPolicy:
			initialData?.automatedReviewPolicy ??
			definitionOptionsFor(definitionOptions, artifactType).recommendedRequirements,
	};
}

function recommendedPolicyWithCurrentSupport(
	recommended: PracticeAutomatedReviewPolicy,
	current: PracticeAutomatedReviewPolicy,
): PracticeAutomatedReviewPolicy {
	if (current.automatedReview.mode === "NONE") {
		return {
			...recommended,
			automatedReview: { mode: "NONE", evidenceSufficiency: "NONE" },
			requiredEvidence: [],
			optionalContext: [],
			knownLimitations: [],
		};
	}
	if (current.automatedReview.evidenceSufficiency === "DECLARED_EVIDENCE_INSUFFICIENT") {
		return {
			...recommended,
			automatedReview: {
				mode: "LANGUAGE_MODEL",
				evidenceSufficiency: "DECLARED_EVIDENCE_INSUFFICIENT",
			},
			knownLimitations: current.knownLimitations,
		};
	}
	return recommended;
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
		definitionOptions,
		evidenceOutcome,
	} = props;
	const formDisabled = isPending || disabled;
	const [form, setForm] = useState<FormState>(() => initialState(definitionOptions, initialData));
	// Recorded history belongs to the work type the practice was reviewed under. Switching work type in
	// the form changes which sources are even allowed, so the same rows would resolve to "Unknown source"
	// and describe a work type the author is no longer editing.
	const artifactTypeUnchanged = initialData?.artifactType === form.artifactType;
	const evidenceDrafts = useRef<Partial<Record<WorkArtifact, PracticeAutomatedReviewPolicy>>>({
		[form.artifactType]: form.automatedReviewPolicy,
	});
	const triggerDrafts = useRef<Partial<Record<WorkArtifact, string[]>>>({
		[form.artifactType]: form.triggerEvents,
	});
	const precomputeDrafts = useRef<Partial<Record<WorkArtifact, string>>>({
		[form.artifactType]: form.precomputeScript,
	});
	const [submitted, setSubmitted] = useState(false);
	const [showAdvanced, setShowAdvanced] = useState(() => Boolean(initialData?.precomputeScript));
	const selectedDefinitionOptions = definitionOptionsFor(definitionOptions, form.artifactType);
	const canRunMentoring = canAttemptAutomatedReview(
		form.automatedReviewPolicy,
		selectedDefinitionOptions.supportedAutomatedReviewModes,
	);
	const isDirty = !deepEqual(form, initialState(definitionOptions, initialData));
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
		submitted &&
		canRunMentoring &&
		form.artifactType !== "CONVERSATION_THREAD" &&
		form.triggerEvents.length === 0
			? "Select at least one trigger event"
			: undefined;
	const criteriaError =
		submitted && form.criteria.trim().length < 3
			? "Criteria must be at least 3 characters"
			: undefined;
	const evidenceError = practiceEvidenceError(form.automatedReviewPolicy);

	const valid =
		form.name.trim().length >= 3 &&
		form.criteria.trim().length >= 3 &&
		(!canRunMentoring ||
			form.artifactType === "CONVERSATION_THREAD" ||
			form.triggerEvents.length > 0) &&
		!evidenceError &&
		(mode === "edit" || isValidSlug(form.slug));

	const handleSubmit = (event: React.FormEvent) => {
		event.preventDefault();
		setSubmitted(true);
		if (!valid) {
			const firstInvalidId =
				form.name.trim().length < 3
					? "practice-name"
					: form.criteria.trim().length < 3
						? "practice-criteria"
						: evidenceError
							? practiceEvidenceErrorTarget(form.automatedReviewPolicy)
							: mode === "create" && !isValidSlug(form.slug)
								? "practice-slug"
								: canRunMentoring &&
										form.artifactType !== "CONVERSATION_THREAD" &&
										form.triggerEvents.length === 0
									? `practice-trigger-${selectedDefinitionOptions.triggerEvents[0]?.event}`
									: "practice-name";
			if (firstInvalidId === "practice-slug" || firstInvalidId.startsWith("practice-trigger-")) {
				setShowAdvanced(true);
			}
			requestAnimationFrame(() => document.getElementById(firstInvalidId)?.focus());
			return;
		}

		props.onSubmit({
			slug: form.slug,
			name: form.name.trim(),
			artifactType: form.artifactType,
			triggerEvents: canRunMentoring ? form.triggerEvents : [],
			criteria: form.criteria.trim(),
			...(form.areaSlug === NO_AREA ? {} : { areaSlug: form.areaSlug }),
			...(form.whyItMatters.trim() ? { whyItMatters: form.whyItMatters.trim() } : {}),
			...(form.whatGoodLooksLike.trim()
				? { whatGoodLooksLike: form.whatGoodLooksLike.trim() }
				: {}),
			...(canRunMentoring && form.precomputeScript.trim()
				? { precomputeScript: form.precomputeScript.trim() }
				: {}),
			automatedReviewPolicy: form.automatedReviewPolicy,
		});
	};

	const slugWasEdited = mode === "create" && form.slug !== generateSlug(form.name);
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
				<div className="max-w-3xl space-y-10">
					<p className="text-sm text-muted-foreground">
						Define one observable habit. The same definition should make sense to a developer, peer,
						human mentor, and Hephaestus.
					</p>

					<section className="space-y-4">
						<div>
							<h2 className="text-lg font-semibold">Practice</h2>
							<p className="text-sm text-muted-foreground">
								Name the habit and choose where it applies. Fields marked <span aria-hidden>*</span>{" "}
								are required.
							</p>
						</div>
						<FieldGroup className="gap-4">
							<Field data-invalid={nameError ? "true" : undefined}>
								<FieldLabel htmlFor="practice-name">Name *</FieldLabel>
								<Input
									id="practice-name"
									value={form.name}
									onChange={(event) => handleNameChange(event.target.value)}
									placeholder="e.g. Explain what changed and why"
									required
									minLength={3}
									maxLength={128}
									aria-invalid={Boolean(nameError)}
									aria-describedby={nameError ? "practice-name-error" : undefined}
								/>
								<FieldDescription>Use a short, action-oriented name.</FieldDescription>
								{nameError && <FieldError id="practice-name-error">{nameError}</FieldError>}
							</Field>

							<div className="grid gap-4 sm:grid-cols-2">
								<FieldSet>
									<FieldLegend variant="label">Review this kind of work</FieldLegend>
									<RadioGroup
										value={form.artifactType}
										onValueChange={(value) =>
											value &&
											setForm((previous) => {
												const artifactType = value as WorkArtifact;
												const nextOptions = definitionOptionsFor(definitionOptions, artifactType);
												evidenceDrafts.current[previous.artifactType] =
													previous.automatedReviewPolicy;
												triggerDrafts.current[previous.artifactType] = previous.triggerEvents;
												precomputeDrafts.current[previous.artifactType] = previous.precomputeScript;
												const automatedReviewPolicy =
													evidenceDrafts.current[artifactType] ??
													recommendedPolicyWithCurrentSupport(
														nextOptions.recommendedRequirements,
														previous.automatedReviewPolicy,
													);
												const canRunNext = canAttemptAutomatedReview(
													automatedReviewPolicy,
													nextOptions.supportedAutomatedReviewModes,
												);
												return {
													...previous,
													artifactType,
													automatedReviewPolicy,
													triggerEvents: canRunNext
														? (triggerDrafts.current[artifactType] ??
															nextOptions.triggerEvents
																.filter((event) => event.recommended)
																.map((event) => event.event))
														: [],
													precomputeScript: canRunNext
														? (precomputeDrafts.current[artifactType] ?? "")
														: "",
												};
											})
										}
										className="gap-2"
									>
										{FOCUS_ARTIFACT_OPTIONS.map((option) => (
											<FieldLabel key={option.value} htmlFor={`practice-artifact-${option.value}`}>
												<Field orientation="horizontal">
													<RadioGroupItem
														id={`practice-artifact-${option.value}`}
														value={option.value}
													/>
													<FieldContent>
														<FieldTitle>{option.label}</FieldTitle>
														<FieldDescription>{option.hint}</FieldDescription>
													</FieldContent>
												</Field>
											</FieldLabel>
										))}
									</RadioGroup>
								</FieldSet>

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

					<section className="space-y-5">
						<div>
							<h2 className="text-lg font-semibold">Review guidance</h2>
							<p className="text-sm text-muted-foreground">
								Explain the habit in plain language before configuring how Hephaestus supports it.
							</p>
						</div>
						<Field data-invalid={criteriaError ? "true" : undefined}>
							<FieldLabel htmlFor="practice-criteria">What to look for *</FieldLabel>
							<FieldDescription id="practice-criteria-description">
								Describe one observable habit, what demonstrates it, and when a reviewer should stay
								silent. Do not ask the reviewer to infer intent or facts outside the selected work.
								For example: “Look for a description that explains the behavior change and why. Stay
								silent for automated dependency updates.” Markdown is supported.
							</FieldDescription>
							<Textarea
								id="practice-criteria"
								value={form.criteria}
								onChange={(event) =>
									setForm((previous) => ({ ...previous, criteria: event.target.value }))
								}
								placeholder="Describe the standard, signals of doing it well or poorly, and cases where it does not apply…"
								className="min-h-56"
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
							<FieldDescription>
								Shown to developers; it does not change review behavior.
							</FieldDescription>
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
							<FieldDescription>Give one concrete example a developer can act on.</FieldDescription>
						</Field>
					</section>

					<Separator />

					<PracticeEvidenceEditor
						options={selectedDefinitionOptions}
						value={form.automatedReviewPolicy}
						outcome={artifactTypeUnchanged ? evidenceOutcome : undefined}
						disabled={formDisabled}
						onChange={(automatedReviewPolicy) =>
							setForm((previous) => {
								evidenceDrafts.current[previous.artifactType] = automatedReviewPolicy;
								const canRunNext = canAttemptAutomatedReview(
									automatedReviewPolicy,
									selectedDefinitionOptions.supportedAutomatedReviewModes,
								);
								return {
									...previous,
									automatedReviewPolicy,
									triggerEvents:
										canRunNext && previous.triggerEvents.length === 0
											? (triggerDrafts.current[previous.artifactType] ??
												selectedDefinitionOptions.triggerEvents
													.filter((event) => event.recommended)
													.map((event) => event.event))
											: previous.triggerEvents,
									precomputeScript:
										canRunNext && !previous.precomputeScript
											? (precomputeDrafts.current[previous.artifactType] ?? "")
											: previous.precomputeScript,
								};
							})
						}
						error={submitted ? evidenceError : undefined}
					/>

					{afterFields}

					<Separator />

					<Collapsible open={showAdvanced} onOpenChange={setShowAdvanced}>
						<CollapsibleTrigger
							render={
								<Button
									type="button"
									variant="ghost"
									className="group -ml-3 h-auto items-start py-2 text-left disabled:opacity-100"
								/>
							}
						>
							<ChevronRight className="mt-0.5 size-4 transition-transform group-aria-expanded:rotate-90" />
							<span>
								<span className="block text-lg font-semibold">Technical settings</span>
								<span className="block text-sm font-normal text-muted-foreground">
									Identifier{canRunMentoring ? ", review timing, and static analysis" : ""}
								</span>
							</span>
						</CollapsibleTrigger>
						<CollapsibleContent className="mt-4 space-y-6 rounded-lg border p-4">
							<Field data-invalid={slugError ? "true" : undefined}>
								<FieldLabel htmlFor="practice-slug">Identifier</FieldLabel>
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
									Generated from the name for URLs and integrations. It cannot be changed later.
								</FieldDescription>
								{slugError && <FieldError id="practice-slug-error">{slugError}</FieldError>}
							</Field>

							{canRunMentoring && form.artifactType !== "CONVERSATION_THREAD" && (
								<FieldSet
									data-invalid={triggerError ? "true" : undefined}
									aria-invalid={Boolean(triggerError)}
									aria-describedby={`practice-trigger-description${triggerError ? " practice-trigger-error" : ""}`}
								>
									<FieldLegend>Run mentoring when *</FieldLegend>
									<FieldDescription id="practice-trigger-description">
										Recommended events are selected automatically. Change them only when this
										practice needs different timing.
									</FieldDescription>
									<FieldGroup data-slot="checkbox-group" className="grid gap-3 sm:grid-cols-2">
										{selectedDefinitionOptions.triggerEvents.map((option) => (
											<FieldLabel
												key={option.event}
												htmlFor={`practice-trigger-${option.event}`}
												className="flex cursor-pointer items-center gap-2 text-sm font-normal"
											>
												<Checkbox
													id={`practice-trigger-${option.event}`}
													checked={form.triggerEvents.includes(option.event)}
													onCheckedChange={(checked) =>
														toggleTrigger(option.event, checked === true)
													}
												/>
												{option.displayName}
											</FieldLabel>
										))}
									</FieldGroup>
									{triggerError && (
										<FieldError id="practice-trigger-error">{triggerError}</FieldError>
									)}
								</FieldSet>
							)}

							{canRunMentoring && (
								<div className="space-y-3">
									<div>
										<p className="font-medium">Static analysis</p>
										<p className="text-sm text-muted-foreground">
											Optional TypeScript that prepares structured context before a review. Most
											practices do not need it.
										</p>
									</div>
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
								</div>
							)}
						</CollapsibleContent>
					</Collapsible>
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
