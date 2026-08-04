import { ChevronRight, Info, Plus, RotateCcw, Trash2, TriangleAlert } from "lucide-react";
import { useState } from "react";
import type {
	PracticeEvidenceArtifactOptions,
	PracticeEvidenceDeclaration,
	PracticeEvidenceSourceOption,
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
import { evidenceQualityLabel, evidenceSourceLabel } from "./evidence-presentation";

type EvidenceRole = "NOT_USED" | "OPTIONAL" | "REQUIRED";

const OBSERVABILITY_OPTIONS: Array<{
	value: PracticeEvidenceDeclaration["observability"];
	label: string;
	description: string;
}> = [
	{
		value: "SEMANTIC",
		label: "Meaning requires judgment",
		description: "The evidence supports a reasoned assessment, usually with AI.",
	},
	{
		value: "MECHANICAL",
		label: "Mechanically checkable",
		description: "A deterministic rule can decide the practice from these inputs.",
	},
	{
		value: "CONDITIONALLY_OBSERVABLE",
		label: "Only observable in some cases",
		description: "Even complete evidence may not always support a judgment.",
	},
	{
		value: "UNOBSERVABLE",
		label: "Not observable",
		description: "Hephaestus will always decline this practice.",
	},
];

const PRIVACY_LABELS: Record<PracticeEvidenceSourceOption["privacyClass"], string> = {
	PUBLIC: "Public",
	INTERNAL: "Internal",
	PERSONAL: "Personal data",
	SENSITIVE_PERSONAL: "Sensitive personal data",
};

function roleOf(declaration: PracticeEvidenceDeclaration, sourceKind: string): EvidenceRole {
	if (declaration.required.some((requirement) => requirement.sourceKind === sourceKind)) {
		return "REQUIRED";
	}
	if (declaration.optional.some((requirement) => requirement.sourceKind === sourceKind)) {
		return "OPTIONAL";
	}
	return "NOT_USED";
}

function withRole(
	declaration: PracticeEvidenceDeclaration,
	source: PracticeEvidenceSourceOption,
	role: EvidenceRole,
): PracticeEvidenceDeclaration {
	const required = declaration.required.filter(
		(requirement) => requirement.sourceKind !== source.sourceKind,
	);
	const optional = declaration.optional.filter(
		(requirement) => requirement.sourceKind !== source.sourceKind,
	);
	if (role === "REQUIRED") {
		required.push({
			sourceKind: source.sourceKind,
			completeness: source.supportsComplete ? "COMPLETE" : "ANY",
			freshness: source.supportsCurrent ? "CURRENT" : "ANY",
		});
	}
	if (role === "OPTIONAL") {
		optional.push({ sourceKind: source.sourceKind, completeness: "ANY", freshness: "ANY" });
	}
	return { ...declaration, required, optional };
}

function nextBlindSpotCode(declaration: PracticeEvidenceDeclaration) {
	const used = new Set(declaration.blindSpots.map((blindSpot) => blindSpot.code));
	let index = 1;
	while (used.has(`LIMITATION_${index}`)) index += 1;
	return `LIMITATION_${index}`;
}

export function practiceEvidenceError(declaration: PracticeEvidenceDeclaration) {
	if (declaration.required.length === 0) return "Choose at least one required evidence source.";
	for (const blindSpot of declaration.blindSpots) {
		if (!/^[A-Z][A-Z0-9_]{2,63}$/.test(blindSpot.code)) {
			return "Limitation identifiers must use 3–64 uppercase letters, numbers, and underscores.";
		}
		if (!blindSpot.summary.trim() || blindSpot.summary.length > 500) {
			return "Each limitation needs a description of 1–500 characters.";
		}
	}
	return undefined;
}

export interface PracticeEvidenceEditorProps {
	options: PracticeEvidenceArtifactOptions;
	value: PracticeEvidenceDeclaration;
	onChange: (value: PracticeEvidenceDeclaration) => void;
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
	const [open, setOpen] = useState(false);
	const requiredSources = value.required.map((requirement) => ({
		...requirement,
		label: evidenceSourceLabel(requirement.sourceKind),
	}));
	const optionalSources = value.optional.map((requirement) =>
		evidenceSourceLabel(requirement.sourceKind),
	);
	const observability = OBSERVABILITY_OPTIONS.find(
		(option) => option.value === value.observability,
	);
	const unavailableRequiredSources = value.required.filter((requirement) => {
		const source = options.sources.find((item) => item.sourceKind === requirement.sourceKind);
		return !source?.authorizedForDetection;
	});

	return (
		<section className="space-y-4" aria-labelledby="practice-evidence-heading">
			<div>
				<h2 id="practice-evidence-heading" className="text-lg font-semibold" tabIndex={-1}>
					Evidence needed
				</h2>
				<p className="text-sm text-muted-foreground">
					Define what Hephaestus must know before it is allowed to judge this practice.
				</p>
			</div>

			<ol className="grid gap-3 text-sm sm:grid-cols-3">
				<li className="rounded-lg border p-3">
					<span className="font-medium">1. A review starts</span>
					<p className="mt-1 text-muted-foreground">
						A selected event or schedule creates a review.
					</p>
				</li>
				<li className="rounded-lg border p-3">
					<span className="font-medium">2. Evidence is checked</span>
					<p className="mt-1 text-muted-foreground">
						Required sources must meet this minimum quality.
					</p>
				</li>
				<li className="rounded-lg border p-3">
					<span className="font-medium">3. Judge or decline</span>
					<p className="mt-1 text-muted-foreground">
						Missing evidence skips this practice instead of asking AI to guess.
					</p>
				</li>
			</ol>

			<div className="rounded-lg border p-4 text-sm">
				<div className="flex flex-wrap items-center justify-between gap-2">
					<p className="font-medium">Current evidence rule</p>
					<Badge variant="outline">Evidence rules v{value.sourceContractVersion}</Badge>
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
							<p className="mt-1 text-destructive">No required source selected</p>
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
					{observability?.label}. {observability?.description}
				</p>
			</div>

			{unavailableRequiredSources.length > 0 && (
				<Alert variant="warning">
					<TriangleAlert />
					<AlertTitle>Required evidence is not enabled</AlertTitle>
					<AlertDescription>
						An instance operator must enable{" "}
						{unavailableRequiredSources
							.map((source) => evidenceSourceLabel(source.sourceKind))
							.join(", ")}{" "}
						before this practice can be judged. Until then, reviews will decline it.
					</AlertDescription>
				</Alert>
			)}

			<Alert>
				<Info />
				<AlertTitle>Declaring a source does not collect or authorize it</AlertTitle>
				<AlertDescription>
					The workspace integration must provide it and the instance operator must allow it.
					Hephaestus checks both on every review.
				</AlertDescription>
			</Alert>

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
						onClick={() => onChange(options.baseline)}
					>
						<RotateCcw className="size-4" />
						Use recommended rule
					</Button>
				</div>
				<CollapsibleContent className="mt-4 space-y-6">
					<FieldGroup className="gap-4">
						{options.sources.map((source) => {
							const role = roleOf(value, source.sourceKind);
							const requirement = value.required.find(
								(item) => item.sourceKind === source.sourceKind,
							);
							return (
								<div key={source.sourceKind} className="rounded-lg border p-4">
									<div className="grid gap-3 sm:grid-cols-[1fr_12rem] sm:items-start">
										<div>
											<div className="flex flex-wrap items-center gap-2">
												<p className="font-medium">{evidenceSourceLabel(source.sourceKind)}</p>
												<Badge variant="outline">{PRIVACY_LABELS[source.privacyClass]}</Badge>
												{source.supportsEmpty && (
													<Badge variant="outline">Empty can be valid</Badge>
												)}
												{!source.authorizedForDetection && (
													<Badge variant="warning">Not enabled on this instance</Badge>
												)}
											</div>
											<p className="mt-1 text-sm text-muted-foreground">{source.description}</p>
										</div>
										<Field>
											<FieldLabel htmlFor={`practice-evidence-${source.sourceKind}`}>
												Use in this practice
											</FieldLabel>
											<Select
												disabled={disabled}
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
													Minimum completeness
												</FieldLabel>
												<Select
													disabled={disabled}
													value={requirement.completeness}
													onValueChange={(completeness) =>
														onChange({
															...value,
															required: value.required.map((item) =>
																item.sourceKind === source.sourceKind
																	? { ...item, completeness: completeness as "ANY" | "COMPLETE" }
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
														<SelectItem value="ANY">Partial allowed</SelectItem>
													</SelectContent>
												</Select>
											</Field>
											<Field>
												<FieldLabel htmlFor={`practice-freshness-${source.sourceKind}`}>
													Minimum freshness
												</FieldLabel>
												<Select
													disabled={disabled}
													value={requirement.freshness}
													onValueChange={(freshness) =>
														onChange({
															...value,
															required: value.required.map((item) =>
																item.sourceKind === source.sourceKind
																	? { ...item, freshness: freshness as "ANY" | "CURRENT" }
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
														<SelectItem value="ANY">Any age</SelectItem>
													</SelectContent>
												</Select>
											</Field>
										</div>
									)}
								</div>
							);
						})}
					</FieldGroup>

					<Field>
						<FieldLabel htmlFor="practice-observability">
							How can this practice be judged?
						</FieldLabel>
						<Select
							disabled={disabled}
							value={value.observability}
							onValueChange={(observability) =>
								onChange({
									...value,
									observability: observability as PracticeEvidenceDeclaration["observability"],
								})
							}
						>
							<SelectTrigger id="practice-observability">
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								{OBSERVABILITY_OPTIONS.map((option) => (
									<SelectItem key={option.value} value={option.value}>
										{option.label}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
						<FieldDescription>{observability?.description}</FieldDescription>
					</Field>

					<div className="space-y-3">
						<div>
							<p className="font-medium">What this evidence cannot prove</p>
							<p className="text-sm text-muted-foreground">
								State what these sources still cannot prove, even when they are complete.
							</p>
						</div>
						{value.blindSpots.map((blindSpot, index) => (
							<div
								key={`${blindSpot.code}-${index}`}
								className="grid gap-3 rounded-lg border p-4 sm:grid-cols-[1fr_auto]"
							>
								<Field>
									<FieldLabel htmlFor={`practice-limitation-summary-${index}`}>
										Description
									</FieldLabel>
									<Input
										disabled={disabled}
										id={`practice-limitation-summary-${index}`}
										value={blindSpot.summary}
										onChange={(event) =>
											onChange({
												...value,
												blindSpots: value.blindSpots.map((item, itemIndex) =>
													itemIndex === index ? { ...item, summary: event.target.value } : item,
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
									onClick={() =>
										onChange({
											...value,
											blindSpots: value.blindSpots.filter((_, itemIndex) => itemIndex !== index),
										})
									}
									aria-label={`Remove limitation ${blindSpot.code}`}
								>
									<Trash2 className="size-4" />
								</Button>
							</div>
						))}
						<Button
							type="button"
							disabled={disabled}
							variant="outline"
							size="sm"
							onClick={() =>
								onChange({
									...value,
									blindSpots: [
										...value.blindSpots,
										{ code: nextBlindSpotCode(value), summary: "" },
									],
								})
							}
						>
							<Plus className="size-4" />
							Add limitation
						</Button>
					</div>
				</CollapsibleContent>
			</Collapsible>

			{value.observability === "UNOBSERVABLE" && (
				<Alert variant="warning">
					<TriangleAlert />
					<AlertTitle>This practice cannot produce a judgment</AlertTitle>
					<AlertDescription>
						It can be saved for documentation, but every detection attempt will decline it.
					</AlertDescription>
				</Alert>
			)}
			{error && <FieldError id="practice-evidence-error">{error}</FieldError>}
		</section>
	);
}
