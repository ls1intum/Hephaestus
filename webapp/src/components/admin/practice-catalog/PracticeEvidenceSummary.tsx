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

const OBSERVABILITY_LABELS: Record<PracticeEvidenceDeclaration["observability"], string> = {
	MECHANICAL: "Mechanically checkable",
	SEMANTIC: "Meaning requires judgment",
	CONDITIONALLY_OBSERVABLE: "Only observable in some cases",
	UNOBSERVABLE: "Not observable",
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
				<dt className="font-medium">How it can be judged</dt>
				<dd className="text-muted-foreground">{OBSERVABILITY_LABELS[declaration.observability]}</dd>
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
