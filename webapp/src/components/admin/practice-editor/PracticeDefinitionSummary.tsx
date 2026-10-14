import { cn } from "cn";
import type {
	AppliesWhenClause,
	PracticeDefinition,
	PracticeItemTypeOption,
} from "@/api/types.gen";
import { aboutLabel } from "@/components/practice-vocabulary/about-role-labels";
import { Badge } from "@/components/ui/badge";
import { itemKindLabel } from "@/lib/item-kinds";

import { lineNotesSentence, reviewedBySentence, scriptLabel } from "./definition-text";
import { readSourceLabel } from "./reads-presentation";
import { eventDef, eventLabel } from "./review-events";

export interface PracticeDefinitionSummaryProps {
	kind: string;
	definition: PracticeDefinition;
	/**
	 * The options for the practice's kind, where the instance still offers it. Without them, events
	 * and sources are named from this build's vocabulary.
	 */
	itemType?: PracticeItemTypeOption;
	className?: string;
}

function appliesWhenClause(clause: AppliesWhenClause) {
	if (clause.changedPath !== undefined) {
		return `A changed path matches ${clause.changedPath.join(", ")}`;
	}
	if (clause.diffContains !== undefined) {
		return `The change contains ${clause.diffContains.join(", ")}`;
	}
	return `The work has ${clause.hasItems ?? ""}`;
}

export function PracticeDefinitionSummary({
	kind,
	definition,
	itemType,
	className,
}: PracticeDefinitionSummaryProps) {
	const events = itemType?.events ?? [];
	const readSources = itemType?.readSources ?? [];
	const dimensions = itemType?.reviewOnStateDimensions ?? [];
	const required = definition.reads.sources.filter((entry) => entry.need !== "OPTIONAL");
	const optional = definition.reads.sources.filter((entry) => entry.need === "OPTIONAL");
	const reviewedBy = reviewedBySentence(definition.reviewedBy);
	const automated = definition.reviewedBy.by === "HEPHAESTUS";
	const { scan, appliesWhen } = definition;

	return (
		<dl className={cn("grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2", className)}>
			<div>
				<dt className="font-medium">Reviews</dt>
				<dd className="text-muted-foreground">{itemType?.label ?? itemKindLabel(kind)}</dd>
			</div>
			<div>
				<dt className="font-medium">About</dt>
				<dd className="text-muted-foreground">{aboutLabel(definition.about)}</dd>
			</div>
			<div className="sm:col-span-2">
				<dt className="font-medium">Review on</dt>
				<dd className="mt-1 flex flex-wrap gap-1.5">
					{definition.reviewOn.events.length > 0 ? (
						definition.reviewOn.events.map((name) => {
							const Icon = eventDef(name).icon;
							return (
								<Badge key={name} variant="secondary">
									<Icon className="size-3" aria-hidden />
									{eventLabel(name, events)}
								</Badge>
							);
						})
					) : (
						<span className="text-muted-foreground">Only when someone requests a review</span>
					)}
				</dd>
			</div>
			{Object.keys(definition.reviewOn.state).length > 0 && (
				<div className="sm:col-span-2">
					<dt className="font-medium">When</dt>
					<dd className="mt-1 flex flex-wrap gap-1.5">
						{Object.entries(definition.reviewOn.state).map(([name, values]) => {
							const dimension = dimensions.find((option) => option.name === name);
							return (
								<Badge key={name} variant="outline">
									{dimension?.label ?? name}:{" "}
									{values
										.map(
											(value) =>
												dimension?.values.find((option) => option.value === value)?.label ?? value,
										)
										.join(", ")}
								</Badge>
							);
						})}
					</dd>
				</div>
			)}
			<div className="sm:col-span-2">
				<dt className="font-medium">Applies when</dt>
				<dd className="text-muted-foreground">
					{appliesWhen === null ? (
						"Every item of this kind"
					) : (
						<>
							<ul className="list-inside list-disc">
								{appliesWhen.anyOf.map((clause, index) => (
									<li key={index} className="break-words">
										{appliesWhenClause(clause)}
									</li>
								))}
							</ul>
							<p>Otherwise: {appliesWhen.otherwise}</p>
						</>
					)}
				</dd>
			</div>
			<div className="sm:col-span-2">
				<dt className="font-medium">Reviewed by</dt>
				<dd className="text-muted-foreground">
					{reviewedBy.label}
					{reviewedBy.detail !== null && <span className="mt-1 block">{reviewedBy.detail}</span>}
				</dd>
			</div>
			{automated && (
				<>
					<div>
						<dt className="font-medium">Must have</dt>
						<dd className="mt-1 flex flex-wrap gap-1.5">
							{required.map((entry) => (
								<Badge key={entry.source} variant="secondary">
									{readSourceLabel(entry.source, readSources)}
									{/* "captured whole", never "nothing in the world is missing": completeness is a
									    property of one capture of one source. */}
									{entry.need === "COMPLETE" && (
										<span className="text-muted-foreground">· captured whole</span>
									)}
								</Badge>
							))}
						</dd>
					</div>
					<div>
						<dt className="font-medium">May also use</dt>
						<dd className="mt-1 flex flex-wrap gap-1.5">
							{optional.length > 0 ? (
								optional.map((entry) => (
									<Badge key={entry.source} variant="outline">
										{readSourceLabel(entry.source, readSources)}
									</Badge>
								))
							) : (
								<span className="text-muted-foreground">Nothing</span>
							)}
						</dd>
					</div>
					<div>
						<dt className="font-medium">Script</dt>
						<dd className="text-muted-foreground">{scriptLabel(definition.script)}</dd>
					</div>
					<div>
						<dt className="font-medium">Scans</dt>
						<dd className="text-muted-foreground">
							{scan === null
								? "Not included"
								: `Included: ${itemKindLabel(scan.itemKind, 2).toLowerCase()} matching ${scan.include.join(", ")}`}
						</dd>
					</div>
					{definition.reviewedBy.limits.length > 0 && (
						<div className="sm:col-span-2">
							<dt className="font-medium">What the review cannot support</dt>
							<dd>
								<ul className="space-y-1">
									{definition.reviewedBy.limits.map((limit) => (
										<li key={limit.code}>{limit.description}</li>
									))}
								</ul>
							</dd>
						</div>
					)}
				</>
			)}
			<div className="sm:col-span-2">
				<dt className="font-medium">Line notes</dt>
				<dd className="text-muted-foreground">{lineNotesSentence(definition.lineNotes)}</dd>
			</div>
		</dl>
	);
}
