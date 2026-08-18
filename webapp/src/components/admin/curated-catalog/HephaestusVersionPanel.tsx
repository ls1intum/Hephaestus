import { RotateCcw } from "lucide-react";
import { useId } from "react";
import type {
	CatalogEntryStatus,
	CuratedAreaRequest,
	CuratedPracticeDefinition,
	PracticeDefinitionOptions,
} from "@/api/types.gen";
import { PracticeEvidenceSummary } from "@/components/admin/practice-catalog/PracticeEvidenceSummary";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Spinner } from "@/components/ui/spinner";
import { artifactKindLabel } from "@/lib/artifact-kinds";
import { humanizeToken } from "@/lib/humanize";
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
	criteria: "What to look for",
	whyItMatters: "Why it matters",
	whatGoodLooksLike: "What good looks like",
	precomputeScript: "Static analysis",
	// The occasions and their evidence render under a heading of their own, which this must not repeat.
	automatedReviewPolicy: "How it is reviewed",
} satisfies Record<
	Exclude<keyof CuratedPracticeDefinition, "automatedReviewValidation" | "bindings">,
	string
>;

function fieldEntries(fields: Record<string, string>): Array<[keyof ShippedDefinition, string]> {
	return Object.entries(fields) as Array<[keyof ShippedDefinition, string]>;
}

function displayValue(
	field: string,
	value: unknown,
	areaNames: Readonly<Record<string, string>>,
): string {
	if (value === null || value === undefined || value === "") {
		return field === "areaSlug" ? "Unassigned" : "Not set";
	}
	if (field === "artifactKind") {
		return artifactKindLabel(String(value));
	}
	if (field === "areaSlug" && typeof value === "string") {
		return areaNames[value] ?? "Area no longer exists";
	}
	if ((field === "icon" || field === "color") && typeof value === "string")
		return humanizeToken(value);
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
															bindings={shippedPractice.bindings}
															validation={shippedPractice.automatedReviewValidation}
															sources={shippedDefinitionOptions.allowedSources}
															signals={shippedDefinitionOptions.signals}
															workTypeLabel={artifactKindLabel(shippedPractice.artifactKind)}
														/>
													) : (
														"Evidence details are unavailable for this work type."
													)
												) : (
													displayValue(field, shippedDefinition[field], areaNames)
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
