import { ChevronRight, Plus, RotateCcw, Trash2, TriangleAlert } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import type {
	PracticeAutomatedReviewPolicy,
	PracticeEvidenceSourceOption,
	PracticeWorkTypeEvidenceOptions,
} from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
	Field,
	FieldContent,
	FieldDescription,
	FieldError,
	FieldGroup,
	FieldLabel,
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
import { evidenceQualityLabel, evidenceSourceLabel } from "./evidence-presentation";

type EvidenceRole = "NOT_USED" | "OPTIONAL" | "REQUIRED";
type MentoringSupport = "AI_SUPPORTED" | "HUMAN_CONTEXT_REQUIRED" | "GUIDANCE_ONLY";

const EVIDENCE_ROLE_OPTIONS = [
	{ value: "REQUIRED", label: "Required" },
	{ value: "OPTIONAL", label: "Optional context" },
	{ value: "NOT_USED", label: "Not used" },
] satisfies Array<{ value: EvidenceRole; label: string }>;

const PRIVACY_LABELS: Record<PracticeEvidenceSourceOption["privacyClass"], string> = {
	PUBLIC: "Public",
	INTERNAL: "Internal",
	PERSONAL: "Personal data",
	SENSITIVE_PERSONAL: "Sensitive personal data",
};

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
		});
	}
	if (role === "OPTIONAL") {
		optional.push({ sourceKind: source.sourceKind });
	}
	return { ...requirements, requiredEvidence: required, optionalContext: optional };
}

function newLimitationCode() {
	return `LIMITATION_${crypto.randomUUID().replaceAll("-", "").toUpperCase()}`;
}

export function practiceEvidenceError(requirements: PracticeAutomatedReviewPolicy) {
	const noAutomatedReview = requirements.automatedReview.mode === "NONE";
	if (
		noAutomatedReview &&
		(requirements.requiredEvidence.length > 0 ||
			requirements.optionalContext.length > 0 ||
			requirements.knownLimitations.length > 0)
	) {
		return "Practice guidance only cannot require evidence or declare evidence limitations.";
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
	options: PracticeWorkTypeEvidenceOptions;
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
	useEffect(() => {
		if (value.automatedReview.mode !== "NONE") {
			savedAutomatedRequirements.current.set(profileKey, value);
		}
	}, [profileKey, value]);
	useEffect(() => {
		if (error) setOpen(true);
	}, [error]);
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
	const supportsAiReview = options.supportedAutomatedReviewModes.includes("LANGUAGE_MODEL");
	const unavailableRequiredSources = value.requiredEvidence.filter((requirement) => {
		const source = options.allowedSources.find(
			(item) => item.sourceKind === requirement.sourceKind,
		);
		return !source?.authorizedForAutomatedReview;
	});
	const noAutomatedReview = value.automatedReview.mode === "NONE";
	const canAttemptReview = mentoringSupport === "AI_SUPPORTED" && supportsAiReview;
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
		onChange({
			...restored,
			automatedReview: {
				mode: "LANGUAGE_MODEL",
				evidenceSufficiency: needsHumanContext
					? "DECLARED_EVIDENCE_INSUFFICIENT"
					: "SUFFICIENT_WHEN_REQUIREMENTS_MET",
			},
			knownLimitations:
				needsHumanContext && restored.knownLimitations.length === 0
					? [{ code: newLimitationCode(), description: "" }]
					: restored.knownLimitations,
		});
		if (needsHumanContext) setOpen(true);
	};

	return (
		<section
			className="space-y-4"
			aria-labelledby="practice-evidence-heading"
			aria-describedby={error ? "practice-evidence-error" : undefined}
			aria-invalid={error ? true : undefined}
		>
			<div>
				<h2
					id="practice-evidence-heading"
					className="text-lg font-semibold"
					tabIndex={-1}
					aria-describedby={error ? "practice-evidence-error" : undefined}
				>
					AI-supported practice mentoring
				</h2>
				<p className="text-sm text-muted-foreground">
					Choose how Hephaestus supports this practice. People may use context that Hephaestus
					cannot access.
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
					<Field orientation="horizontal" data-disabled={disabled || !supportsAiReview}>
						<FieldContent>
							<FieldTitle>Human context needed</FieldTitle>
							<FieldDescription>
								Connected work is not enough for responsible AI guidance. Keep the practice for
								self, peer, or mentor review.
							</FieldDescription>
						</FieldContent>
						<RadioGroupItem
							id="practice-mentoring-human-context"
							value="HUMAN_CONTEXT_REQUIRED"
							disabled={disabled || !supportsAiReview}
						/>
					</Field>
				</FieldLabel>
				<FieldLabel htmlFor="practice-mentoring-guidance-only">
					<Field orientation="horizontal" data-disabled={disabled}>
						<FieldContent>
							<FieldTitle>Practice guidance only</FieldTitle>
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

			{!noAutomatedReview && (
				<div className="rounded-lg border p-4 text-sm">
					<p className="font-medium">Evidence Hephaestus can use</p>
					<div className="mt-3 grid gap-3 sm:grid-cols-2">
						<div>
							<p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
								Required
							</p>
							{requiredSources.length > 0 ? (
								<ul className="mt-1 space-y-1">
									{requiredSources.map((source) => (
										<li key={source.sourceKind}>
											{source.label} · {evidenceQualityLabel(source)}
										</li>
									))}
								</ul>
							) : (
								<p className="mt-1 text-muted-foreground">No required source selected</p>
							)}
						</div>
						<div>
							<p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
								Optional context
							</p>
							<p className="mt-1">{optionalSources.join(", ") || "None"}</p>
						</div>
					</div>
					<p className="mt-3 text-muted-foreground">
						{mentoringSupport === "AI_SUPPORTED"
							? "Hephaestus checks every required source before reviewing. Missing, incomplete, or outdated evidence makes it skip this practice instead of guessing."
							: "These sources document what Hephaestus can access, but they are not enough for AI guidance. Hephaestus skips this practice."}
					</p>
				</div>
			)}

			{canAttemptReview && unavailableRequiredSources.length > 0 && (
				<Alert variant="warning">
					<TriangleAlert />
					<AlertTitle>Required evidence is not authorized</AlertTitle>
					<AlertDescription>
						An instance operator must authorize{" "}
						{unavailableRequiredSources
							.map((source) => evidenceSourceLabel(source.sourceKind, options.allowedSources))
							.join(", ")}{" "}
						for AI-supported mentoring through the source-governance configuration. The workspace
						integration must also provide them. Until then, Hephaestus skips this practice.
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
						<Button
							type="button"
							variant="ghost"
							disabled={disabled}
							onClick={() =>
								onChange({
									...options.recommendedRequirements,
									automatedReview: value.automatedReview,
								})
							}
						>
							<RotateCcw className="size-4" />
							Use recommended evidence
						</Button>
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
													<Badge variant="outline">{PRIVACY_LABELS[source.privacyClass]}</Badge>
													{source.supportsEmpty && (
														<Badge variant="outline">Empty can be valid</Badge>
													)}
													{!source.authorizedForAutomatedReview && (
														<Badge variant="warning">Not authorized on this instance</Badge>
													)}
												</div>
												<p className="mt-1 text-sm text-muted-foreground">{source.description}</p>
											</div>
											<Field>
												<FieldLabel htmlFor={`practice-evidence-${source.sourceKind}`}>
													Use in this practice <span className="sr-only">for {sourceLabel}</span>
												</FieldLabel>
												<Select
													disabled={disabled}
													items={EVIDENCE_ROLE_OPTIONS}
													value={role}
													onValueChange={(nextRole) =>
														onChange(withRole(value, source, nextRole as EvidenceRole))
													}
												>
													<SelectTrigger id={`practice-evidence-${source.sourceKind}`}>
														<SelectValue />
													</SelectTrigger>
													<SelectContent>
														<SelectItem value="REQUIRED">Required</SelectItem>
														<SelectItem value="OPTIONAL">Optional context</SelectItem>
														<SelectItem value="NOT_USED">Not used</SelectItem>
													</SelectContent>
												</Select>
											</Field>
										</div>
										{requirement && (
											<div className="mt-4 grid gap-4 border-t pt-4 sm:grid-cols-2">
												<Field>
													<FieldLabel htmlFor={`practice-completeness-${source.sourceKind}`}>
														Minimum completeness <span className="sr-only">for {sourceLabel}</span>
													</FieldLabel>
													<Select
														disabled={disabled}
														items={[
															...(source.supportsComplete
																? [{ value: "COMPLETE", label: "Complete" }]
																: []),
															{ value: "NO_REQUIREMENT", label: "No completeness requirement" },
														]}
														value={requirement.completeness}
														onValueChange={(completeness) =>
															onChange({
																...value,
																requiredEvidence: value.requiredEvidence.map((item) =>
																	item.sourceKind === source.sourceKind
																		? {
																				...item,
																				completeness: completeness as "NO_REQUIREMENT" | "COMPLETE",
																			}
																		: item,
																),
															})
														}
													>
														<SelectTrigger id={`practice-completeness-${source.sourceKind}`}>
															<SelectValue />
														</SelectTrigger>
														<SelectContent>
															{source.supportsComplete && (
																<SelectItem value="COMPLETE">Complete</SelectItem>
															)}
															<SelectItem value="NO_REQUIREMENT">
																No completeness requirement
															</SelectItem>
														</SelectContent>
													</Select>
												</Field>
												<Field>
													<FieldLabel htmlFor={`practice-freshness-${source.sourceKind}`}>
														Minimum freshness <span className="sr-only">for {sourceLabel}</span>
													</FieldLabel>
													<Select
														disabled={disabled}
														items={[
															...(source.supportsCurrent
																? [{ value: "CURRENT", label: "Current" }]
																: []),
															{ value: "NO_REQUIREMENT", label: "No freshness requirement" },
														]}
														value={requirement.freshness}
														onValueChange={(freshness) =>
															onChange({
																...value,
																requiredEvidence: value.requiredEvidence.map((item) =>
																	item.sourceKind === source.sourceKind
																		? {
																				...item,
																				freshness: freshness as "NO_REQUIREMENT" | "CURRENT",
																			}
																		: item,
																),
															})
														}
													>
														<SelectTrigger id={`practice-freshness-${source.sourceKind}`}>
															<SelectValue />
														</SelectTrigger>
														<SelectContent>
															{source.supportsCurrent && (
																<SelectItem value="CURRENT">Current</SelectItem>
															)}
															<SelectItem value="NO_REQUIREMENT">
																No freshness requirement
															</SelectItem>
														</SelectContent>
													</Select>
												</Field>
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
							{value.knownLimitations.map((limitation, index) => {
								const limitationId = limitation.code;
								return (
									<div
										key={limitationId}
										className="grid gap-3 rounded-lg border p-4 sm:grid-cols-[1fr_auto]"
									>
										<Field>
											<FieldLabel htmlFor={`practice-limitation-description-${limitationId}`}>
												Description <span className="sr-only">for limitation {index + 1}</span>
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
																? { ...item, description: event.target.value }
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
													remaining[index]?.code ?? remaining[index - 1]?.code ?? "add";
												onChange({
													...value,
													knownLimitations: remaining,
												});
												focusLimitation(focusTarget);
											}}
											aria-label={`Remove limitation ${index + 1}`}
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
									const limitationCode = newLimitationCode();
									onChange({
										...value,
										knownLimitations: [
											...value.knownLimitations,
											{ code: limitationCode, description: "" },
										],
									});
									focusLimitation(limitationCode);
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
