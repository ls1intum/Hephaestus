import { RotateCcw } from "lucide-react";
import type { CatalogEntryStatus } from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { canUseHephaestusVersion, curatedEntryCopy } from "./curated-entry-state";

export interface HephaestusVersionPanelProps {
	status: CatalogEntryStatus;
	kind: "practice" | "area";
	/** The definition Hephaestus ships now, shown verbatim so accepting it is never a leap. */
	shipped?: Record<string, unknown> | null;
	isResetPending: boolean;
	disabled: boolean;
	onUseHephaestusVersion?: () => void;
	onKeepOurs?: () => void;
}

const FIELD_LABELS: Record<string, string> = {
	name: "Name",
	criteria: "Criteria",
	whyItMatters: "Why it matters",
	whatGoodLooksLike: "What good looks like",
	precomputeScript: "Precompute script",
	description: "Description",
	areaSlug: "Area",
	artifactType: "Applies to",
	displayOrder: "Display order",
	icon: "Icon",
	color: "Colour",
};

/**
 * How the entry stands, what Hephaestus ships if that differs, and the two things an administrator
 * can do about it. The shipped definition is shown rather than described: taking it replaces what
 * this instance runs, and nobody should agree to text they cannot read.
 */
export function HephaestusVersionPanel({
	status,
	kind,
	shipped,
	isResetPending,
	disabled,
	onUseHephaestusVersion,
	onKeepOurs,
}: HephaestusVersionPanelProps) {
	const copy = curatedEntryCopy(status, kind);
	const canReset = canUseHephaestusVersion(status) && onUseHephaestusVersion;

	return (
		<Alert variant={copy.tone === "attention" ? "warning" : "default"} className="max-w-3xl">
			<RotateCcw />
			<AlertTitle>{copy.label}</AlertTitle>
			<AlertDescription>
				<p>{copy.detail}</p>

				{shipped && (
					<Collapsible className="mt-3 w-full">
						<CollapsibleTrigger
							render={
								<Button type="button" variant="outline" size="sm">
									Show the Hephaestus version
								</Button>
							}
						/>
						<CollapsibleContent className="mt-2 space-y-3 rounded-md border bg-muted/40 p-3">
							{Object.entries(shipped)
								.filter(([, value]) => value !== null && value !== undefined && value !== "")
								.map(([field, value]) => (
									<div key={field} className="space-y-1">
										<p className="font-medium text-xs">{FIELD_LABELS[field] ?? field}</p>
										<pre className="whitespace-pre-wrap break-words font-mono text-muted-foreground text-xs">
											{Array.isArray(value) ? value.join(", ") : String(value)}
										</pre>
									</div>
								))}
						</CollapsibleContent>
					</Collapsible>
				)}

				{(canReset || onKeepOurs) && (
					<div className="mt-3 flex flex-wrap gap-2">
						{canReset && (
							<Button
								type="button"
								variant="outline"
								size="sm"
								disabled={isResetPending || disabled}
								onClick={onUseHephaestusVersion}
							>
								Use the Hephaestus version
							</Button>
						)}
						{status.state === "UPDATE_WAITING" && onKeepOurs && (
							<Button
								type="button"
								variant="ghost"
								size="sm"
								disabled={isResetPending || disabled}
								onClick={onKeepOurs}
							>
								Keep ours
							</Button>
						)}
					</div>
				)}
			</AlertDescription>
		</Alert>
	);
}
