import type {
	PracticeAutomatedReviewPolicy,
	PracticeAutomatedReviewValidation,
	PracticeEvidenceSourceOption,
} from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import {
	evidenceQualityLabel,
	evidenceSourceLabel,
	evidenceSufficiencyLabel,
	reviewModeLabel,
} from "./evidence-presentation";

const VALIDATION_LABELS: Record<PracticeAutomatedReviewValidation["status"], string> = {
	AUTHOR_DECLARED: "Not independently validated",
	INDEPENDENTLY_VALIDATED: "Automated review independently validated",
	STALE: "Validation is stale",
	SUPERSEDED: "Validation is superseded",
};

const VALIDATION_VARIANTS: Record<
	PracticeAutomatedReviewValidation["status"],
	"outline" | "success" | "warning"
> = {
	AUTHOR_DECLARED: "outline",
	INDEPENDENTLY_VALIDATED: "success",
	STALE: "warning",
	SUPERSEDED: "warning",
};

function RequiredEvidence({
	requirements,
	sources,
}: {
	requirements: PracticeAutomatedReviewPolicy["requiredEvidence"];
	sources: readonly PracticeEvidenceSourceOption[];
}) {
	if (requirements.length === 0) return <span>None</span>;
	return (
		<ul className="space-y-1">
			{requirements.map((requirement) => (
				<li key={requirement.sourceKind}>
					<span>{evidenceSourceLabel(requirement.sourceKind, sources)}</span>
					<span className="text-muted-foreground"> · {evidenceQualityLabel(requirement)}</span>
				</li>
			))}
		</ul>
	);
}

function OptionalContext({
	sources,
	options,
}: {
	sources: PracticeAutomatedReviewPolicy["optionalContext"];
	options: readonly PracticeEvidenceSourceOption[];
}) {
	if (sources.length === 0) return <span>None</span>;
	return (
		<ul className="space-y-1">
			{sources.map((source) => (
				<li key={source.sourceKind}>
					<span>{evidenceSourceLabel(source.sourceKind, options)}</span>
					<span className="text-muted-foreground">
						{" "}
						· Used when available; never blocks a review
					</span>
				</li>
			))}
		</ul>
	);
}

export interface PracticeEvidenceSummaryProps {
	policy: PracticeAutomatedReviewPolicy;
	validation: PracticeAutomatedReviewValidation;
	sources: readonly PracticeEvidenceSourceOption[];
	workTypeLabel: string;
	className?: string;
}

export interface PracticeAutomatedReviewValidationSummaryProps {
	validation: PracticeAutomatedReviewValidation;
}

export function PracticeAutomatedReviewValidationSummary({
	validation,
}: PracticeAutomatedReviewValidationSummaryProps) {
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
				<>
					<p className="text-muted-foreground">
						Validated for source contract {validation.sourceContractVersion}{" "}
						<RelativeTime value={validation.validatedAt} fallback="at an unknown time" />
					</p>
					<details className="text-muted-foreground">
						<summary className="cursor-pointer">Technical details</summary>
						<p className="mt-1">
							Review rules <code className="break-all">{validation.reviewRuleFingerprint}</code>
							<br />
							Review policy <code className="break-all">{validation.policyDigest}</code>
							{validation.evaluatorProcedureFingerprint && (
								<>
									<br />
									Evaluator procedure{" "}
									<code className="break-all">{validation.evaluatorProcedureFingerprint}</code>
								</>
							)}
						</p>
					</details>
				</>
			)}
		</div>
	);
}

export function PracticeEvidenceSummary({
	policy,
	validation,
	sources,
	workTypeLabel,
	className,
}: PracticeEvidenceSummaryProps) {
	return (
		<dl className={cn("grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2", className)}>
			<div>
				<dt className="font-medium">Source contract</dt>
				<dd className="text-muted-foreground">
					Version {policy.sourceContractVersion} · {workTypeLabel}
				</dd>
			</div>
			<div>
				<dt className="font-medium">Automated review</dt>
				<dd className="text-muted-foreground">
					{reviewModeLabel(policy.automatedReview.mode)}
					<span className="block">
						{evidenceSufficiencyLabel(policy.automatedReview.evidenceSufficiency)}
					</span>
					<span className="mt-1 block text-xs">
						This setting only controls Hephaestus. Human review, if applicable, is a separate
						process and is not collected.
					</span>
				</dd>
			</div>
			<div>
				<dt className="font-medium">Required evidence</dt>
				<dd>
					<RequiredEvidence requirements={policy.requiredEvidence} sources={sources} />
				</dd>
			</div>
			<div>
				<dt className="font-medium">Optional context</dt>
				<dd>
					<OptionalContext sources={policy.optionalContext} options={sources} />
				</dd>
			</div>
			<div>
				<dt className="font-medium">When evidence requirements are not met</dt>
				<dd className="text-muted-foreground">Skip this practice rather than guess</dd>
			</div>
			<div>
				<dt className="font-medium">Automated review validation</dt>
				<dd>
					<PracticeAutomatedReviewValidationSummary validation={validation} />
				</dd>
			</div>
			{policy.knownLimitations.length > 0 && (
				<div className="sm:col-span-2">
					<dt className="font-medium">What the evidence cannot support</dt>
					<dd>
						<ul className="space-y-1">
							{policy.knownLimitations.map((limitation) => (
								<li key={limitation.code}>{limitation.description}</li>
							))}
						</ul>
					</dd>
				</div>
			)}
		</dl>
	);
}
