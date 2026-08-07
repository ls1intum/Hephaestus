import { useBlocker } from "@tanstack/react-router";
import deepEqual from "fast-deep-equal";
import { ChevronRight, RotateCcw } from "lucide-react";
import { useRef, useState } from "react";
import type {
	PracticeAutomatedReviewPolicy,
	PracticeBinding,
	PracticeDefinitionOptions,
	PracticeEvidenceOutcome,
	PracticeWorkTypeDefinitionOptions,
} from "@/api/types.gen";
import {
	artifactKindOfBindings,
	bindingsProblem,
	normalizeBindings,
	orderedWorkTypes,
	recommendedBinding,
	workTypeOptionsFor,
} from "@/components/admin/practice-catalog/bindings";
import {
	generateSlug,
	isValidSlug,
	workArtifactHint,
} from "@/components/admin/practice-catalog/constants";
import { canAttemptAutomatedReview } from "@/components/admin/practice-catalog/evidence-presentation";
import {
	PracticeBindingsEditor,
	withoutEvidence,
	withRecommendedEvidence,
} from "@/components/admin/practice-catalog/PracticeBindingsEditor";
import {
	PracticeMentoringSupportEditor,
	practicePolicyError,
	practicePolicyErrorTarget,
} from "@/components/admin/practice-catalog/PracticeMentoringSupportEditor";
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
import { artifactKindLabel } from "@/lib/artifact-kinds";

const NO_AREA = "__none__";

export interface PracticeDefinitionAreaOption {
	slug: string;
	name: string;
}

export interface PracticeDefinitionValue {
	slug: string;
	name: string;
	areaSlug?: string;
	/** The occasions this practice is reviewed on. The kind of work is read off their signals. */
	bindings: PracticeBinding[];
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
	areaSlug: string;
	bindings: PracticeBinding[];
	criteria: string;
	whyItMatters: string;
	whatGoodLooksLike: string;
	precomputeScript: string;
	automatedReviewPolicy: PracticeAutomatedReviewPolicy;
}

/** Everything a work type owns, stashed so switching away and back does not discard the work. */
interface WorkTypeDraft {
	bindings: PracticeBinding[];
	precomputeScript: string;
	automatedReviewPolicy: PracticeAutomatedReviewPolicy;
}

function initialState(
	definitionOptions: PracticeDefinitionOptions,
	initialData?: PracticeDefinitionValue,
): FormState {
	const fallback = orderedWorkTypes(definitionOptions)[0];
	return {
		name: initialData?.name ?? "",
		slug: initialData?.slug ?? "",
		areaSlug: initialData?.areaSlug ?? NO_AREA,
		bindings: initialData
			? normalizeBindings(initialData.bindings)
			: fallback
				? [recommendedBinding(fallback)]
				: [],
		criteria: initialData?.criteria ?? "",
		whyItMatters: initialData?.whyItMatters ?? "",
		whatGoodLooksLike: initialData?.whatGoodLooksLike ?? "",
		precomputeScript: initialData?.precomputeScript ?? "",
		automatedReviewPolicy:
			initialData?.automatedReviewPolicy ?? fallback?.recommendedPolicy ?? EMPTY_POLICY,
	};
}

/** Only reachable on an instance that offers no reviewable work type at all. */
const EMPTY_POLICY: PracticeAutomatedReviewPolicy = {
	sourceContractVersion: "",
	automatedReview: { mode: "NONE", evidenceSufficiency: "NONE" },
	whenEvidenceIsInsufficient: "SKIP_AUTOMATED_REVIEW",
	knownLimitations: [],
};

/**
 * The new work type's recommended frame, keeping the answer the author already gave about how far
 * Hephaestus may go. Changing what is reviewed is not a decision to start reviewing it.
 */
function recommendedPolicyWithCurrentSupport(
	recommended: PracticeAutomatedReviewPolicy,
	current: PracticeAutomatedReviewPolicy,
): PracticeAutomatedReviewPolicy {
	if (current.automatedReview.mode === "NONE") {
		return {
			...recommended,
			automatedReview: { mode: "NONE", evidenceSufficiency: "NONE" },
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
			insufficiencyReason: current.insufficiencyReason,
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
	const [submitted, setSubmitted] = useState(false);
	const [showAdvanced, setShowAdvanced] = useState(() => Boolean(initialData?.precomputeScript));
	const workTypes = orderedWorkTypes(definitionOptions);
	const artifactKind = artifactKindOfBindings(form.bindings);
	const selectedWorkType = workTypeOptionsFor(definitionOptions, artifactKind);
	// Recorded history belongs to the work type the practice was reviewed under. Switching work type in
	// the form changes which sources are even allowed, so the same rows would resolve to "Unknown
	// source" and describe a work type the author is no longer editing.
	const workTypeUnchanged = artifactKindOfBindings(initialData?.bindings ?? []) === artifactKind;
	// One stash per work type, replacing three parallel ones keyed the same way. They could never
	// disagree usefully — an occasion, its evidence and the script that prepares it all belong to the
	// same kind of work — and keeping them apart meant every switch had to remember to write all three.
	const workTypeDrafts = useRef<Map<string, WorkTypeDraft>>(
		new Map(
			artifactKind
				? [
						[
							artifactKind,
							{
								bindings: form.bindings,
								precomputeScript: form.precomputeScript,
								automatedReviewPolicy: form.automatedReviewPolicy,
							},
						],
					]
				: [],
		),
	);
	const canRunMentoring = canAttemptAutomatedReview(
		form.automatedReviewPolicy,
		selectedWorkType?.supportedAutomatedReviewModes ?? [],
	);
	const guidanceOnly = form.automatedReviewPolicy.automatedReview.mode === "NONE";
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

	const selectWorkType = (next: PracticeWorkTypeDefinitionOptions) => {
		setForm((previous) => {
			const previousKind = artifactKindOfBindings(previous.bindings);
			if (previousKind === next.artifactKind) return previous;
			if (previousKind) {
				workTypeDrafts.current.set(previousKind, {
					bindings: previous.bindings,
					precomputeScript: previous.precomputeScript,
					automatedReviewPolicy: previous.automatedReviewPolicy,
				});
			}
			const draft = workTypeDrafts.current.get(next.artifactKind);
			const automatedReviewPolicy =
				draft?.automatedReviewPolicy ??
				recommendedPolicyWithCurrentSupport(next.recommendedPolicy, previous.automatedReviewPolicy);
			const bindings = draft?.bindings ?? [recommendedBinding(next)];
			return {
				...previous,
				automatedReviewPolicy,
				bindings:
					automatedReviewPolicy.automatedReview.mode === "NONE"
						? withoutEvidence(bindings)
						: bindings,
				precomputeScript: draft?.precomputeScript ?? "",
			};
		});
	};

	// Evidence is forbidden outright while no review runs and mandatory as soon as one does, so the
	// support choice has to reach into every occasion rather than leaving the author to fix each by
	// hand and be refused on save.
	const updatePolicy = (automatedReviewPolicy: PracticeAutomatedReviewPolicy) => {
		setForm((previous) => {
			const nowGuidanceOnly = automatedReviewPolicy.automatedReview.mode === "NONE";
			const wasGuidanceOnly = previous.automatedReviewPolicy.automatedReview.mode === "NONE";
			const bindings = nowGuidanceOnly
				? withoutEvidence(previous.bindings)
				: wasGuidanceOnly && selectedWorkType
					? withRecommendedEvidence(previous.bindings, selectedWorkType)
					: previous.bindings;
			return {
				...previous,
				automatedReviewPolicy,
				bindings,
				precomputeScript: nowGuidanceOnly ? "" : previous.precomputeScript,
			};
		});
	};

	const nameError =
		submitted && form.name.trim().length < 3 ? "Name must be at least 3 characters" : undefined;
	const slugError =
		submitted && mode === "create" && !isValidSlug(form.slug)
			? "Use 3–64 lowercase letters, numbers, and single hyphens."
			: undefined;
	const criteriaError =
		submitted && form.criteria.trim().length < 3
			? "Criteria must be at least 3 characters"
			: undefined;
	const policyError = practicePolicyError(form.automatedReviewPolicy);
	const bindingsError = bindingsProblem(
		form.bindings,
		form.automatedReviewPolicy,
		selectedWorkType,
	);

	const valid =
		form.name.trim().length >= 3 &&
		form.criteria.trim().length >= 3 &&
		!policyError &&
		!bindingsError &&
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
						: policyError
							? practicePolicyErrorTarget(form.automatedReviewPolicy)
							: bindingsError
								? bindingsError.focusId
								: mode === "create" && !isValidSlug(form.slug)
									? "practice-slug"
									: "practice-name";
			if (firstInvalidId === "practice-slug") setShowAdvanced(true);
			requestAnimationFrame(() => document.getElementById(firstInvalidId)?.focus());
			return;
		}

		props.onSubmit({
			slug: form.slug,
			name: form.name.trim(),
			bindings: normalizeBindings(form.bindings),
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

					<PracticeMentoringSupportEditor
						value={form.automatedReviewPolicy}
						recommended={selectedWorkType?.recommendedPolicy ?? form.automatedReviewPolicy}
						supportedAutomatedReviewModes={selectedWorkType?.supportedAutomatedReviewModes ?? []}
						disabled={formDisabled}
						onChange={updatePolicy}
						error={submitted ? policyError : undefined}
					/>

					<Separator />

					<section className="space-y-4" aria-labelledby="practice-occasions-heading">
						<div>
							<h2 id="practice-occasions-heading" className="text-lg font-semibold">
								When this practice is reviewed
							</h2>
							<p className="text-sm text-muted-foreground">
								A practice is reviewed on occasions. Each one says what starts a review and what
								that review reads — and those differ: the review at the merge can say a thread was
								never resolved, while the review when the work arrived can only describe what is in
								front of it.
							</p>
						</div>

						<FieldSet>
							<FieldLegend variant="label">Review this kind of work</FieldLegend>
							<FieldDescription>
								Every occasion belongs to the same kind of work. Changing it starts the occasions
								again from the recommended ones.
							</FieldDescription>
							<RadioGroup
								value={artifactKind ?? ""}
								onValueChange={(value) => {
									const next = workTypes.find((option) => option.artifactKind === value);
									if (next) selectWorkType(next);
								}}
								className="gap-2"
							>
								{workTypes.map((option) => (
									<FieldLabel
										key={option.artifactKind}
										htmlFor={`practice-artifact-${option.artifactKind}`}
									>
										<Field orientation="horizontal">
											<RadioGroupItem
												id={`practice-artifact-${option.artifactKind}`}
												value={option.artifactKind}
											/>
											<FieldContent>
												<FieldTitle>{artifactKindLabel(option.artifactKind)}</FieldTitle>
												<FieldDescription>{workArtifactHint(option.artifactKind)}</FieldDescription>
											</FieldContent>
										</Field>
									</FieldLabel>
								))}
							</RadioGroup>
						</FieldSet>

						{selectedWorkType ? (
							<PracticeBindingsEditor
								options={selectedWorkType}
								bindings={form.bindings}
								canAttemptReview={canRunMentoring}
								guidanceOnly={guidanceOnly}
								outcome={workTypeUnchanged ? evidenceOutcome : undefined}
								disabled={formDisabled}
								error={submitted ? bindingsError?.message : undefined}
								errorFocusId={submitted ? bindingsError?.focusId : undefined}
								onChange={(bindings) => setForm((previous) => ({ ...previous, bindings }))}
							/>
						) : (
							<p className="text-sm text-muted-foreground">
								This practice reviews {artifactKindLabel(artifactKind)}, which this instance no
								longer offers. Choose a kind of work above to edit its occasions.
							</p>
						)}
					</section>

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
									Identifier{canRunMentoring ? " and static analysis" : ""}
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
