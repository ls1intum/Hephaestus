import { OUTCOMES, type Outcome } from "./work/practice-scripts/lib/core/contract.ts";
import { z } from "./work/practice-scripts/lib/core/practice-script.ts";

const citationsSchema = z
	.array(z.looseObject({}))
	.transform((citations) => citations.map((citation, index) => Object.assign(citation, { index })));
/** Validate admission identity and outcome; retain server-owned details for composition. */
export const admittedResultSchema = z
	.looseObject({
		resultId: z.uuid(),
		practiceSlug: z.string().min(1),
		outcome: z.enum(OUTCOMES),
		evidence: z.looseObject({ citations: citationsSchema }),
		problems: z.array(z.looseObject({ id: z.string().min(1), citations: citationsSchema })),
	})
	.refine((result) => (result.outcome === "NOT_MET") === result.problems.length > 0, {
		message: "Only NOT_MET results have problems",
	})
	.transform(({ problems, evidence, ...result }) =>
		Object.assign(result, {
			evidence,
			citations: evidence.citations,
			problems: problems.map(({ id, ...problem }) => Object.assign(problem, { problemId: id })),
		}),
	);
export type AdmittedResult = z.infer<typeof admittedResultSchema>;
type AdmittedCitation = AdmittedResult["citations"][number];

interface LeanCitation {
	index: number;
	sourceKind: unknown;
	path: unknown;
	side: unknown;
	startLine: unknown;
	endLine: unknown;
	anchorable: unknown;
}

export interface LeanResult {
	resultId: string;
	practiceSlug: string;
	outcome: Outcome;
	citations: LeanCitation[];
	problems: {
		problemId: string;
		severity: unknown;
		anchorable: unknown;
		citations: LeanCitation[];
	}[];
}

function leanCitation(citation: AdmittedCitation): LeanCitation {
	return {
		index: citation.index,
		sourceKind: citation.sourceKind,
		path: citation.path,
		side: citation.side,
		startLine: citation.startLine,
		endLine: citation.endLine,
		anchorable: citation.anchorable,
	};
}

export function resultsById(results: readonly AdmittedResult[]): Map<string, AdmittedResult> {
	return new Map(results.map((result) => [result.resultId, result]));
}

export function leanResults(results: readonly AdmittedResult[]): LeanResult[] {
	return results.map((result) => ({
		resultId: result.resultId,
		practiceSlug: result.practiceSlug,
		outcome: result.outcome,
		citations: result.citations.map(leanCitation),
		problems: result.problems.map((problem) => ({
			problemId: problem.problemId,
			severity: problem.severity,
			anchorable: problem.anchorable,
			citations: problem.citations.map(leanCitation),
		})),
	}));
}
