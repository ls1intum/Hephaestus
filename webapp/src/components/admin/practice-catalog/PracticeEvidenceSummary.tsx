import type {
	PracticeAutomatedReviewPolicy,
	PracticeAutomatedReviewValidation,
	PracticeBinding,
	PracticeEvidenceSourceOption,
	PracticeSignalOption,
} from "@/api/types.gen";
import { momentDef } from "@/components/admin/practice-catalog/occasion-moments";
import { RelativeTime } from "@/components/common/RelativeTime";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { evidenceSourceLabel, mentoringSupportLabel } from "./evidence-presentation";

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
	sources: readonly PracticeEvidenceSourceOption[];
	signals: readonly PracticeSignalOption[];
}

/**
 * Listed per occasion rather than merged into one set: a merged list would say the practice reads the
 * review threads whole without saying that only the review at the merge does.
 *
 * <p>The moments wear the same glyphs the editor's lifecycle strip draws, so an operator who set this
 * up recognises it here without translating.
 */
function OccasionSummary({ binding, sources, signals }: OccasionSummaryProps) {
	const required = binding.needs.filter((need) => need.stance !== "CONTEXTUAL");
	const contextual = binding.needs.filter((need) => need.stance === "CONTEXTUAL");
	return (
		<div className="space-y-2 rounded-md border p-3">
			<div className="flex flex-wrap items-center gap-1.5">
				{binding.signals.map((signal) => {
					const Icon = momentDef(signal).icon;
					return (
						<Badge key={signal} variant="secondary">
							<Icon className="size-3" aria-hidden />
							{signalLabel(signal, signals)}
						</Badge>
					);
				})}
				{binding.onDrafts && <Badge variant="outline">Drafts included</Badge>}
			</div>
			<dl className="grid gap-x-4 gap-y-1.5 sm:grid-cols-[6.5rem_1fr]">
				<dt className="text-muted-foreground">Must have</dt>
				<dd className="flex flex-wrap gap-1.5">
					{required.length > 0 ? (
						required.map((need) => (
							<Badge key={need.sourceKind} variant="secondary">
								{evidenceSourceLabel(need.sourceKind, sources)}
								{need.stance === "EXHAUSTIVE" && (
									<span className="text-muted-foreground">· whole</span>
								)}
							</Badge>
						))
					) : (
						<span className="text-muted-foreground">Nothing</span>
					)}
				</dd>
				<dt className="text-muted-foreground">May also use</dt>
				<dd className="flex flex-wrap gap-1.5">
					{contextual.length > 0 ? (
						contextual.map((need) => (
							<Badge key={need.sourceKind} variant="outline">
								{evidenceSourceLabel(need.sourceKind, sources)}
							</Badge>
						))
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
				<p className="text-muted-foreground">
					Validated for source contract {validation.sourceContractVersion}{" "}
					<RelativeTime value={validation.validatedAt} fallback="at an unknown time" />
				</p>
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
						Developers, peers, and mentors may use context no automated review can reach.
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
				<dt className="sr-only">Technical details</dt>
				<dd>
					{/* One disclosure, not two. The contract version and the digests are the same kind of
					    answer — provenance for somebody reproducing a decision — and splitting them left two
					    identically named triangles a paragraph apart. */}
					<details className="text-muted-foreground">
						<summary className="cursor-pointer">Technical details</summary>
						<p className="mt-1">
							Source contract {policy.sourceContractVersion} · {workTypeLabel}
							<br />
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
				</dd>
			</div>
		</dl>
	);
}
