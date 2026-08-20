import { CircleCheck, Lightbulb } from "lucide-react";
import type { CuratedPracticeDefinition, PracticeDefinitionOptions } from "@/api/types.gen";
import { PracticeEvidenceSummary } from "@/components/admin/practice-catalog/PracticeEvidenceSummary";
import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
import { Separator } from "@/components/ui/separator";
import { artifactKindLabel } from "@/lib/artifact-kinds";

export interface PracticeDefinitionPreviewProps {
	definition: CuratedPracticeDefinition;
	options: PracticeDefinitionOptions;
}

export function PracticeDefinitionPreview({ definition, options }: PracticeDefinitionPreviewProps) {
	const workType = options.workTypes?.find(
		(candidate) => candidate.artifactKind === definition.artifactKind,
	);

	return (
		<div className="space-y-8">
			<section aria-labelledby="practice-checks-heading" className="space-y-3">
				<h2 id="practice-checks-heading" className="text-sm font-medium">
					What this practice checks
				</h2>
				<p className="whitespace-pre-wrap text-xl font-semibold tracking-tight">
					{definition.criteria}
				</p>
			</section>

			{(definition.whyItMatters || definition.whatGoodLooksLike) && (
				<div className="grid gap-4 sm:grid-cols-2">
					{definition.whyItMatters && (
						<section className="rounded-xl bg-muted/50 p-4">
							<Lightbulb className="mb-3 size-5 text-muted-foreground" aria-hidden="true" />
							<h2 className="font-medium">Why it matters</h2>
							<p className="mt-1 whitespace-pre-wrap text-sm text-muted-foreground">
								{definition.whyItMatters}
							</p>
						</section>
					)}
					{definition.whatGoodLooksLike && (
						<section className="rounded-xl bg-muted/50 p-4">
							<CircleCheck className="mb-3 size-5 text-muted-foreground" aria-hidden="true" />
							<h2 className="font-medium">What good looks like</h2>
							<p className="mt-1 whitespace-pre-wrap text-sm text-muted-foreground">
								{definition.whatGoodLooksLike}
							</p>
						</section>
					)}
				</div>
			)}

			<Separator />

			<Accordion aria-label="Practice review details">
				<AccordionItem value="review-mechanics">
					<AccordionTrigger>How reviews work</AccordionTrigger>
					<AccordionContent className="pt-2">
						<PracticeEvidenceSummary
							policy={definition.automatedReviewPolicy}
							bindings={definition.bindings}
							validation={definition.automatedReviewValidation}
							sources={workType?.allowedSources ?? []}
							signals={workType?.signals ?? []}
							workTypeLabel={artifactKindLabel(definition.artifactKind)}
							showValidation={false}
						/>
					</AccordionContent>
				</AccordionItem>
				{definition.precomputeScript && (
					<AccordionItem value="static-analysis">
						<AccordionTrigger>Advanced: static analysis</AccordionTrigger>
						<AccordionContent>
							<pre className="max-h-80 overflow-auto rounded-md bg-muted p-3 text-xs">
								<code>{definition.precomputeScript}</code>
							</pre>
						</AccordionContent>
					</AccordionItem>
				)}
			</Accordion>
		</div>
	);
}
