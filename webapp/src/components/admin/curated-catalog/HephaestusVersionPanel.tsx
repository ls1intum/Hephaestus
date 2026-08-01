import { RotateCcw } from "lucide-react";
import type {
	CatalogEntryStatus,
	CuratedAreaRequest,
	CuratedPracticeRequest,
} from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import {
	canKeepOurVersion,
	canUseHephaestusVersion,
	curatedEntryCopy,
} from "./curated-entry-state";

/** Every field either definition can carry, so a new one cannot silently reach the page unlabelled. */
type ShippedDefinition = Partial<
	Record<keyof CuratedPracticeRequest | keyof CuratedAreaRequest, unknown>
>;

export interface HephaestusVersionPanelProps {
	status: CatalogEntryStatus;
	kind: "practice" | "area";
	/** The definition Hephaestus ships now, shown verbatim so taking it is never a leap. */
	shipped?: ShippedDefinition;
	isResetPending: boolean;
	isKeepPending?: boolean;
	disabled: boolean;
	onUseHephaestusVersion?: () => void;
	onKeepOurVersion?: () => void;
}

const FIELD_LABELS: Record<keyof CuratedPracticeRequest | keyof CuratedAreaRequest, string> = {
	name: "Name",
	criteria: "Evaluation criteria",
	whyItMatters: "Why it matters",
	whatGoodLooksLike: "What good looks like",
	precomputeScript: "Precompute script",
	triggerEvents: "Starts a review when",
	description: "Description",
	areaSlug: "Area",
	artifactType: "Applies to",
	icon: "Icon",
	color: "Color",
};

function labelFor(field: string): string {
	return FIELD_LABELS[field as keyof typeof FIELD_LABELS] ?? field;
}

/**
 * How the entry stands, what Hephaestus ships if that differs, and the two things an administrator
 * can do about it. The shipped definition is shown rather than described: taking it replaces what
 * this instance offers, and nobody should agree to text they cannot read.
 *
 * <p>Deliberately not an `Alert`. An alert is an assertive live region, and this panel holds a
 * disclosure and two buttons — expanding it would read the whole definition aloud over whatever the
 * user was doing.
 */
export function HephaestusVersionPanel({
	status,
	kind,
	shipped,
	isResetPending,
	isKeepPending = false,
	disabled,
	onUseHephaestusVersion,
	onKeepOurVersion,
}: HephaestusVersionPanelProps) {
	const copy = curatedEntryCopy(status, kind);
	const canReset = canUseHephaestusVersion(status) && onUseHephaestusVersion;
	const canKeep = canKeepOurVersion(status) && onKeepOurVersion;
	const busy = isResetPending || isKeepPending || disabled;

	return (
		<section
			aria-label={`How this ${kind} stands against Hephaestus`}
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
										Show the Hephaestus version
									</Button>
								}
							/>
							<CollapsibleContent className="mt-2 space-y-3 rounded-md border bg-muted/40 p-3">
								{Object.entries(shipped)
									.filter(([, value]) => value !== null && value !== undefined && value !== "")
									.map(([field, value]) => (
										<div key={field} className="space-y-1">
											<p className="font-medium text-xs">{labelFor(field)}</p>
											<pre className="whitespace-pre-wrap break-words font-mono text-muted-foreground text-xs">
												{Array.isArray(value) ? value.join(", ") : String(value)}
											</pre>
										</div>
									))}
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
									{isResetPending ? "Using the Hephaestus version…" : "Use the Hephaestus version"}
								</Button>
							)}
							{canKeep && (
								<Button
									type="button"
									variant="ghost"
									size="sm"
									disabled={busy}
									onClick={onKeepOurVersion}
								>
									{isKeepPending && <Spinner className="mr-1.5 size-3.5" />}
									{isKeepPending ? "Keeping our version…" : "Keep our version"}
								</Button>
							)}
						</div>
					)}
				</div>
			</div>
		</section>
	);
}
