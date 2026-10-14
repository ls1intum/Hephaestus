import { CheckCircle2Icon, CircleStopIcon } from "lucide-react";

import type { FeedbackCheck, FeedbackCheckFacts } from "@/api/types.gen";
import { ReviewDayTime } from "@/components/admin/practice-reviews/ReviewDayTime";
import { statusValues } from "@/components/common/status-def";
import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";

import {
	FEEDBACK_CHECK_FACT_LABELS,
	FEEDBACK_CHECK_REASON_SENTENCES,
	FEEDBACK_CHECK_STAGE_LABELS,
} from "./feedback-check-vocabulary";

export interface FeedbackChecksProps {
	checks: FeedbackCheck[];
}

export function FeedbackChecks({ checks }: FeedbackChecksProps) {
	if (checks.length === 0) {
		return null;
	}
	const stopped = checks.some((check) => !check.allowed);

	return (
		<section className="min-w-0 rounded-lg border bg-card p-4 text-sm">
			<h4 className="font-medium">{stopped ? "Why this was not sent" : "Feedback checks"}</h4>
			<p className="mt-1 text-xs text-muted-foreground">
				Checks run in order. After a check stops the feedback, later checks do not run.
			</p>
			<Accordion multiple className="mt-3">
				{checks.map((check, index) => {
					const value = `${check.stage}-${check.checkedAt.toISOString()}-${index}`;
					return (
						<AccordionItem key={value} value={value}>
							<AccordionTrigger className="gap-3 no-underline hover:no-underline">
								<span className="min-w-0 flex-1">
									<span className="block break-words">
										{FEEDBACK_CHECK_STAGE_LABELS[check.stage]}
									</span>
									<span className="block text-xs font-normal text-muted-foreground">
										<ReviewDayTime at={check.checkedAt} />
									</span>
								</span>
								<Badge variant={check.allowed ? "success" : "warning"}>
									{check.allowed ? (
										<CheckCircle2Icon aria-hidden />
									) : (
										<CircleStopIcon aria-hidden />
									)}
									{check.allowed ? "Allowed" : "Stopped"}
								</Badge>
							</AccordionTrigger>
							<AccordionContent className="min-w-0 space-y-2 pb-4">
								{check.reason !== null && (
									<p className="text-xs">{FEEDBACK_CHECK_REASON_SENTENCES[check.reason]}</p>
								)}
								<CheckFacts facts={check.facts} />
								<p className="min-w-0 text-xs break-all text-muted-foreground">
									{settingsLine(check.facts)}
								</p>
							</AccordionContent>
						</AccordionItem>
					);
				})}
			</Accordion>
		</section>
	);
}

const FACTS = statusValues(FEEDBACK_CHECK_FACT_LABELS);

function CheckFacts({ facts }: { facts: FeedbackCheckFacts }) {
	return (
		<dl className="grid min-w-0 grid-cols-[max-content_minmax(0,1fr)] gap-x-3 gap-y-1 text-xs text-muted-foreground">
			{FACTS.map((fact) => (
				<div key={fact} className="contents">
					<dt className="font-medium text-foreground">{FEEDBACK_CHECK_FACT_LABELS[fact]}</dt>
					<dd className="min-w-0 break-words">{facts[fact] ? "yes" : "no"}</dd>
				</div>
			))}
		</dl>
	);
}

function settingsLine(facts: FeedbackCheckFacts): string {
	return facts.admittedRolloutRevision === facts.evaluatedRolloutRevision
		? `Workspace settings ${facts.admittedRolloutRevision}`
		: `Admitted under workspace settings ${facts.admittedRolloutRevision}, checked against ${facts.evaluatedRolloutRevision}`;
}
