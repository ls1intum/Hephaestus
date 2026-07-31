export interface FindingEvidenceProps {
	evidence: unknown;
}

interface EvidenceLocation {
	path?: string;
	startLine?: number;
	endLine?: number;
}

export function FindingEvidence({ evidence }: FindingEvidenceProps) {
	if (typeof evidence === "string" && evidence.trim()) {
		return <p className="whitespace-pre-wrap text-sm">{evidence}</p>;
	}
	if (!isRecord(evidence) || Object.keys(evidence).length === 0) {
		return <p className="text-sm text-muted-foreground">No structured evidence was recorded.</p>;
	}

	const locations = asLocations(evidence.locations);
	const snippets = asStrings(evidence.snippets);
	const references = asStrings(evidence.references);
	if (locations.length + snippets.length + references.length === 0) {
		return (
			<p className="text-sm text-muted-foreground">Evidence is available in Technical details.</p>
		);
	}

	return (
		<div className="space-y-4">
			{locations.length > 0 && (
				<div>
					<h4 className="text-sm font-medium">Locations</h4>
					<ul className="mt-1 space-y-1">
						{locations.map((location, index) => (
							<li
								key={`${location.path ?? "location"}-${index}`}
								className="font-mono text-xs break-words"
							>
								{locationLabel(location)}
							</li>
						))}
					</ul>
				</div>
			)}
			{snippets.length > 0 && (
				<div className="space-y-2">
					<h4 className="text-sm font-medium">Excerpts</h4>
					{snippets.map((snippet, index) => (
						<pre
							key={`${snippet.slice(0, 24)}-${index}`}
							className="overflow-auto rounded-md bg-muted p-3 text-xs whitespace-pre-wrap"
						>
							{snippet}
						</pre>
					))}
				</div>
			)}
			{references.length > 0 && (
				<div>
					<h4 className="text-sm font-medium">References</h4>
					<ul className="mt-1 space-y-1 text-sm">
						{references.map((reference) => (
							<li key={reference} className="break-words">
								{reference}
							</li>
						))}
					</ul>
				</div>
			)}
		</div>
	);
}

function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null && !Array.isArray(value);
}

function asLocations(value: unknown): EvidenceLocation[] {
	if (!Array.isArray(value)) return [];
	return value.filter(
		(entry): entry is EvidenceLocation =>
			isRecord(entry) &&
			(typeof entry.path === "string" ||
				typeof entry.startLine === "number" ||
				typeof entry.endLine === "number"),
	);
}

function asStrings(value: unknown): string[] {
	return Array.isArray(value)
		? value.filter((entry): entry is string => typeof entry === "string" && entry.length > 0)
		: [];
}

function locationLabel(location: EvidenceLocation): string {
	const path = location.path ?? "Unknown file";
	if (location.startLine == null) return path;
	if (location.endLine == null || location.endLine === location.startLine) {
		return `${path}:${location.startLine}`;
	}
	return `${path}:${location.startLine}–${location.endLine}`;
}
