import type {
	PracticeAutomatedReviewPolicy,
	PracticeAutomatedReviewValidation,
	PracticeBinding,
	PracticeEvidenceSourceOption,
	PracticeSignalOption,
} from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import {
	evidenceQualityLabel,
	evidenceSourceLabel,
	mentoringSupportLabel,
} from "./evidence-presentation";

const VALIDATION_LABELS: Record<PracticeAutomatedReviewValidation["status"], string> = {
	AUTHOR_DECLARED: "Not independently validated",
	INDEPENDENTLY_VALIDATED: "AI mentoring independently validated",
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

function signalLabel(signal: string, signals: readonly PracticeSignalOption[]) {
	return signals.find((option) => option.signal === signal)?.displayName ?? signal;
}

interface OccasionSummaryProps {
	binding: PracticeBinding;
	index: number;
	sources: readonly PracticeEvidenceSourceOption[];
	signals: readonly PracticeSignalOption[];
}

/**
 * Listed per occasion rather than merged into one set: a merged list would say the practice reads the
 * review threads whole without saying that only the review at the merge does.
 */
function OccasionSummary({ binding, index, sources, signals }: OccasionSummaryProps) {
	const required = binding.needs.filter((need) => need.stance !== "CONTEXTUAL");
	const contextual = binding.needs.filter((need) => need.stance === "CONTEXTUAL");
	return (
		<div className="rounded-md border p-3">
			<p className="font-medium">
				Occasion {index + 1}:{" "}
				{binding.signals.map((signal) => signalLabel(signal, signals)).join(", ")}
				{binding.onDrafts && (
					<span className="font-normal text-muted-foreground"> · also while a draft</span>
				)}
			</p>
			<dl className="mt-2 grid gap-x-4 gap-y-1 sm:grid-cols-[8rem_1fr]">
				<dt className="text-muted-foreground">Must have</dt>
				<dd>
					{required.length > 0 ? (
						<ul>
							{required.map((need) => (
								<li key={need.sourceKind}>
									{evidenceSourceLabel(need.sourceKind, sources)}
									<span className="text-muted-foreground">
										{" · "}
										{need.stance === "EXHAUSTIVE"
											? "captured whole, so it can say what is missing"
											: evidenceQualityLabel(
													sources.find((source) => source.sourceKind === need.sourceKind)
														?.requiredQuality,
												)}
									</span>
								</li>
							))}
						</ul>
					) : (
						<span className="text-muted-foreground">Nothing</span>
					)}
				</dd>
				<dt className="text-muted-foreground">May also use</dt>
				<dd>
					{contextual.length > 0 ? (
						contextual.map((need) => evidenceSourceLabel(need.sourceKind, sources)).join(", ")
					) : (
						<span className="text-muted-foreground">Nothing</span>
					)}
				</dd>
			</dl>
		</div>
	);
}

export interface PracticeEvidenceSummaryProps {
	policy: PracticeAutomatedReviewPolicy;
	bindings: readonly PracticeBinding[];
	validation: PracticeAutomatedReviewValidation;
	sources: readonly PracticeEvidenceSourceOption[];
	signals?: readonly PracticeSignalOption[];
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
	bindings,
	validation,
	sources,
	signals = [],
	workTypeLabel,
	className,
}: PracticeEvidenceSummaryProps) {
	return (
		<dl className={cn("grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2", className)}>
			<div>
				<dt className="font-medium">Mentoring support</dt>
				<dd className="text-muted-foreground">
					{mentoringSupportLabel(policy.automatedReview)}
					<span className="mt-1 block text-xs">
						Developers, peers, and mentors may use context that Hephaestus cannot access.
					</span>
				</dd>
			</div>
			<div>
				<dt className="font-medium">When evidence requirements are not met</dt>
				<dd className="text-muted-foreground">Skip this practice rather than guess</dd>
			</div>
			<div className="sm:col-span-2">
				<dt className="font-medium">When it is reviewed, and what it reads</dt>
				<dd className="mt-1 space-y-2">
					{bindings.length > 0 ? (
						bindings.map((binding, index) => (
							<OccasionSummary
								key={binding.signals.join(",") || index}
								binding={binding}
								index={index}
								sources={sources}
								signals={signals}
							/>
						))
					) : (
						<span className="text-muted-foreground">No occasion starts a review</span>
					)}
				</dd>
			</div>
			<div>
				<dt className="font-medium">AI mentoring validation</dt>
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
			<div className="sm:col-span-2">
				<dt className="sr-only">Technical evidence contract</dt>
				<dd>
					<details className="text-muted-foreground">
						<summary className="cursor-pointer">Technical evidence contract</summary>
						<p className="mt-1">
							Source contract {policy.sourceContractVersion} · {workTypeLabel}
						</p>
					</details>
				</dd>
			</div>
		</dl>
	);
}
