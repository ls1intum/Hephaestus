import { CircleCheck, Lightbulb } from "lucide-react";
import type { CuratedPracticeDefinition, PracticeDefinitionOptions } from "@/api/types.gen";
import { PracticeEvidenceSummary } from "@/components/admin/practice-catalog/PracticeEvidenceSummary";
import { Section } from "@/components/core/Section";
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
			<Section size="sm" title="What this practice checks">
				<p className="whitespace-pre-wrap text-xl font-semibold tracking-tight">
					{definition.criteria}
				</p>
			</Section>

			{(definition.whyItMatters || definition.whatGoodLooksLike) && (
				<div className="grid gap-4 sm:grid-cols-2">
					{definition.whyItMatters && (
						<Section
							size="sm"
							className="rounded-xl bg-muted/50 p-4"
							title={
								<>
									<Lightbulb
										className="mb-3 block size-5 text-muted-foreground"
										aria-hidden="true"
									/>
									Why it matters
								</>
							}
						>
							<p className="whitespace-pre-wrap text-sm text-muted-foreground">
								{definition.whyItMatters}
							</p>
						</Section>
					)}
					{definition.whatGoodLooksLike && (
						<Section
							size="sm"
							className="rounded-xl bg-muted/50 p-4"
							title={
								<>
									<CircleCheck
										className="mb-3 block size-5 text-muted-foreground"
										aria-hidden="true"
									/>
									What good looks like
								</>
							}
						>
							<p className="whitespace-pre-wrap text-sm text-muted-foreground">
								{definition.whatGoodLooksLike}
							</p>
						</Section>
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
