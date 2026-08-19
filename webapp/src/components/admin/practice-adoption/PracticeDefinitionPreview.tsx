import type { CuratedPracticeDefinition, PracticeDefinitionOptions } from "@/api/types.gen";
import { PracticeEvidenceSummary } from "@/components/admin/practice-catalog/PracticeEvidenceSummary";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
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
		<div className="space-y-6">
			<Card>
				<CardHeader>
					<CardTitle>
						<h2>Practice definition</h2>
					</CardTitle>
				</CardHeader>
				<CardContent className="space-y-5">
					<div>
						<h3 className="font-medium">Review criteria</h3>
						<p className="mt-1 whitespace-pre-wrap text-muted-foreground">{definition.criteria}</p>
					</div>
					{definition.whyItMatters && (
						<div>
							<h3 className="font-medium">Why it matters</h3>
							<p className="mt-1 whitespace-pre-wrap text-muted-foreground">
								{definition.whyItMatters}
							</p>
						</div>
					)}
					{definition.whatGoodLooksLike && (
						<div>
							<h3 className="font-medium">What good looks like</h3>
							<p className="mt-1 whitespace-pre-wrap text-muted-foreground">
								{definition.whatGoodLooksLike}
							</p>
						</div>
					)}
					{definition.precomputeScript && (
						<details>
							<summary className="cursor-pointer font-medium">Static analysis script</summary>
							<pre className="mt-2 max-h-80 overflow-auto rounded-md bg-muted p-3 text-xs">
								<code>{definition.precomputeScript}</code>
							</pre>
						</details>
					)}
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>
						<h2>Review evidence and validation</h2>
					</CardTitle>
				</CardHeader>
				<CardContent>
					<PracticeEvidenceSummary
						policy={definition.automatedReviewPolicy}
						bindings={definition.bindings}
						validation={definition.automatedReviewValidation}
						sources={workType?.allowedSources ?? []}
						signals={workType?.signals ?? []}
						workTypeLabel={artifactKindLabel(definition.artifactKind)}
					/>
				</CardContent>
			</Card>
		</div>
	);
}
