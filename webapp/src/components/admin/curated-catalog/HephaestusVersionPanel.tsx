import { RotateCcw } from "lucide-react";
import { useId } from "react";
import type {
	CatalogEntryStatus,
	CuratedAreaRequest,
	CuratedPracticeDefinition,
	PracticeDefinitionOptions,
	PracticeTriggerEventOption,
} from "@/api/types.gen";
import { FOCUS_ARTIFACT_OPTIONS } from "@/components/admin/practice-catalog/constants";
import { PracticeEvidenceSummary } from "@/components/admin/practice-catalog/PracticeEvidenceSummary";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import {
	canKeepCurrentDefinition,
	canUseHephaestusVersion,
	curatedEntryCopy,
} from "./curated-entry-state";

type ShippedDefinition = Partial<
	Record<keyof CuratedPracticeDefinition | keyof CuratedAreaRequest, unknown>
>;

interface HephaestusVersionPanelBaseProps {
	status: CatalogEntryStatus;
	areaNames?: Readonly<Record<string, string>>;
	isResetPending: boolean;
	isKeepPending?: boolean;
	disabled: boolean;
	onUseHephaestusVersion?: () => void;
	onKeepCurrentDefinition?: () => void;
}

export type HephaestusVersionPanelProps = HephaestusVersionPanelBaseProps &
	(
		| {
				kind: "practice";
				shipped?: CuratedPracticeDefinition;
				definitionOptions: PracticeDefinitionOptions;
		  }
		| { kind: "area"; shipped?: CuratedAreaRequest }
	);

const AREA_FIELDS = {
	name: "Name",
	description: "Description",
	icon: "Icon",
	color: "Color",
} satisfies Record<keyof CuratedAreaRequest, string>;

const PRACTICE_FIELDS = {
	name: "Name",
	artifactKind: "Work reviewed",
	areaSlug: "Area",
	triggerEvents: "Starts a review when",
	criteria: "What to look for",
	whyItMatters: "Why it matters",
	whatGoodLooksLike: "What good looks like",
	precomputeScript: "Static analysis",
	automatedReviewPolicy: "Mentoring support and evidence",
} satisfies Record<Exclude<keyof CuratedPracticeDefinition, "automatedReviewValidation">, string>;

function fieldEntries(fields: Record<string, string>): Array<[keyof ShippedDefinition, string]> {
	return Object.entries(fields) as Array<[keyof ShippedDefinition, string]>;
}

function words(token: string): string {
	return token
		.replace(/_/g, " ")
		.replace(/([a-z])([A-Z])/g, "$1 $2")
		.toLowerCase()
		.replace(/^./, (letter) => letter.toUpperCase());
}

function displayValue(
	field: string,
	value: unknown,
	areaNames: Readonly<Record<string, string>>,
	triggerEvents: readonly PracticeTriggerEventOption[] = [],
): string {
	if (value === null || value === undefined || value === "") {
		return field === "areaSlug" ? "Unassigned" : "Not set";
	}
	if (field === "triggerEvents" && Array.isArray(value) && value.length === 0) {
		return "No automatic trigger";
	}
	if (field === "artifactKind") {
		return (
			FOCUS_ARTIFACT_OPTIONS.find((option) => option.value === value)?.label ?? words(String(value))
		);
	}
	if (field === "triggerEvents" && Array.isArray(value)) {
		return value
			.map(
				(event) =>
					triggerEvents.find((option) => option.event === event)?.displayName ??
					words(String(event)),
			)
			.join("\n");
	}
	if (field === "areaSlug" && typeof value === "string") {
		return areaNames[value] ?? "Area no longer exists";
	}
	if ((field === "icon" || field === "color") && typeof value === "string") return words(value);
	return Array.isArray(value) ? value.join("\n") : String(value);
}

export function HephaestusVersionPanel(props: HephaestusVersionPanelProps) {
	const {
		status,
		kind,
		shipped,
		areaNames = {},
		isResetPending,
		isKeepPending = false,
		disabled,
		onUseHephaestusVersion,
		onKeepCurrentDefinition,
	} = props;
	const shippedDefinition: ShippedDefinition | undefined = shipped;
	const shippedPractice = props.kind === "practice" ? props.shipped : undefined;
	const shippedDefinitionOptions =
		props.kind === "practice" && shippedPractice
			? props.definitionOptions.workTypes.find(
					(option) => option.artifactKind === shippedPractice.artifactKind,
				)
			: undefined;
	const headingId = useId();
	const copy = curatedEntryCopy(status, kind);
	const canReset = canUseHephaestusVersion(status) && onUseHephaestusVersion;
	const canKeep = canKeepCurrentDefinition(status) && onKeepCurrentDefinition;
	const busy = isResetPending || isKeepPending || disabled;
	const updateAvailable = status.state === "UPDATE_WAITING";
	const viewLabel = updateAvailable ? "Review Hephaestus update" : "View Hephaestus default";
	const useLabel = updateAvailable ? "Apply Hephaestus update" : "Restore Hephaestus default";
	const keepLabel =
		status.state === "NO_LONGER_SHIPPED" ? "Keep saved version as custom" : "Keep saved version";

	return (
		<section
			aria-labelledby={headingId}
			className={cn(
				"max-w-3xl rounded-lg border p-4 text-sm",
				copy.tone === "attention" ? "border-warning/50 bg-warning/5" : "bg-card",
			)}
		>
			<div className="flex items-start gap-3">
				<RotateCcw className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
				<div className="min-w-0 flex-1">
					<h2 id={headingId} className="font-medium">
						{copy.label}
					</h2>
					<p className="mt-1 text-muted-foreground">{copy.detail}</p>

					{shippedDefinition && (
						<Collapsible className="mt-3 w-full">
							<CollapsibleTrigger
								render={
									<Button type="button" variant="outline" size="sm">
										{viewLabel}
									</Button>
								}
							/>
							<CollapsibleContent
								render={<dl />}
								className="mt-2 space-y-3 rounded-md border bg-muted/40 p-3"
							>
								{fieldEntries(kind === "area" ? AREA_FIELDS : PRACTICE_FIELDS).map(
									([field, label]) => (
										<div key={field} className="space-y-1">
											<dt className="font-medium text-xs">{label}</dt>
											<dd
												className={cn(
													"whitespace-pre-wrap break-words text-muted-foreground text-xs",
													field === "precomputeScript" && "font-mono",
												)}
											>
												{field === "automatedReviewPolicy" && shippedPractice ? (
													shippedDefinitionOptions ? (
														<PracticeEvidenceSummary
															policy={shippedPractice.automatedReviewPolicy}
															validation={shippedPractice.automatedReviewValidation}
															sources={shippedDefinitionOptions.allowedSources}
															workTypeLabel={
																FOCUS_ARTIFACT_OPTIONS.find(
																	(option) => option.value === shippedPractice.artifactKind,
																)?.label ?? "Reviewed work"
															}
														/>
													) : (
														"Evidence details are unavailable for this work type."
													)
												) : (
													displayValue(
														field,
														shippedDefinition[field],
														areaNames,
														shippedDefinitionOptions?.triggerEvents,
													)
												)}
											</dd>
										</div>
									),
								)}
							</CollapsibleContent>
						</Collapsible>
					)}

					{(canReset || canKeep) && (
						<div className="mt-3 flex flex-wrap items-center gap-2">
							{canReset && (
								<Button
									type="button"
									variant="outline"
									size="sm"
									disabled={busy}
									onClick={onUseHephaestusVersion}
								>
									{isResetPending && <Spinner className="mr-1.5 size-3.5" />}
									{isResetPending ? `${useLabel}…` : useLabel}
								</Button>
							)}
							{canKeep && (
								<Button
									type="button"
									variant="ghost"
									size="sm"
									disabled={busy}
									onClick={onKeepCurrentDefinition}
								>
									{isKeepPending && <Spinner className="mr-1.5 size-3.5" />}
									{isKeepPending ? `${keepLabel}…` : keepLabel}
								</Button>
							)}
						</div>
					)}
				</div>
			</div>
		</section>
	);
}
