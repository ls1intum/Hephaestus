import { ChevronDownIcon } from "lucide-react";
import type {
	DeliveryPolicyFactsSnapshot,
	DeliveryPolicyTrace as DeliveryPolicyTraceData,
} from "@/api/types.gen";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
	DELIVERY_AUTONOMY_LABELS,
	DELIVERY_CHECK_LABELS,
	DELIVERY_CHECK_STATUS_DEFS,
	DELIVERY_PERSON_MODE_LABELS,
	DELIVERY_REASON_SENTENCES,
	DELIVERY_REPOSITORY_MODE_LABELS,
	DELIVERY_STAGE_LABELS,
	DELIVERY_STATUS_LABELS,
	DELIVERY_SUBJECT_LABELS,
	DELIVERY_SURFACE_LABELS,
	DELIVERY_TRIGGER_LABELS,
} from "./delivery-policy-vocabulary";

export interface DeliveryPolicyTraceProps {
	evaluations: DeliveryPolicyTraceData[];
}

export function DeliveryPolicyTrace({ evaluations }: DeliveryPolicyTraceProps) {
	if (evaluations.length === 0) return null;

	return (
		<Collapsible className="rounded-lg border bg-card p-4 text-sm">
			<CollapsibleTrigger
				className="group/trace"
				render={
					<Button variant="ghost" size="sm" className="-ml-2">
						Technical delivery policy trace
						<ChevronDownIcon
							className="transition-transform group-aria-expanded/trace:rotate-180"
							aria-hidden
						/>
					</Button>
				}
			/>
			<CollapsibleContent>
				<p className="mt-2 text-xs text-muted-foreground">
					Checks run in order. After a denial, later checks are not reached.
				</p>
				<ol className="mt-4 space-y-4">
					{evaluations.map((evaluation, index) => (
						<li
							key={`${evaluation.reviewId}-${evaluation.evaluatedAt.toISOString()}-${index}`}
							className="space-y-2 rounded-md border p-3"
						>
							<p className="font-medium">
								{DELIVERY_SURFACE_LABELS[evaluation.surface]} ·{" "}
								{DELIVERY_STAGE_LABELS[evaluation.stage]} · admitted revision{" "}
								{evaluation.admittedRevision}
								{evaluation.evaluatedRevision == null
									? ""
									: ` · evaluated revision ${evaluation.evaluatedRevision}`}
							</p>
							<p className="text-xs text-muted-foreground">
								Resolver {evaluation.resolverVersion} · Evaluated{" "}
								<time dateTime={evaluation.evaluatedAt.toISOString()}>
									{evaluation.evaluatedAt.toLocaleString()}
								</time>
							</p>
							{evaluation.decisiveReason && (
								<p className="text-xs">
									Stopped here. {DELIVERY_REASON_SENTENCES[evaluation.decisiveReason]}
								</p>
							)}
							<p className="text-xs text-muted-foreground">{scopeSentence(evaluation.facts)}</p>
							<PolicyFacts facts={evaluation.facts} />
							<ul className="space-y-1">
								{evaluation.checks.map((check) => (
									<li key={check.check} className="flex items-center justify-between gap-3 text-xs">
										<span className="min-w-0">{DELIVERY_CHECK_LABELS[check.check]}</span>
										<StatusBadge def={DELIVERY_CHECK_STATUS_DEFS[check.status]} />
									</li>
								))}
							</ul>
						</li>
					))}
				</ol>
			</CollapsibleContent>
		</Collapsible>
	);
}

function PolicyFacts({ facts }: { facts: DeliveryPolicyFactsSnapshot }) {
	const applicable = (value: boolean | undefined) =>
		value == null ? "not applicable" : value ? "yes" : "no";
	const practices = facts.contributingPractices?.map((practice) => {
		const autonomy = practice.autonomy
			? DELIVERY_AUTONOMY_LABELS[practice.autonomy]
			: "not recorded";
		return `${practice.slug ?? "unnamed practice"}: ${autonomy}`;
	});
	const rows = [
		[
			"Workspace delivery",
			facts.deliveryStatus ? DELIVERY_STATUS_LABELS[facts.deliveryStatus] : "not recorded",
		],
		["Started", facts.triggerMode ? DELIVERY_TRIGGER_LABELS[facts.triggerMode] : "not recorded"],
		["Recipient consent", applicable(facts.recipientConsent)],
		["Repository matched", applicable(facts.repositoryMatched)],
		["Branch matched", applicable(facts.branchMatched)],
		["Person matched", applicable(facts.personMatched)],
		[
			"Contributing practices",
			practices == null ? "not recorded" : practices.length === 0 ? "none" : practices.join(", "),
		],
	] as const;

	return (
		<dl className="grid grid-cols-[max-content_1fr] gap-x-3 gap-y-1 text-xs text-muted-foreground">
			{rows.map(([label, value]) => (
				<div key={label} className="contents">
					<dt className="font-medium text-foreground">{label}</dt>
					<dd>{value}</dd>
				</div>
			))}
		</dl>
	);
}

function scopeSentence(facts: DeliveryPolicyFactsSnapshot): string {
	const NOT_APPLICABLE = "not applicable";
	const where = facts.baseBranch
		? `${facts.repository ?? "no repository"} / ${facts.baseBranch}`
		: (facts.repository ?? "no repository");
	const repositories =
		facts.repositoryMode == null
			? NOT_APPLICABLE
			: DELIVERY_REPOSITORY_MODE_LABELS[facts.repositoryMode];
	const people =
		facts.personMode == null ? NOT_APPLICABLE : DELIVERY_PERSON_MODE_LABELS[facts.personMode];
	const subject = facts.subject == null ? NOT_APPLICABLE : DELIVERY_SUBJECT_LABELS[facts.subject];
	return `Scope: ${where} · Repositories: ${repositories} · People: ${people} · Subject: ${subject}`;
}
