import { useId, useState } from "react";

import type { CatalogPracticeReleaseProposal, Choice, PracticeDefinition } from "@/api/types.gen";
import {
	PRACTICE_DEFINITION_FIELDS,
	type PracticeDefinitionField,
} from "@/components/admin/practices/practice-definition-fields";
import { PracticeDefinitionFieldValue } from "@/components/admin/practices/PracticeDefinitionFieldValue";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Spinner } from "@/components/ui/spinner";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";

type Source = Choice["source"];

export interface PracticeReleaseReviewProps {
	/** The instance catalog's proposal; a workspace proposal adds only the version it applies to. */
	proposal: CatalogPracticeReleaseProposal;
	pending: "accept" | "decline" | null;
	onAccept: (choices: Choice[]) => void;
	onDecline: () => void;
}

export function PracticeReleaseReview({
	proposal,
	pending,
	onAccept,
	onDecline,
}: PracticeReleaseReviewProps) {
	const id = useId();
	// Without the version this workspace adopted, a local edit is indistinguishable from a catalog
	// change, so nothing is chosen for the reader.
	const base: PracticeDefinition | null = proposal.base;
	const baseKnown = base !== null;
	const [choices, setChoices] = useState(
		() =>
			new Map<PracticeDefinitionField, Source>(
				proposal.fields
					.filter((field) => baseKnown && field.offeredChanged && !field.conflict)
					.map((field) => [field.field, "OFFERED"]),
			),
	);
	const ready = proposal.fields.every((field) => !field.offeredChanged || choices.has(field.field));
	const submit = () => {
		if (!ready) {
			return;
		}
		onAccept(Array.from(choices, ([field, source]) => ({ field, source })));
	};

	return (
		<section
			className="min-w-0 space-y-5"
			aria-label={`Review update for ${proposal.offered.name}`}
		>
			<div className="space-y-1">
				<h2 className="text-lg font-semibold">{proposal.offered.name}</h2>
				<p className="text-sm text-muted-foreground">
					Compare each changed field. Your current practice stays in use until you accept this
					update.
				</p>
			</div>
			{!baseKnown && (
				<Alert variant="warning">
					<AlertTitle>The version you adopted is not recorded</AlertTitle>
					<AlertDescription>
						We cannot tell your edits from the catalog’s changes. Choose a version for each changed
						field.
					</AlertDescription>
				</Alert>
			)}
			<Table bordered className="min-w-220 table-fixed">
				<caption className="sr-only">
					Changed fields in the adopted, current, and proposed versions
				</caption>
				<TableHeader>
					<TableRow variant="static">
						<TableHead className="w-38">Field</TableHead>
						<TableHead>Base</TableHead>
						<TableHead>Current</TableHead>
						<TableHead>Proposed</TableHead>
						<TableHead className="w-42">Use</TableHead>
					</TableRow>
				</TableHeader>
				<TableBody>
					{proposal.fields.map(({ field, offeredChanged, conflict }) => (
						<TableRow key={field} variant="static">
							{/* A row header, muted like the column headers: the field is the key, and the three
							    versions beside it are what the reader compares. */}
							<TableHead scope="row" variant="body">
								<span className="flex flex-wrap items-center gap-x-2 gap-y-1">
									{PRACTICE_DEFINITION_FIELDS[field].label}
									{conflict && <Badge variant="warning">Conflict</Badge>}
									{!offeredChanged && <Badge variant="secondary">Local edit</Badge>}
								</span>
							</TableHead>
							<TableCell className="align-top whitespace-normal">
								{base === null ? (
									<span className="text-xs text-muted-foreground">Not recorded</span>
								) : (
									<PracticeDefinitionFieldValue definition={base} field={field} />
								)}
							</TableCell>
							<TableCell className="align-top whitespace-normal">
								<PracticeDefinitionFieldValue definition={proposal.current} field={field} />
							</TableCell>
							<TableCell className="align-top whitespace-normal">
								<PracticeDefinitionFieldValue definition={proposal.offered} field={field} />
							</TableCell>
							<TableCell className="align-top">
								{offeredChanged ? (
									<RadioGroup
										aria-label={`Use a version for ${PRACTICE_DEFINITION_FIELDS[field].label}`}
										value={choices.get(field) ?? ""}
										disabled={pending !== null}
										onValueChange={(choice) => {
											if (choice === "CURRENT" || choice === "OFFERED") {
												setChoices((previous) => new Map(previous).set(field, choice));
											}
										}}
									>
										<label className="flex items-center gap-2" htmlFor={`${id}-${field}-current`}>
											<RadioGroupItem id={`${id}-${field}-current`} value="CURRENT" /> Current
										</label>
										<label className="flex items-center gap-2" htmlFor={`${id}-${field}-offered`}>
											<RadioGroupItem id={`${id}-${field}-offered`} value="OFFERED" /> Proposed
										</label>
									</RadioGroup>
								) : (
									<span className="text-xs text-muted-foreground">Keep current</span>
								)}
							</TableCell>
						</TableRow>
					))}
				</TableBody>
			</Table>
			<div className="flex flex-wrap gap-2">
				<Button type="button" disabled={!ready || pending !== null} onClick={submit}>
					{pending === "accept" && <Spinner className="mr-1.5 size-4" />}
					{pending === "accept" ? "Accepting selected fields…" : "Accept selected fields"}
				</Button>
				<Button type="button" variant="outline" disabled={pending !== null} onClick={onDecline}>
					{pending === "decline" && <Spinner className="mr-1.5 size-4" />}
					{pending === "decline" ? "Declining update…" : "Decline this update"}
				</Button>
			</div>
			<p className="text-xs text-muted-foreground">
				Declining keeps your practice as it is until the catalog changes again. Reset to catalog
				stays available on the practice.
			</p>
		</section>
	);
}
