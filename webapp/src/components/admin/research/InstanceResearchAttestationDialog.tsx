import { type SubmitEvent, useId, useState } from "react";

import type { CloseResearchDisclosure } from "@/api/types.gen";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Checkbox } from "@/components/ui/checkbox";
import {
	Field,
	FieldContent,
	FieldDescription,
	FieldError,
	FieldLabel,
	FieldLegend,
	FieldSet,
	FieldTitle,
} from "@/components/ui/field";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";

import { CLOSURE_LABELS, CLOSURE_OPTIONS } from "./research-labels";

type Closure = CloseResearchDisclosure["closure"];

interface DialogBase {
	open: boolean;
	onOpenChange: (open: boolean) => void;
	/** The workspace and release date that name the dataset on screen. */
	datasetLabel: string;
	datasetId: string;
}

export type InstanceResearchAttestationDialogProps = DialogBase &
	(
		| {
				mode: "removal";
				/** The participant keys whose removal is owed in this dataset. */
				participantKeys: string[];
				onConfirm: (participantKeys: string[]) => void;
		  }
		| {
				mode: "closure";
				/** Removals still owed; closing states that the whole copy is gone or anonymized. */
				owedCount: number;
				onConfirm: (closure: Closure) => void;
		  }
	);

/**
 * Records what the operator states about the research team's copies. Hephaestus cannot see those
 * copies, so the dialog asks for a deliberate statement and never claims to verify it. Closes on
 * confirm; the page reloads the open datasets from the server and reports the outcome.
 */
export function InstanceResearchAttestationDialog(props: InstanceResearchAttestationDialogProps) {
	return (
		<AlertDialog open={props.open} onOpenChange={props.onOpenChange}>
			<AlertDialogContent>
				<AttestationForm
					key={`${props.mode}:${props.datasetId}:${String(props.open)}`}
					{...props}
				/>
			</AlertDialogContent>
		</AlertDialog>
	);
}

function AttestationForm(props: InstanceResearchAttestationDialogProps) {
	const id = useId();
	const [selectedKeys, setSelectedKeys] = useState<string[]>(
		props.mode === "removal" ? props.participantKeys : [],
	);
	const [closure, setClosure] = useState<Closure | undefined>();
	const [attested, setAttested] = useState(false);
	const [submitted, setSubmitted] = useState(false);
	const missingKeys = submitted && props.mode === "removal" && selectedKeys.length === 0;
	const missingClosure = submitted && props.mode === "closure" && closure === undefined;
	const missingAttestation = submitted && !attested;

	function confirm(event: SubmitEvent<HTMLFormElement>) {
		event.preventDefault();
		setSubmitted(true);
		if (!attested) {
			return;
		}
		if (props.mode === "removal") {
			if (selectedKeys.length === 0) {
				return;
			}
			props.onConfirm(selectedKeys);
		} else {
			if (closure === undefined) {
				return;
			}
			props.onConfirm(closure);
		}
		props.onOpenChange(false);
	}

	return (
		<>
			<AlertDialogHeader>
				<AlertDialogTitle>
					{props.mode === "removal" ? "Record removed participants?" : "Close this dataset?"}
				</AlertDialogTitle>
				<AlertDialogDescription>
					{props.mode === "removal"
						? `${props.datasetLabel}. Record only what the research team confirmed. Hephaestus cannot see their copies.`
						: `${props.datasetLabel}. Closing deletes the link between its participant keys and accounts. You cannot undo this. Hephaestus cannot see the research team’s copies, so this records your statement, not a check.`}
				</AlertDialogDescription>
			</AlertDialogHeader>
			<form onSubmit={confirm} className="grid gap-4">
				{props.mode === "removal" ? (
					<FieldSet data-invalid={missingKeys}>
						<FieldLegend variant="label">Participants removed</FieldLegend>
						{props.participantKeys.map((key) => (
							<Field key={key} orientation="horizontal">
								<Checkbox
									id={`${id}-${key}`}
									checked={selectedKeys.includes(key)}
									onCheckedChange={(checked) =>
										setSelectedKeys((current) =>
											checked
												? [...current.filter((value) => value !== key), key]
												: current.filter((value) => value !== key),
										)
									}
								/>
								<FieldLabel htmlFor={`${id}-${key}`} className="font-mono">
									{key}
								</FieldLabel>
							</Field>
						))}
						{missingKeys && <FieldError>Choose at least one participant.</FieldError>}
					</FieldSet>
				) : (
					<FieldSet data-invalid={missingClosure}>
						<FieldLegend variant="label">What happened to every copy?</FieldLegend>
						{props.owedCount > 0 && (
							<FieldDescription>
								{props.owedCount === 1
									? "1 removal is still owed."
									: `${props.owedCount} removals are still owed.`}{" "}
								Closing states that the whole copy is gone or anonymized, which covers them.
							</FieldDescription>
						)}
						<RadioGroup
							value={closure ?? null}
							onValueChange={(value) => setClosure(value ?? undefined)}
							aria-invalid={missingClosure}
						>
							{CLOSURE_OPTIONS.map((value) => (
								<FieldLabel key={value} htmlFor={`${id}-${value}`}>
									<Field orientation="horizontal">
										<RadioGroupItem
											id={`${id}-${value}`}
											value={value}
											aria-labelledby={`${id}-${value}-title`}
											aria-describedby={`${id}-${value}-detail`}
										/>
										<FieldContent>
											<FieldTitle id={`${id}-${value}-title`}>
												{CLOSURE_LABELS[value].label}
											</FieldTitle>
											<FieldDescription id={`${id}-${value}-detail`}>
												{CLOSURE_LABELS[value].description}
											</FieldDescription>
										</FieldContent>
									</Field>
								</FieldLabel>
							))}
						</RadioGroup>
						{missingClosure && <FieldError>Choose what happened to every copy.</FieldError>}
					</FieldSet>
				)}
				<Field orientation="horizontal" data-invalid={missingAttestation}>
					<Checkbox
						id={`${id}-attest`}
						checked={attested}
						onCheckedChange={setAttested}
						aria-invalid={missingAttestation}
						aria-describedby={missingAttestation ? `${id}-attest-error` : undefined}
					/>
					<FieldContent>
						<FieldLabel htmlFor={`${id}-attest`}>
							{props.mode === "removal"
								? "The research team confirmed the removal from every copy that is not anonymized. It removed these participants’ own rows and every review fact that lists their key."
								: "The research team confirmed this for every copy, including copies of data taken from the file."}
						</FieldLabel>
						{missingAttestation && (
							<FieldError id={`${id}-attest-error`}>Confirm this before you record it.</FieldError>
						)}
					</FieldContent>
				</Field>
				<AlertDialogFooter>
					<AlertDialogCancel>Cancel</AlertDialogCancel>
					<AlertDialogAction type="submit">
						{props.mode === "removal" ? "Record removal" : "Close dataset"}
					</AlertDialogAction>
				</AlertDialogFooter>
			</form>
		</>
	);
}
