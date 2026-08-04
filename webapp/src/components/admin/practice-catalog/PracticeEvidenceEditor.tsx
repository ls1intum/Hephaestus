import { ChevronRight, Info, Plus, RotateCcw, Trash2, TriangleAlert } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import type {
	PracticeAutomatedAssessmentPolicy,
	PracticeEvidenceSourceOption,
	PracticeWorkTypeEvidenceOptions,
} from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import {
	assessmentModeLabel,
	canAttemptAutomatedAssessment,
	evidenceQualityLabel,
	evidenceSourceLabel,
	evidenceSufficiencyLabel,
} from "./evidence-presentation";

type EvidenceRole = "NOT_USED" | "OPTIONAL" | "REQUIRED";

type AutomatedAssessment = PracticeAutomatedAssessmentPolicy["automatedAssessment"];

const ASSESSMENT_MODE_OPTIONS: Array<{
	value: AutomatedAssessment["mode"];
	label: string;
	description: string;
}> = [
	{
		value: "LANGUAGE_MODEL",
		label: assessmentModeLabel("LANGUAGE_MODEL"),
		description: "Hephaestus assesses the reviewed work using a language model.",
	},
	{
		value: "NONE",
		label: assessmentModeLabel("NONE"),
		description: "Hephaestus does not assess reviewed work against this practice.",
	},
];

const SUFFICIENCY_OPTIONS: Array<{
	value: Exclude<AutomatedAssessment["evidenceSufficiency"], "NONE">;
	label: string;
	description: string;
}> = [
	{
		value: "SUFFICIENT_WHEN_REQUIREMENTS_MET",
		label: evidenceSufficiencyLabel("SUFFICIENT_WHEN_REQUIREMENTS_MET"),
		description: "Hephaestus may assess the reviewed work after every required source passes.",
	},
	{
		value: "DECLARED_EVIDENCE_INSUFFICIENT",
		label: evidenceSufficiencyLabel("DECLARED_EVIDENCE_INSUFFICIENT"),
		description: "Hephaestus skips this practice because the selected evidence is not enough.",
	},
];

const EVIDENCE_ROLE_OPTIONS = [
	{ value: "REQUIRED", label: "Required" },
	{ value: "OPTIONAL", label: "Optional context" },
	{ value: "NOT_USED", label: "Not used" },
] satisfies Array<{ value: EvidenceRole; label: string }>;

const NO_EVIDENCE_CHECK_OPTION = [{ value: "NONE", label: "No evidence check" }];

const PRIVACY_LABELS: Record<PracticeEvidenceSourceOption["privacyClass"], string> = {
	PUBLIC: "Public",
	INTERNAL: "Internal",
	PERSONAL: "Personal data",
	SENSITIVE_PERSONAL: "Sensitive personal data",
};

function roleOf(requirements: PracticeAutomatedAssessmentPolicy, sourceKind: string): EvidenceRole {
	if (requirements.requiredEvidence.some((requirement) => requirement.sourceKind === sourceKind)) {
		return "REQUIRED";
	}
	if (requirements.optionalContext.some((requirement) => requirement.sourceKind === sourceKind)) {
		return "OPTIONAL";
	}
	return "NOT_USED";
}

function withRole(
	requirements: PracticeAutomatedAssessmentPolicy,
	source: PracticeEvidenceSourceOption,
	role: EvidenceRole,
): PracticeAutomatedAssessmentPolicy {
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

export function practiceEvidenceError(requirements: PracticeAutomatedAssessmentPolicy) {
	const noAutomatedAssessment = requirements.automatedAssessment.mode === "NONE";
	if (
		noAutomatedAssessment &&
		(requirements.requiredEvidence.length > 0 ||
			requirements.optionalContext.length > 0 ||
			requirements.knownLimitations.length > 0)
	) {
		return "A practice without automated assessment cannot require evidence or declare evidence limitations.";
	}
	if (!noAutomatedAssessment && requirements.requiredEvidence.length === 0) {
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
		requirements.automatedAssessment.evidenceSufficiency === "DECLARED_EVIDENCE_INSUFFICIENT" &&
		requirements.knownLimitations.length === 0
	) {
		return "Explain at least one limitation that requires additional context.";
	}
	return undefined;
}

export interface PracticeEvidenceEditorProps {
	options: PracticeWorkTypeEvidenceOptions;
	value: PracticeAutomatedAssessmentPolicy;
	onChange: (value: PracticeAutomatedAssessmentPolicy) => void;
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
	const savedAutomatedRequirements = useRef<Map<string, PracticeAutomatedAssessmentPolicy>>(
		new Map(value.automatedAssessment.mode === "NONE" ? [] : [[profileKey, value]]),
	);
	const addLimitationButton = useRef<HTMLButtonElement>(null);
	useEffect(() => {
		if (value.automatedAssessment.mode !== "NONE") {
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
	const mode = ASSESSMENT_MODE_OPTIONS.find(
		(option) => option.value === value.automatedAssessment.mode,
	);
	const assessmentModeOptions = ASSESSMENT_MODE_OPTIONS.filter(
		(option) =>
			option.value === "NONE" ||
			options.supportedAutomatedAssessmentModes.includes(option.value) ||
			value.automatedAssessment.mode === option.value,
	);
	const evidenceSufficiency = SUFFICIENCY_OPTIONS.find(
		(option) => option.value === value.automatedAssessment.evidenceSufficiency,
	);
	const unavailableRequiredSources = value.requiredEvidence.filter((requirement) => {
		const source = options.allowedSources.find(
			(item) => item.sourceKind === requirement.sourceKind,
		);
		return !source?.authorizedForAutomatedAssessment;
	});
	const noAutomatedAssessment = value.automatedAssessment.mode === "NONE";
	const canAttemptAssessment = canAttemptAutomatedAssessment(
		value,
		options.supportedAutomatedAssessmentModes,
	);

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
					Evidence for automated assessment
				</h2>
				<p className="text-sm text-muted-foreground">
					Choose what Hephaestus must receive before it may assess reviewed work against this
					practice.
				</p>
			</div>

			{canAttemptAssessment && (
				<ol className="grid gap-3 text-sm sm:grid-cols-3">
					<li className="rounded-lg border p-3">
						<span className="font-medium">1. A practice review starts</span>
						<p className="mt-1 text-muted-foreground">A selected event or schedule starts it.</p>
					</li>
					<li className="rounded-lg border p-3">
						<span className="font-medium">2. Required evidence is checked</span>
						<p className="mt-1 text-muted-foreground">Every required source must pass.</p>
					</li>
					<li className="rounded-lg border p-3">
						<span className="font-medium">3. Assess or skip</span>
						<p className="mt-1 text-muted-foreground">Missing evidence never becomes a guess.</p>
					</li>
				</ol>
			)}

			<div className="rounded-lg border p-4 text-sm">
				<div className="flex flex-wrap items-center justify-between gap-2">
					<p className="font-medium">Evidence requirements</p>
					<Badge variant="outline">Source contract {value.sourceContractVersion}</Badge>
				</div>
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
							<p className="mt-1 text-muted-foreground">
								{noAutomatedAssessment ? "No automated assessment" : "No required source selected"}
							</p>
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
					{mode?.label}. {mode?.description}
					{evidenceSufficiency && ` ${evidenceSufficiency.label}.`}
				</p>
				<p className="mt-2 text-muted-foreground">
					This setting only controls Hephaestus. Human assessment, if applicable, is a separate
					process and is not collected.
				</p>
			</div>

			{canAttemptAssessment && unavailableRequiredSources.length > 0 && (
				<Alert variant="warning">
					<TriangleAlert />
					<AlertTitle>Required evidence is not authorized</AlertTitle>
					<AlertDescription>
						An instance operator must authorize{" "}
						{unavailableRequiredSources
							.map((source) => evidenceSourceLabel(source.sourceKind, options.allowedSources))
							.join(", ")}{" "}
						for automated assessment through the source-governance configuration. The workspace
						integration must also provide them. Until then, Hephaestus skips this practice.
					</AlertDescription>
				</Alert>
			)}

			{!noAutomatedAssessment && (
				<Alert>
					<Info />
					<AlertTitle>Declaring a source does not collect or authorize it</AlertTitle>
					<AlertDescription>
						The instance operator must authorize it, the workspace integration must provide it, and
						any model data transfer must be permitted. Hephaestus checks these separately.
					</AlertDescription>
				</Alert>
			)}

			<Collapsible open={open} onOpenChange={setOpen}>
				<div className="flex flex-wrap items-center gap-2">
					<CollapsibleTrigger
						disabled={disabled}
						render={
							<Button type="button" variant="outline" disabled={disabled}>
								<ChevronRight className="size-4 transition-transform group-aria-expanded:rotate-90" />
								Edit evidence requirements
							</Button>
						}
						className="group"
					/>
					<Button
						type="button"
						variant="ghost"
						disabled={disabled}
						onClick={() => onChange(options.recommendedRequirements)}
					>
						<RotateCcw className="size-4" />
						Use recommended requirements
					</Button>
				</div>
				<CollapsibleContent className="mt-4 space-y-6">
					<div className="grid gap-4 sm:grid-cols-2">
						<Field>
							<FieldLabel htmlFor="practice-assessment-mode">
								How should Hephaestus assess reviewed work?
							</FieldLabel>
							<Select
								disabled={disabled}
								items={assessmentModeOptions}
								value={value.automatedAssessment.mode}
								onValueChange={(mode) => {
									if (mode === "NONE") {
										savedAutomatedRequirements.current.set(profileKey, value);
										onChange({
											...value,
											automatedAssessment: { mode: "NONE", evidenceSufficiency: "NONE" },
											requiredEvidence: [],
											optionalContext: [],
											knownLimitations: [],
										});
										return;
									}
									const restored =
										savedAutomatedRequirements.current.get(profileKey) ??
										options.recommendedRequirements;
									onChange({
										...restored,
										automatedAssessment: {
											mode: mode as Exclude<AutomatedAssessment["mode"], "NONE">,
											evidenceSufficiency:
												restored.automatedAssessment.evidenceSufficiency === "NONE"
													? "SUFFICIENT_WHEN_REQUIREMENTS_MET"
													: restored.automatedAssessment.evidenceSufficiency,
										},
									});
								}}
							>
								<SelectTrigger
									id="practice-assessment-mode"
									aria-describedby="practice-assessment-mode-description"
								>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									{assessmentModeOptions.map((option) => (
										<SelectItem
											key={option.value}
											value={option.value}
											disabled={
												option.value !== "NONE" &&
												!options.supportedAutomatedAssessmentModes.includes(option.value)
											}
										>
											{option.label}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
							<FieldDescription id="practice-assessment-mode-description">
								{mode?.description}
							</FieldDescription>
						</Field>

						<Field>
							<FieldLabel htmlFor="practice-evidence-sufficiency">
								When evidence checks pass, is it enough?
							</FieldLabel>
							<Select
								disabled={disabled || value.automatedAssessment.mode === "NONE"}
								items={noAutomatedAssessment ? NO_EVIDENCE_CHECK_OPTION : SUFFICIENCY_OPTIONS}
								value={value.automatedAssessment.evidenceSufficiency}
								onValueChange={(evidenceSufficiency) =>
									onChange({
										...value,
										automatedAssessment: {
											...value.automatedAssessment,
											evidenceSufficiency: evidenceSufficiency as Exclude<
												AutomatedAssessment["evidenceSufficiency"],
												"NONE"
											>,
										},
									})
								}
							>
								<SelectTrigger
									id="practice-evidence-sufficiency"
									aria-describedby="practice-evidence-sufficiency-description"
								>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									{noAutomatedAssessment ? (
										<SelectItem value="NONE">No evidence check</SelectItem>
									) : (
										SUFFICIENCY_OPTIONS.map((option) => (
											<SelectItem key={option.value} value={option.value}>
												{option.label}
											</SelectItem>
										))
									)}
								</SelectContent>
							</Select>
							<FieldDescription id="practice-evidence-sufficiency-description">
								{value.automatedAssessment.mode === "NONE"
									? "No evidence check is needed without automated assessment."
									: evidenceSufficiency?.description}
							</FieldDescription>
						</Field>
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
												{!source.authorizedForAutomatedAssessment && (
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
												disabled={disabled || noAutomatedAssessment}
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
													disabled={disabled || noAutomatedAssessment}
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
													disabled={disabled || noAutomatedAssessment}
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
														<SelectItem value="NO_REQUIREMENT">No freshness requirement</SelectItem>
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
								State which claims these sources cannot support, even when every requirement passes.
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
											disabled={disabled || noAutomatedAssessment}
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
										disabled={disabled || noAutomatedAssessment}
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
							disabled={disabled || noAutomatedAssessment}
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

			{value.automatedAssessment.mode === "NONE" && (
				<Alert variant="warning">
					<TriangleAlert />
					<AlertTitle>No automated assessment</AlertTitle>
					<AlertDescription>
						Hephaestus cannot use this practice in automated reviews. Human assessment, if
						applicable, is a separate process and is not collected.
					</AlertDescription>
				</Alert>
			)}
			{value.automatedAssessment.evidenceSufficiency === "DECLARED_EVIDENCE_INSUFFICIENT" && (
				<Alert variant="warning">
					<TriangleAlert />
					<AlertTitle>Declared evidence is insufficient</AlertTitle>
					<AlertDescription>
						Hephaestus cannot use this practice in automated reviews. It skips the practice rather
						than guessing without the context described in its known limitations.
					</AlertDescription>
				</Alert>
			)}
			{error && <FieldError id="practice-evidence-error">{error}</FieldError>}
		</section>
	);
}
