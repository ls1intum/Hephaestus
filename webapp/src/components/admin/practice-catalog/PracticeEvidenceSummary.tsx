import type { PracticeEvidenceDeclaration, PracticeEvidenceValidation } from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { evidenceQualityLabel, evidenceSourceLabel } from "./evidence-presentation";

const VALIDATION_LABELS: Record<PracticeEvidenceValidation["status"], string> = {
	AUTHOR_DECLARED: "Not independently validated",
	INDEPENDENTLY_VALIDATED: "Independently validated",
	STALE: "Validation is stale",
	SUPERSEDED: "Validation is superseded",
};

const VALIDATION_VARIANTS: Record<
	PracticeEvidenceValidation["status"],
	"outline" | "success" | "warning"
> = {
	AUTHOR_DECLARED: "outline",
	INDEPENDENTLY_VALIDATED: "success",
	STALE: "warning",
	SUPERSEDED: "warning",
};

type DetectorCapability = PracticeEvidenceDeclaration["detectorCapability"];

const ASSESSMENT_METHOD_LABELS: Record<DetectorCapability["assessmentMethod"], string> = {
	MECHANICAL: "Declared mechanical assessment",
	SEMANTIC: "Declared semantic assessment",
	NONE: "Hephaestus cannot judge it",
};

const EVIDENCE_COVERAGE_LABELS: Record<DetectorCapability["evidenceCoverage"], string> = {
	DECLARED_REQUIREMENTS_SUFFICIENT: "Declared evidence covers every case",
	CONDITIONAL: "Declared evidence covers only some cases",
	NONE: "No automated evidence coverage",
};

function Requirements({
	requirements,
}: {
	requirements: PracticeEvidenceDeclaration["required"] | PracticeEvidenceDeclaration["optional"];
}) {
	if (requirements.length === 0) return <span>None</span>;
	return (
		<ul className="space-y-1">
			{requirements.map((requirement) => (
				<li key={requirement.sourceKind}>
					<span>{evidenceSourceLabel(requirement.sourceKind)}</span>
					<span className="text-muted-foreground"> · {evidenceQualityLabel(requirement)}</span>
					<code className="block break-all text-xs text-muted-foreground">
						{requirement.sourceKind}
					</code>
				</li>
			))}
		</ul>
	);
}

export interface PracticeEvidenceSummaryProps {
	declaration: PracticeEvidenceDeclaration;
	validation: PracticeEvidenceValidation;
	className?: string;
}

export interface PracticeEvidenceValidationSummaryProps {
	validation: PracticeEvidenceValidation;
}

export function PracticeEvidenceValidationSummary({
	validation,
}: PracticeEvidenceValidationSummaryProps) {
	return (
		<div className="space-y-1 text-sm">
			<Badge variant={VALIDATION_VARIANTS[validation.status]}>
				{VALIDATION_LABELS[validation.status]}
			</Badge>
			{validation.validator && (
				<p className="text-muted-foreground">
					{validation.validator}
					{validation.validationReference && <> · {validation.validationReference}</>}
				</p>
			)}
			{validation.status !== "AUTHOR_DECLARED" && (
				<p className="text-muted-foreground">
					Checked against source contract {validation.sourceContractVersion} and declaration{" "}
					<code className="break-all">{validation.declarationDigest}</code> · validated{" "}
					<RelativeTime value={validation.validatedAt} fallback="at an unknown time" />
				</p>
			)}
		</div>
	);
}

export function PracticeEvidenceSummary({
	declaration,
	validation,
	className,
}: PracticeEvidenceSummaryProps) {
	return (
		<dl className={cn("grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2", className)}>
			<div>
				<dt className="font-medium">Source rules</dt>
				<dd className="text-muted-foreground">
					Version {declaration.sourceContractVersion} · {declaration.profile}
				</dd>
			</div>
			<div>
				<dt className="font-medium">Hephaestus detectability</dt>
				<dd className="text-muted-foreground">
					{ASSESSMENT_METHOD_LABELS[declaration.detectorCapability.assessmentMethod]}
					<span className="block">
						{EVIDENCE_COVERAGE_LABELS[declaration.detectorCapability.evidenceCoverage]}
					</span>
					<span className="mt-1 block text-xs">
						This does not classify what a practitioner, peer, or human mentor can observe.
					</span>
				</dd>
			</div>
			<div>
				<dt className="font-medium">Required evidence</dt>
				<dd>
					<Requirements requirements={declaration.required} />
				</dd>
			</div>
			<div>
				<dt className="font-medium">Optional evidence</dt>
				<dd>
					<Requirements requirements={declaration.optional} />
				</dd>
			</div>
			<div>
				<dt className="font-medium">When required evidence is missing</dt>
				<dd className="text-muted-foreground">Skip this practice rather than guess</dd>
			</div>
			<div>
				<dt className="font-medium">Independent validation</dt>
				<dd>
					<PracticeEvidenceValidationSummary validation={validation} />
				</dd>
			</div>
			{declaration.blindSpots.length > 0 && (
				<div className="sm:col-span-2">
					<dt className="font-medium">What the evidence cannot prove</dt>
					<dd>
						<ul className="space-y-1">
							{declaration.blindSpots.map((blindSpot) => (
								<li key={blindSpot.code}>{blindSpot.summary}</li>
							))}
						</ul>
					</dd>
				</div>
			)}
		</dl>
	);
}
