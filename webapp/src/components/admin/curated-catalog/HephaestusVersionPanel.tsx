import { RotateCcw } from "lucide-react";
import type {
	CatalogEntryStatus,
	CuratedAreaRequest,
	CuratedPracticeRequest,
} from "@/api/types.gen";
import {
	FOCUS_ARTIFACT_OPTIONS,
	TRIGGER_EVENTS_BY_FOCUS,
} from "@/components/admin/practice-catalog/constants";
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
	Record<keyof CuratedPracticeRequest | keyof CuratedAreaRequest, unknown>
>;

export interface HephaestusVersionPanelProps {
	status: CatalogEntryStatus;
	kind: "practice" | "area";
	shipped?: ShippedDefinition;
	areaNames?: Readonly<Record<string, string>>;
	isResetPending: boolean;
	isKeepPending?: boolean;
	disabled: boolean;
	onUseHephaestusVersion?: () => void;
	onKeepCurrentDefinition?: () => void;
}

const AREA_FIELDS = {
	name: "Name",
	description: "Description",
	icon: "Icon",
	color: "Color",
} satisfies Record<keyof CuratedAreaRequest, string>;

const PRACTICE_FIELDS = {
	name: "Name",
	artifactType: "Evaluates",
	areaSlug: "Area",
	triggerEvents: "Starts a review when",
	criteria: "Evaluation criteria",
	whyItMatters: "Why it matters",
	whatGoodLooksLike: "What good looks like",
	precomputeScript: "Precompute script",
} satisfies Record<keyof CuratedPracticeRequest, string>;

function fieldEntries(fields: Record<string, string>): Array<[keyof ShippedDefinition, string]> {
	return Object.entries(fields) as Array<[keyof ShippedDefinition, string]>;
}

function displayValue(
	field: string,
	value: unknown,
	shipped: ShippedDefinition,
	areaNames: Readonly<Record<string, string>>,
): string {
	if (value === null || value === undefined || value === "") {
		return field === "areaSlug" ? "Unassigned" : "Not set";
	}
	if (field === "triggerEvents" && Array.isArray(value) && value.length === 0) {
		return "No automatic trigger";
	}
	const words = (token: string) =>
		token
			.replace(/_/g, " ")
			.replace(/([a-z])([A-Z])/g, "$1 $2")
			.replace(/^./, (letter) => letter.toUpperCase());
	if (field === "artifactType") {
		return (
			FOCUS_ARTIFACT_OPTIONS.find((option) => option.value === value)?.label ?? words(String(value))
		);
	}
	if (field === "triggerEvents" && Array.isArray(value)) {
		const artifact = shipped.artifactType;
		const options =
			typeof artifact === "string" && artifact in TRIGGER_EVENTS_BY_FOCUS
				? TRIGGER_EVENTS_BY_FOCUS[artifact as keyof typeof TRIGGER_EVENTS_BY_FOCUS]
				: [];
		return value
			.map(
				(event) => options.find((option) => option.value === event)?.label ?? words(String(event)),
			)
			.join("\n");
	}
	if (field === "areaSlug" && typeof value === "string") {
		return areaNames[value] ?? "Area no longer exists";
	}
	if ((field === "icon" || field === "color") && typeof value === "string") return words(value);
	return Array.isArray(value) ? value.join("\n") : String(value);
}

export function HephaestusVersionPanel({
	status,
	kind,
	shipped,
	areaNames = {},
	isResetPending,
	isKeepPending = false,
	disabled,
	onUseHephaestusVersion,
	onKeepCurrentDefinition,
}: HephaestusVersionPanelProps) {
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
			aria-label={`Hephaestus default for this ${kind}`}
			className={cn(
				"max-w-3xl rounded-lg border p-4 text-sm",
				copy.tone === "attention" ? "border-warning/50 bg-warning/5" : "bg-card",
			)}
		>
			<div className="flex items-start gap-3">
				<RotateCcw className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
				<div className="min-w-0 flex-1">
					<h2 className="font-medium">{copy.label}</h2>
					<p className="mt-1 text-muted-foreground">{copy.detail}</p>

					{shipped && (
						<Collapsible className="mt-3 w-full">
							<CollapsibleTrigger
								render={
									<Button type="button" variant="outline" size="sm">
										{viewLabel}
									</Button>
								}
							/>
							<CollapsibleContent className="mt-2 space-y-3 rounded-md border bg-muted/40 p-3">
								{fieldEntries(kind === "area" ? AREA_FIELDS : PRACTICE_FIELDS).map(
									([field, label]) => (
										<div key={field} className="space-y-1">
											<p className="font-medium text-xs">{label}</p>
											<p
												className={cn(
													"whitespace-pre-wrap break-words text-muted-foreground text-xs",
													field === "precomputeScript" && "font-mono",
												)}
											>
												{displayValue(field, shipped[field], shipped, areaNames)}
											</p>
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
