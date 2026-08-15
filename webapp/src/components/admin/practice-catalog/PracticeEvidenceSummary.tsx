import type { ComponentProps } from "react";
import type {
	PracticeAutomatedReviewPolicy,
	PracticeAutomatedReviewValidation,
	PracticeBinding,
	PracticeEvidenceSourceOption,
	PracticeSignalOption,
} from "@/api/types.gen";
import { momentDef } from "@/components/admin/practice-catalog/occasion-moments";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { evidenceSourceLabel, mentoringSupportLabel } from "./evidence-presentation";

/** Total over the wire union, so a status the API learns to send cannot arrive unlabelled. */
const VALIDATION_DEFS: Record<
	PracticeAutomatedReviewValidation["status"],
	{ label: string; variant: ComponentProps<typeof Badge>["variant"] }
> = {
	AUTHOR_DECLARED: { label: "Not independently validated", variant: "outline" },
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
	const def = VALIDATION_DEFS[validation.status];
	return (
		<div className="space-y-1 text-sm">
			<Badge variant={def.variant}>{def.label}</Badge>
			{/* The digests answer one question — which exact rules produced this verdict — so they
			    belong beside the verdict, not in a disclosure of their own. Small and monospaced:
			    nobody reads a hash, they compare one. */}
			<p className="text-muted-foreground text-xs">
				Rules <code className="break-all">{validation.reviewRuleFingerprint}</code> · policy{" "}
				<code className="break-all">{validation.policyDigest}</code>
			</p>
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
			{/* The work it applies to, and the contract version its evidence is written against, are
			    facts an operator comparing two versions of a practice reads first. They spent this
			    branch inside a "Technical details" triangle next to three digests, which is where a
			    fact goes to be missed. */}
			<div>
				<dt className="font-medium">Work it reviews</dt>
				<dd className="text-muted-foreground">
					{workTypeLabel}
					<span className="mt-1 block text-xs">
						Evidence written against source contract {policy.sourceContractVersion}
					</span>
				</dd>
			</div>
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
		</dl>
	);
}
