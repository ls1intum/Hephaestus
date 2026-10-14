import { ShieldAlertIcon } from "lucide-react";

import type { EvidenceCitation } from "@/api/types.gen";
import {
	codeCitationLocator,
	DIFF_SIDE_LABELS,
	evidenceSourceDef,
	type EvidenceSourceGroup,
	groupCitationsBySource,
} from "@/components/practice-vocabulary/evidence-source-defs";
import { Badge } from "@/components/ui/badge";
import { hasText } from "@/lib/text";

export interface ResultEvidenceProps {
	citations: EvidenceCitation[];
}

// Non-code coordinates refer to serialized input, so they cannot identify a location in the source app.
export function ResultEvidence({ citations }: ResultEvidenceProps) {
	if (citations.length === 0) {
		return (
			<p className="text-sm text-muted-foreground">
				Nothing was quoted, so there is no passage to check this against.
			</p>
		);
	}
	const groups = groupCitationsBySource(citations);

	return (
		<div className="space-y-6">
			<p className="text-sm text-muted-foreground">
				{summaryLine(citations.length, groups.length)}
			</p>
			{groups.map((group) => (
				<EvidenceSourceSection key={group.sourceKind} group={group} />
			))}
		</div>
	);
}

function summaryLine(citations: number, sources: number): string {
	const passages = citations === 1 ? "One passage" : `${citations} passages`;
	if (sources === 1) {
		return `${passages} from one source.`;
	}
	return `${passages} from ${sources} sources.`;
}

function EvidenceSourceSection({ group }: { group: EvidenceSourceGroup }) {
	const { def, citations } = group;
	const Icon = def.icon;
	return (
		<section className="space-y-3">
			<div className="flex min-w-0 items-start gap-3">
				<span className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-md border bg-muted">
					<Icon className="size-4 text-muted-foreground" aria-hidden />
				</span>
				<div className="min-w-0 flex-1">
					<h4 className="text-sm font-medium break-words">{def.label}</h4>
					<p className="text-xs break-words text-muted-foreground">{def.description}</p>
				</div>
				<span className="shrink-0 text-xs text-muted-foreground">
					{citations.length === 1 ? "1 passage" : `${citations.length} passages`}
				</span>
			</div>
			<ul className="space-y-3 sm:pl-11">
				{citations.map((citation, index) => (
					<li
						key={`${citationKey(citation)}:${index}`}
						className="overflow-hidden rounded-lg border"
					>
						<CitationHeader citation={citation} locator={def.locator} />
						{citation.quote === null ? (
							<RedactedQuote />
						) : (
							<pre className="overflow-x-auto p-3 text-xs break-words whitespace-pre-wrap">
								{citation.quote}
							</pre>
						)}
					</li>
				))}
			</ul>
		</section>
	);
}

type CitationPlace = Pick<
	EvidenceCitation,
	"sourceKind" | "path" | "startLine" | "endLine" | "side"
>;

export function citationLocation(citation: CitationPlace): string | undefined {
	const { path, startLine, endLine } = citation;
	if (path === null) {
		return undefined;
	}
	return evidenceSourceDef(citation.sourceKind).locator === "code" && startLine !== null
		? codeCitationLocator({ path, startLine, endLine: endLine ?? undefined })
		: path;
}

export function citationSide(citation: CitationPlace): string | undefined {
	return evidenceSourceDef(citation.sourceKind).locator === "code" && citation.side !== null
		? DIFF_SIDE_LABELS[citation.side]
		: undefined;
}

function CitationHeader({
	citation,
	locator,
}: {
	citation: EvidenceCitation;
	locator: EvidenceSourceGroup["def"]["locator"];
}) {
	const side = citationSide(citation);
	const location = citationLocation(citation);
	return (
		<div className="flex flex-wrap items-center gap-2 border-b bg-muted/50 px-3 py-2">
			{locator === "code" && location !== undefined ? (
				<code className="min-w-0 text-xs break-all">{location}</code>
			) : (
				<p className="min-w-0 text-xs break-words text-muted-foreground">
					{location ?? "Location not recorded"}
				</p>
			)}
			{side !== undefined && (
				<Badge variant="outline" className="shrink-0">
					{side}
				</Badge>
			)}
			{hasText(citation.commit) && (
				<p className="min-w-0 basis-full text-xs break-all text-muted-foreground">
					Commit <code>{citation.commit}</code>
				</p>
			)}
		</div>
	);
}

function RedactedQuote() {
	return (
		<p className="flex items-start gap-2 p-3 text-sm text-muted-foreground">
			<ShieldAlertIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
			Not quoted. The passage was withheld, so only its location was kept.
		</p>
	);
}

function citationKey(citation: EvidenceCitation): string {
	return [
		citation.sourceKind,
		citation.sourcePath,
		citation.path,
		citation.commit,
		citation.side,
		citation.startLine,
		citation.endLine,
	].join(":");
}
