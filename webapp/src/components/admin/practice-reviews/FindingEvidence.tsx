import type { EvidenceCitation, ObservationEvidence } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";

const citationSideLabels = {
	OLD: "old side",
	NEW: "new side",
} satisfies Record<NonNullable<EvidenceCitation["side"]>, string>;

export interface FindingEvidenceProps {
	evidence: ObservationEvidence | null | undefined;
}

export function FindingEvidence({ evidence }: FindingEvidenceProps) {
	if (!evidence) {
		return <p className="text-sm text-muted-foreground">No evidence was recorded.</p>;
	}
	const sourceKinds = [...new Set(evidence.citations.map((citation) => citation.sourceKind))];

	return (
		<div className="space-y-4">
			<div>
				<h4 className="text-sm font-medium">Sources</h4>
				<div className="mt-2 flex flex-wrap gap-2">
					{sourceKinds.map((sourceKind) => (
						<Badge key={sourceKind} variant="outline" className="font-mono">
							{sourceKind}
						</Badge>
					))}
				</div>
			</div>
			<div>
				<h4 className="text-sm font-medium">Citations</h4>
				<ul className="mt-2 space-y-3">
					{evidence.citations.map((citation, index) => (
						<li key={`${citationKey(citation)}:${index}`} className="rounded-md border p-3">
							<p className="font-mono text-xs break-words">{citationLabel(citation)}</p>
							<p className="mt-1 text-xs text-muted-foreground break-words">
								{citation.sourceKind} · {citation.artifactPath}
							</p>
							{citation.quoteRedacted ? (
								<p className="mt-3 text-sm text-muted-foreground">Quote redacted.</p>
							) : (
								<pre className="mt-3 overflow-auto rounded-md bg-muted p-3 text-xs whitespace-pre-wrap">
									{citation.quote}
								</pre>
							)}
						</li>
					))}
				</ul>
			</div>
		</div>
	);
}

function citationKey(citation: EvidenceCitation): string {
	return [
		citation.sourceKind,
		citation.artifactPath,
		citation.path,
		citation.side,
		citation.startLine,
		citation.endLine,
	].join(":");
}

function citationLabel(citation: EvidenceCitation): string {
	const lineRange =
		citation.startLine === citation.endLine
			? `${citation.startLine}`
			: `${citation.startLine}–${citation.endLine}`;
	return `${citation.path}:${lineRange}${citation.side ? ` (${citationSideLabels[citation.side]})` : ""}`;
}
