import deepEqual from "fast-deep-equal";
import { ChevronRight, Plus, RotateCcw, Trash2, TriangleAlert } from "lucide-react";
import { useRef, useState } from "react";
import type {
	PracticeAutomatedReviewPolicy,
	PracticeEvidenceSourceOption,
	PracticeWorkTypeDefinitionOptions,
} from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
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
import { evidenceQualityLabel, evidenceSourceLabel } from "./evidence-presentation";

type EvidenceRole = "NOT_USED" | "OPTIONAL" | "REQUIRED";
type MentoringSupport = "AI_SUPPORTED" | "HUMAN_CONTEXT_REQUIRED" | "GUIDANCE_ONLY";

const EVIDENCE_ROLE_OPTIONS = [
	{ value: "REQUIRED", label: "Required" },
	{ value: "OPTIONAL", label: "Optional context" },
	{ value: "NOT_USED", label: "Not used" },
] satisfies Array<{ value: EvidenceRole; label: string }>;

function mentoringSupportOf(requirements: PracticeAutomatedReviewPolicy): MentoringSupport {
	if (requirements.automatedReview.mode === "NONE") return "GUIDANCE_ONLY";
	if (requirements.automatedReview.evidenceSufficiency === "DECLARED_EVIDENCE_INSUFFICIENT") {
		return "HUMAN_CONTEXT_REQUIRED";
	}
	return "AI_SUPPORTED";
}

function roleOf(requirements: PracticeAutomatedReviewPolicy, sourceKind: string): EvidenceRole {
	if (requirements.requiredEvidence.some((requirement) => requirement.sourceKind === sourceKind)) {
		return "REQUIRED";
	}
	if (requirements.optionalContext.some((requirement) => requirement.sourceKind === sourceKind)) {
		return "OPTIONAL";
	}
	return "NOT_USED";
}

function withRole(
	requirements: PracticeAutomatedReviewPolicy,
	source: PracticeEvidenceSourceOption,
	role: EvidenceRole,
	recommended: PracticeAutomatedReviewPolicy,
): PracticeAutomatedReviewPolicy {
	const required = requirements.requiredEvidence.filter(
		(requirement) => requirement.sourceKind !== source.sourceKind,
	);
	const optional = requirements.optionalContext.filter(
		(requirement) => requirement.sourceKind !== source.sourceKind,
	);
	if (role === "REQUIRED") {
		required.push({
			sourceKind: source.sourceKind,
			completeness: source.supportsComplete ? "COMPLETE" : "NO_REQUIREMENT",
			freshness: source.supportsCurrent ? "CURRENT" : "NO_REQUIREMENT",
			// Whether an empty capture can be judged is an editorial call per source, not something
			// the editor can infer, so re-adding a source restores the recommended answer rather
			// than silently dropping to "an empty one will do".
			content: recommendedContentFor(recommended, source.sourceKind),
		});
	}
	if (role === "OPTIONAL") {
		optional.push({ sourceKind: source.sourceKind });
	}
	return { ...requirements, requiredEvidence: required, optionalContext: optional };
}

/**
 * The server digests a limitation's code together with its text, and that digest decides whether a
 * practice's independent validation is still current and whether a curated update changed review
 * behaviour or only wording. A random code would make retyping the same sentence look like a new
 * rule, so the code is derived from the text: identical wording always yields the identical code.
 */
function recommendedContentFor(
	recommended: PracticeAutomatedReviewPolicy,
	sourceKind: string,
): PracticeAutomatedReviewPolicy["requiredEvidence"][number]["content"] {
	return recommended.requiredEvidence.find((requirement) => requirement.sourceKind === sourceKind)
		?.content;
}

/**
 * One requirement, as a checkbox. Each is a yes/no decision, and the caller renders only the
 * requirements the source can actually establish, so an option is never shown that cannot be chosen.
 */
function RequirementCheckbox({
	id,
	label,
	sourceLabel,
	disabled,
	checked,
	onCheckedChange,
}: {
	id: string;
	label: string;
	sourceLabel: string;
	disabled: boolean;
	checked: boolean;
	onCheckedChange: (checked: boolean) => void;
}) {
	return (
		<FieldLabel htmlFor={id} className="w-auto font-normal">
			<Field orientation="horizontal" className="w-auto gap-2" data-disabled={disabled}>
				<Checkbox
					id={id}
					disabled={disabled}
					checked={checked}
					onCheckedChange={(next) => onCheckedChange(next === true)}
				/>
				<FieldTitle className="font-normal">
					{label} <span className="sr-only">for {sourceLabel}</span>
				</FieldTitle>
			</Field>
		</FieldLabel>
	);
}

function patchRequirement(
	requirements: PracticeAutomatedReviewPolicy,
	sourceKind: string,
	patch: Partial<PracticeAutomatedReviewPolicy["requiredEvidence"][number]>,
): PracticeAutomatedReviewPolicy {
	return {
		...requirements,
		requiredEvidence: requirements.requiredEvidence.map((item) =>
			item.sourceKind === sourceKind ? { ...item, ...patch } : item,
		),
	};
}

function limitationCodeFor(description: string) {
	const slug = description
		.toUpperCase()
		.replace(/[^A-Z0-9]+/g, "_")
		.replace(/^_+|_+$/g, "")
		.slice(0, 63);
	return /^[A-Z][A-Z0-9_]{2,63}$/.test(slug) ? slug : `LIMITATION_${fnv1a(description)}`;
}

/** Deterministic, non-cryptographic fallback for text that cannot form a legal code. */
function fnv1a(input: string) {
	let hash = 0x811c9dc5;
	for (let index = 0; index < input.length; index += 1) {
		hash ^= input.charCodeAt(index);
		hash = Math.imul(hash, 0x01000193) >>> 0;
	}
	return hash.toString(16).toUpperCase().padStart(8, "0");
}

function matchesRecommendedEvidence(
	value: PracticeAutomatedReviewPolicy,
	recommended: PracticeAutomatedReviewPolicy,
) {
	return (
		value.sourceContractVersion === recommended.sourceContractVersion &&
		value.evidenceProfile === recommended.evidenceProfile &&
		value.whenEvidenceIsInsufficient === recommended.whenEvidenceIsInsufficient &&
		deepEqual(value.requiredEvidence, recommended.requiredEvidence) &&
		deepEqual(value.optionalContext, recommended.optionalContext)
	);
}

function humanReviewReasonIsMissing(requirements: PracticeAutomatedReviewPolicy) {
	return (
		requirements.automatedReview.evidenceSufficiency === "DECLARED_EVIDENCE_INSUFFICIENT" &&
		requirements.knownLimitations[0] !== undefined &&
		!requirements.knownLimitations[0].description.trim()
	);
}

export function practiceEvidenceErrorTarget(requirements: PracticeAutomatedReviewPolicy) {
	return humanReviewReasonIsMissing(requirements)
		? "practice-human-review-reason"
		: "practice-evidence-heading";
}

export function practiceEvidenceError(requirements: PracticeAutomatedReviewPolicy) {
	const noAutomatedReview = requirements.automatedReview.mode === "NONE";
	if (
		noAutomatedReview &&
		(requirements.requiredEvidence.length > 0 ||
			requirements.optionalContext.length > 0 ||
			requirements.knownLimitations.length > 0)
	) {
		return "Guidance only cannot require evidence or declare evidence limitations.";
	}
	if (!noAutomatedReview && requirements.requiredEvidence.length === 0) {
		return "Choose at least one required evidence source.";
	}
	for (const limitation of requirements.knownLimitations) {
		if (!/^[A-Z][A-Z0-9_]{2,63}$/.test(limitation.code)) {
			return "Limitation identifiers must use 3–64 uppercase letters, numbers, and underscores.";
		}
		if (!limitation.description.trim() || limitation.description.length > 500) {
			return "Each limitation needs a description of 1–500 characters.";
		}
	}
	if (
		requirements.automatedReview.evidenceSufficiency === "DECLARED_EVIDENCE_INSUFFICIENT" &&
		requirements.knownLimitations.length === 0
	) {
		return "Explain at least one limitation that requires additional context.";
	}
	return undefined;
}

export interface PracticeEvidenceEditorProps {
	options: PracticeWorkTypeDefinitionOptions;
	value: PracticeAutomatedReviewPolicy;
	onChange: (value: PracticeAutomatedReviewPolicy) => void;
	error?: string;
	disabled?: boolean;
}

export function PracticeEvidenceEditor({
	options,
	value,
	onChange,
	error,
	disabled = false,
}: PracticeEvidenceEditorProps) {
	const [open, setOpen] = useState(Boolean(error));
	const profileKey = `${value.sourceContractVersion}:${value.evidenceProfile}`;
	const savedAutomatedRequirements = useRef<Map<string, PracticeAutomatedReviewPolicy>>(
		new Map(value.automatedReview.mode === "NONE" ? [] : [[profileKey, value]]),
	);
	const addLimitationButton = useRef<HTMLButtonElement>(null);
	// Reveal the customization panel when a submit lands an error the user cannot see from the
	// collapsed state. Adjusting during render rather than in an effect keeps it keyed on the error
	// itself, so editing afterwards no longer re-opens the panel under the caret.
	// https://react.dev/learn/you-might-not-need-an-effect
	const [lastError, setLastError] = useState(error);
	if (error !== lastError) {
		setLastError(error);
		if (error && !humanReviewReasonIsMissing(value)) setOpen(true);
	}
	const focusLimitation = (focusTarget: string) => {
		requestAnimationFrame(() => {
			if (focusTarget === "add") {
				addLimitationButton.current?.focus();
			} else {
				document.getElementById(`practice-limitation-description-${focusTarget}`)?.focus();
			}
		});
	};
	const requiredSources = value.requiredEvidence.map((requirement) => ({
		...requirement,
		label: evidenceSourceLabel(requirement.sourceKind, options.allowedSources),
	}));
	const optionalSources = value.optionalContext.map((requirement) =>
		evidenceSourceLabel(requirement.sourceKind, options.allowedSources),
	);
	const mentoringSupport = mentoringSupportOf(value);
	const limitationOffset = mentoringSupport === "HUMAN_CONTEXT_REQUIRED" ? 1 : 0;
	const editableLimitations = value.knownLimitations.slice(limitationOffset);
	const supportsAiReview = options.supportedAutomatedReviewModes.includes("LANGUAGE_MODEL");
	const unavailableRequiredSources = value.requiredEvidence.filter((requirement) => {
		const source = options.allowedSources.find(
			(item) => item.sourceKind === requirement.sourceKind,
		);
		return !source?.authorizedForAutomatedReview;
	});
	const noAutomatedReview = value.automatedReview.mode === "NONE";
	const canAttemptReview = mentoringSupport === "AI_SUPPORTED" && supportsAiReview;
	const humanReviewReasonMissing = humanReviewReasonIsMissing(value);
	const showHumanReviewReasonError = Boolean(error) && humanReviewReasonMissing;
	const usesRecommendedEvidence = matchesRecommendedEvidence(
		value,
		options.recommendedRequirements,
	);
	const updateMentoringSupport = (next: MentoringSupport) => {
		if (next === "GUIDANCE_ONLY") {
			if (!noAutomatedReview) savedAutomatedRequirements.current.set(profileKey, value);
			onChange({
				...value,
				automatedReview: { mode: "NONE", evidenceSufficiency: "NONE" },
				requiredEvidence: [],
				optionalContext: [],
				knownLimitations: [],
			});
			return;
		}
		const restored = noAutomatedReview
			? (savedAutomatedRequirements.current.get(profileKey) ?? options.recommendedRequirements)
			: value;
		const needsHumanContext = next === "HUMAN_CONTEXT_REQUIRED";
		const needsLimitation = needsHumanContext && restored.knownLimitations.length === 0;
		onChange({
			...restored,
			automatedReview: {
				mode: "LANGUAGE_MODEL",
				evidenceSufficiency: needsHumanContext
					? "DECLARED_EVIDENCE_INSUFFICIENT"
					: "SUFFICIENT_WHEN_REQUIREMENTS_MET",
			},
			knownLimitations: needsLimitation
				? [{ code: limitationCodeFor(""), description: "" }]
				: restored.knownLimitations.filter((limitation) => limitation.description.trim()),
		});
	};

	return (
		<section
			className="space-y-4"
			aria-labelledby="practice-evidence-heading"
			aria-describedby={error ? "practice-evidence-error" : undefined}
		>
			<div>
				<h2
					id="practice-evidence-heading"
					className="text-lg font-semibold"
					tabIndex={-1}
					aria-describedby={error ? "practice-evidence-error" : undefined}
				>
					How Hephaestus can help
				</h2>
				<p className="text-sm text-muted-foreground">
					Choose the responsible level of support. This does not limit what a developer, peer, or
					human mentor can observe.
				</p>
			</div>

			<RadioGroup
				value={mentoringSupport}
				onValueChange={(next) => {
					if (next) updateMentoringSupport(next as MentoringSupport);
				}}
				className="gap-3"
				aria-label="How Hephaestus supports this practice"
			>
				<FieldLabel htmlFor="practice-mentoring-ai-supported">
					<Field orientation="horizontal" data-disabled={disabled || !supportsAiReview}>
						<FieldContent>
							<FieldTitle>AI-supported mentoring</FieldTitle>
							<FieldDescription>
								Hephaestus may review connected work and offer practice-focused guidance. It skips
								the practice when required evidence is unavailable.
								{!supportsAiReview && (
									<> This work type has no AI review available on this instance.</>
								)}
							</FieldDescription>
						</FieldContent>
						<RadioGroupItem
							id="practice-mentoring-ai-supported"
							value="AI_SUPPORTED"
							disabled={disabled || !supportsAiReview}
						/>
					</Field>
				</FieldLabel>
				<FieldLabel htmlFor="practice-mentoring-human-context">
					<Field orientation="horizontal" data-disabled={disabled}>
						<FieldContent>
							<FieldTitle>Human review needed</FieldTitle>
							<FieldDescription>
								Connected work cannot support a responsible conclusion. Hephaestus steps back so a
								developer, peer, or mentor can review it.
							</FieldDescription>
						</FieldContent>
						<RadioGroupItem
							id="practice-mentoring-human-context"
							value="HUMAN_CONTEXT_REQUIRED"
							disabled={disabled}
						/>
					</Field>
				</FieldLabel>
				<FieldLabel htmlFor="practice-mentoring-guidance-only">
					<Field orientation="horizontal" data-disabled={disabled}>
						<FieldContent>
							<FieldTitle>Guidance only</FieldTitle>
							<FieldDescription>
								Keep the criteria and guidance without asking Hephaestus to review this practice.
							</FieldDescription>
						</FieldContent>
						<RadioGroupItem
							id="practice-mentoring-guidance-only"
							value="GUIDANCE_ONLY"
							disabled={disabled}
						/>
					</Field>
				</FieldLabel>
			</RadioGroup>

			{mentoringSupport === "HUMAN_CONTEXT_REQUIRED" && value.knownLimitations[0] && (
				<Field data-invalid={showHumanReviewReasonError || undefined}>
					<FieldLabel htmlFor="practice-human-review-reason">
						Why is human review needed? *
					</FieldLabel>
					<Input
						id="practice-human-review-reason"
						disabled={disabled}
						value={value.knownLimitations[0].description}
						onChange={(event) =>
							onChange({
								...value,
								knownLimitations: value.knownLimitations.map((limitation, index) =>
									index === 0
										? {
												...limitation,
												description: event.target.value,
												code: limitationCodeFor(event.target.value),
											}
										: limitation,
								),
							})
						}
						maxLength={500}
						aria-invalid={showHumanReviewReasonError || undefined}
						aria-describedby={
							showHumanReviewReasonError
								? "practice-human-review-reason-error practice-human-review-reason-description"
								: "practice-human-review-reason-description"
						}
					/>
					<FieldDescription id="practice-human-review-reason-description">
						Name the context a person may have that the connected work cannot provide.
					</FieldDescription>
					{showHumanReviewReasonError && (
						<FieldError id="practice-human-review-reason-error">
							Say what a person can see here that the connected work cannot show.
						</FieldError>
					)}
				</Field>
			)}

			{!noAutomatedReview && (
				<div className="rounded-lg border p-4 text-sm">
					<p className="font-medium">Evidence boundary</p>
					<dl className="mt-3 grid gap-3 sm:grid-cols-[9rem_1fr]">
						<dt className="font-medium text-muted-foreground">Must have</dt>
						<dd>
							{requiredSources.length > 0 ? (
								<ul className="space-y-1">
									{requiredSources.map((source) => (
										<li key={source.sourceKind}>
											{source.label} · {evidenceQualityLabel(source)}
										</li>
									))}
								</ul>
							) : (
								<span className="text-muted-foreground">No required source selected</span>
							)}
						</dd>
						<dt className="font-medium text-muted-foreground">May also use</dt>
						<dd>{optionalSources.join(", ") || "None"}</dd>
						<dt className="font-medium text-muted-foreground">Cannot establish</dt>
						<dd>
							{value.knownLimitations.length > 0 ? (
								<ul className="space-y-1">
									{value.knownLimitations.map((limitation) => (
										<li key={limitation.code}>
											{limitation.description || "A limitation still needs a description"}
										</li>
									))}
								</ul>
							) : (
								<span className="text-muted-foreground">No limitation documented</span>
							)}
						</dd>
					</dl>
					<p className="mt-3 text-muted-foreground">
						{canAttemptReview
							? "Hephaestus checks every required source before reviewing. Missing, incomplete, or outdated evidence makes it skip this practice instead of guessing."
							: mentoringSupport === "AI_SUPPORTED"
								? "No AI review is available for this work type on this instance, so these sources are recorded but nothing is reviewed."
								: "Even when these sources are ready, Hephaestus does not have enough context. It skips this practice."}
					</p>
				</div>
			)}

			{canAttemptReview && unavailableRequiredSources.length > 0 && (
				<Alert variant="warning">
					<TriangleAlert />
					<AlertTitle>This evidence requires an authorization decision</AlertTitle>
					<AlertDescription>
						{unavailableRequiredSources
							.map((source) => evidenceSourceLabel(source.sourceKind, options.allowedSources))
							.join(", ")}{" "}
						{unavailableRequiredSources.length === 1 ? "contains" : "contain"} private
						conversations. Hephaestus reads{" "}
						{unavailableRequiredSources.length === 1 ? "it" : "them"} only after an instance
						operator authorizes the source in{" "}
						<code className="font-mono text-xs">HEPHAESTUS_EVIDENCE_SENSITIVE_SOURCE_USES</code>.
						Sources such as pull requests and issues need no separate authorization. Until the
						source is authorized, Hephaestus skips automated review for this practice.
					</AlertDescription>
				</Alert>
			)}

			{!noAutomatedReview && (
				<Collapsible open={open} onOpenChange={setOpen}>
					<div className="flex flex-wrap items-center gap-2">
						<CollapsibleTrigger
							disabled={disabled}
							render={
								<Button type="button" variant="outline" disabled={disabled}>
									<ChevronRight className="size-4 transition-transform group-aria-expanded:rotate-90" />
									Customize evidence
								</Button>
							}
							className="group"
						/>
						{!usesRecommendedEvidence && (
							<Button
								type="button"
								variant="ghost"
								disabled={disabled}
								onClick={() =>
									onChange({
										...options.recommendedRequirements,
										automatedReview: value.automatedReview,
										knownLimitations: value.knownLimitations,
									})
								}
							>
								<RotateCcw className="size-4" />
								Use recommended evidence
							</Button>
						)}
					</div>
					<CollapsibleContent className="mt-4 space-y-6">
						<div>
							<p className="font-medium">Connected evidence</p>
							<p className="text-sm text-muted-foreground">
								Choose what Hephaestus must have and what may provide extra context. Selecting a
								source does not collect or authorize it; instance governance and workspace
								integrations control that separately.
							</p>
						</div>
						<FieldGroup className="gap-4">
							{options.allowedSources.map((source) => {
								const role = roleOf(value, source.sourceKind);
								const sourceLabel = source.displayName;
								const requirement = value.requiredEvidence.find(
									(item) => item.sourceKind === source.sourceKind,
								);
								return (
									<div key={source.sourceKind} className="rounded-lg border p-4">
										<div className="grid gap-3 sm:grid-cols-[1fr_12rem] sm:items-start">
											<div>
												<div className="flex flex-wrap items-center gap-2">
													<p className="font-medium">{sourceLabel}</p>
													{source.privacyClass === "SENSITIVE_PERSONAL" && (
														<Badge variant="outline">Private conversations</Badge>
													)}
													{!source.supportsEmpty && <Badge variant="outline">Never empty</Badge>}
													{!source.authorizedForAutomatedReview && (
														<Badge variant="warning">Needs authorization</Badge>
													)}
												</div>
												<p className="mt-1 text-sm text-muted-foreground">{source.description}</p>
											</div>
											<FieldSet>
												<FieldLegend variant="label">
													Use in this practice <span className="sr-only">for {sourceLabel}</span>
												</FieldLegend>
												<RadioGroup
													value={role}
													onValueChange={(nextRole) =>
														nextRole &&
														onChange(
															withRole(
																value,
																source,
																nextRole as EvidenceRole,
																options.recommendedRequirements,
															),
														)
													}
													className="flex flex-wrap gap-x-5 gap-y-2"
													aria-label={`Use in this practice for ${sourceLabel}`}
												>
													{EVIDENCE_ROLE_OPTIONS.map((option) => (
														<FieldLabel
															key={option.value}
															htmlFor={`practice-evidence-${source.sourceKind}-${option.value}`}
															className="w-auto font-normal"
														>
															<Field
																orientation="horizontal"
																className="w-auto gap-2"
																data-disabled={disabled}
															>
																<RadioGroupItem
																	id={`practice-evidence-${source.sourceKind}-${option.value}`}
																	value={option.value}
																	disabled={disabled}
																/>
																<FieldTitle className="font-normal">{option.label}</FieldTitle>
															</Field>
														</FieldLabel>
													))}
												</RadioGroup>
											</FieldSet>
										</div>
										{requirement && (
											<div className="mt-4 border-t pt-4">
												<FieldSet>
													<FieldLegend variant="label">
														Minimum quality <span className="sr-only">for {sourceLabel}</span>
													</FieldLegend>
													<FieldDescription>
														Hephaestus skips the practice when a checked requirement is not met.
														Only the requirements this source can establish are shown.
													</FieldDescription>
													<div className="flex flex-wrap gap-x-5 gap-y-2">
														{source.supportsComplete && (
															<RequirementCheckbox
																id={`practice-completeness-${source.sourceKind}`}
																label="Must be complete"
																sourceLabel={sourceLabel}
																disabled={disabled}
																checked={requirement.completeness === "COMPLETE"}
																onCheckedChange={(checked) =>
																	onChange(
																		patchRequirement(value, source.sourceKind, {
																			completeness: checked ? "COMPLETE" : "NO_REQUIREMENT",
																		}),
																	)
																}
															/>
														)}
														{source.supportsCurrent && (
															<RequirementCheckbox
																id={`practice-freshness-${source.sourceKind}`}
																label="Must be current"
																sourceLabel={sourceLabel}
																disabled={disabled}
																checked={requirement.freshness === "CURRENT"}
																onCheckedChange={(checked) =>
																	onChange(
																		patchRequirement(value, source.sourceKind, {
																			freshness: checked ? "CURRENT" : "NO_REQUIREMENT",
																		}),
																	)
																}
															/>
														)}
														{source.supportsEmpty && (
															<RequirementCheckbox
																id={`practice-content-${source.sourceKind}`}
																label="Must not be empty"
																sourceLabel={sourceLabel}
																disabled={disabled}
																checked={requirement.content === "NON_EMPTY"}
																onCheckedChange={(checked) =>
																	onChange(
																		patchRequirement(value, source.sourceKind, {
																			content: checked ? "NON_EMPTY" : "NO_REQUIREMENT",
																		}),
																	)
																}
															/>
														)}
													</div>
												</FieldSet>
											</div>
										)}
									</div>
								);
							})}
						</FieldGroup>

						<div className="space-y-3">
							<div>
								<p className="font-medium">What this evidence cannot support</p>
								<p className="text-sm text-muted-foreground">
									State which claims these sources cannot support, even when every requirement
									passes.
								</p>
							</div>
							{/*
							 * Under "Human review needed" the first limitation IS the reason, edited above under
							 * its own label. Listing it again binds one value to two controls with contradictory
							 * labels, only one of which carries the invalid state.
							 */}
							{editableLimitations.map((limitation, offsetIndex) => {
								const index = offsetIndex + limitationOffset;
								// Identity for markup is the row's position; the code is derived from the text and
								// would change under the caret on every keystroke.
								const limitationId = String(index);
								return (
									<div
										key={limitationId}
										className="grid gap-3 rounded-lg border p-4 sm:grid-cols-[1fr_auto]"
									>
										<Field>
											<FieldLabel htmlFor={`practice-limitation-description-${limitationId}`}>
												Description{" "}
												<span className="sr-only">for limitation {offsetIndex + 1}</span>
											</FieldLabel>
											<Input
												disabled={disabled}
												id={`practice-limitation-description-${limitationId}`}
												value={limitation.description}
												onChange={(event) =>
													onChange({
														...value,
														knownLimitations: value.knownLimitations.map((item, itemIndex) =>
															itemIndex === index
																? {
																		...item,
																		description: event.target.value,
																		code: limitationCodeFor(event.target.value),
																	}
																: item,
														),
													})
												}
												maxLength={500}
											/>
										</Field>
										<Button
											type="button"
											disabled={disabled}
											variant="ghost"
											size="icon-sm"
											className="self-end"
											onClick={() => {
												const remaining = value.knownLimitations.filter(
													(_, itemIndex) => itemIndex !== index,
												);
												const focusTarget =
													remaining.length === limitationOffset
														? "add"
														: String(Math.min(index, remaining.length - 1));
												onChange({
													...value,
													knownLimitations: remaining,
												});
												focusLimitation(focusTarget);
											}}
											aria-label={`Remove limitation ${offsetIndex + 1}`}
										>
											<Trash2 className="size-4" />
										</Button>
									</div>
								);
							})}
							<Button
								ref={addLimitationButton}
								type="button"
								disabled={disabled}
								variant="outline"
								size="sm"
								onClick={() => {
									onChange({
										...value,
										knownLimitations: [
											...value.knownLimitations,
											{ code: limitationCodeFor(""), description: "" },
										],
									});
									focusLimitation(String(value.knownLimitations.length));
								}}
							>
								<Plus className="size-4" />
								Add limitation
							</Button>
						</div>
					</CollapsibleContent>
				</Collapsible>
			)}
			{error && <FieldError id="practice-evidence-error">{error}</FieldError>}
		</section>
	);
}
