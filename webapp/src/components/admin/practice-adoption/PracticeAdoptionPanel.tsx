import { CircleAlert, Copy, ShieldCheck } from "lucide-react";
import type { CatalogPracticePreview, PracticeDefinitionOptions } from "@/api/types.gen";
import { PracticeDefinitionPreview } from "@/components/admin/practice-adoption/PracticeDefinitionPreview";
import { AreaPill } from "@/components/admin/practice-catalog/AreaPill";
import { PracticeDefinitionSkeleton } from "@/components/admin/practices/PracticeSkeletons";
import { DetailRow } from "@/components/common/DetailRow";
import type { PanelState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { DetailDrawerHeader } from "@/components/core/detail-drawer/DetailDrawerHeader";
import { DetailStackLink } from "@/components/core/detail-drawer/DetailStackLink";
import { Section } from "@/components/core/Section";
import { AUTONOMY_DEFS } from "@/components/practice-vocabulary/autonomy-defs";
import { CATALOG_AVAILABILITY_DEFS } from "@/components/practice-vocabulary/catalog-availability-defs";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
import { WorkTypeLabel } from "@/components/practice-vocabulary/WorkTypeLabel";
import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button, buttonVariants } from "@/components/ui/button";
import { DrawerBody, DrawerDescription, DrawerFooter, DrawerTitle } from "@/components/ui/drawer";
import {
	Item,
	ItemContent,
	ItemDescription,
	ItemGroup,
	ItemMedia,
	ItemTitle,
} from "@/components/ui/item";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";

export type PracticeAdoptionState = PanelState<{
	preview: CatalogPracticePreview;
	definitionOptions: PracticeDefinitionOptions;
	/** `stale` means a conditional add lost its race and the refreshed preview needs re-reading. */
	action: "idle" | "adding" | "stale";
}>;

export interface PracticeAdoptionPanelProps {
	state: PracticeAdoptionState;
	onAdopt: () => void;
	nested?: boolean;
}

/**
 * The outcome leads: "what will this change" is the question, and the definition below it is the
 * evidence for the answer.
 */
export function PracticeAdoptionPanel({ state, onAdopt, nested }: PracticeAdoptionPanelProps) {
	if (state.status !== "ready") {
		return <PracticeAdoptionPlaceholder state={state} nested={nested} />;
	}
	const { preview, definitionOptions, action } = state;
	const availability = CATALOG_AVAILABILITY_DEFS[preview.availability];
	const unavailable = preview.availability !== "AVAILABLE";

	return (
		<>
			<DetailDrawerHeader nested={nested}>
				<AreaPill
					size="lg"
					slug={preview.area.slug}
					name={preview.area.definition?.name}
					icon={preview.area.definition?.icon}
					color={preview.area.definition?.color}
				/>
				<div className="min-w-0 flex-1 space-y-1">
					<DrawerTitle className="break-words">{preview.definition.name}</DrawerTitle>
					<DrawerDescription>
						<WorkTypeLabel artifactKind={preview.definition.artifactKind} />
					</DrawerDescription>
					{/* Under the title, not beside it: the badge is a sentence of its own, and a second text
					    column would take the width the title needs to stay on one or two lines. */}
					{availability.badged && <StatusBadge def={availability} className="mt-1.5" />}
				</div>
			</DetailDrawerHeader>

			<DrawerBody className="space-y-6">
				{action === "stale" && (
					<Alert variant="warning">
						<CircleAlert />
						<AlertTitle>The catalog changed while you were reading</AlertTitle>
						<AlertDescription>
							This is the current definition and outcome. Nothing was added.
						</AlertDescription>
					</Alert>
				)}
				{unavailable && (
					<Alert variant="warning">
						<CircleAlert />
						<AlertTitle>{availability.label}</AlertTitle>
						<AlertDescription>{availability.description}</AlertDescription>
					</Alert>
				)}

				<PracticeDefinitionPreview definition={preview.definition} options={definitionOptions} />

				{!unavailable && (
					<>
						<Separator />
						<Section size="sm" title="Adding this practice will">
							<ItemGroup className="gap-2">
								<Item variant="muted" size="sm" role="listitem">
									<ItemMedia variant="icon">
										<Copy />
									</ItemMedia>
									<ItemContent>
										<ItemTitle>Create a copy this workspace owns</ItemTitle>
										<ItemDescription>
											Edit it freely. Later catalog changes never reach it, and yours never reach
											the catalog.
										</ItemDescription>
									</ItemContent>
								</Item>
								<AreaOutcome preview={preview} />
								<AutonomyOutcome preview={preview} />
							</ItemGroup>
						</Section>
					</>
				)}

				<Accordion aria-label="Catalog provenance">
					<AccordionItem value="catalog-details">
						<AccordionTrigger>Catalog details</AccordionTrigger>
						<AccordionContent>
							<dl className="divide-y">
								<DetailRow label="Source">
									<code>{preview.slug}</code>
								</DetailRow>
								<DetailRow label="Review version">
									<code>{preview.sourceReviewRuleFingerprint}</code>
								</DetailRow>
							</dl>
						</AccordionContent>
					</AccordionItem>
				</Accordion>
			</DrawerBody>

			<DrawerFooter>
				{unavailable ? (
					<DetailStackLink
						entry={{ kind: "practice", id: preview.slug }}
						className={cn(buttonVariants(), "w-full sm:w-auto")}
					>
						Open workspace practice
					</DetailStackLink>
				) : (
					<Button onClick={onAdopt} disabled={action === "adding"} className="w-full sm:w-auto">
						<ShieldCheck /> {action === "adding" ? "Adding…" : "Add practice"}
					</Button>
				)}
			</DrawerFooter>
		</>
	);
}

/**
 * A level that has not resolved still owes the drawer a title. Base UI does not enforce one: with no
 * `Title` it drops `aria-labelledby` and the panel renders silently with no accessible name, so the
 * failure surfaces only in an axe run, or in a screen reader announcing an unnamed dialog.
 */
function PracticeAdoptionPlaceholder({
	state,
	nested,
}: {
	state: Extract<PracticeAdoptionState, { status: "loading" } | { status: "error" }>;
	nested?: boolean;
}) {
	return (
		<>
			<DetailDrawerHeader nested={nested}>
				<DrawerTitle>Practice</DrawerTitle>
			</DetailDrawerHeader>
			<DrawerBody>
				{state.status === "loading" ? (
					<PracticeDefinitionSkeleton />
				) : (
					<QueryErrorAlert
						error={state.error}
						title="Couldn't load the adoption preview"
						onRetry={state.onRetry}
					/>
				)}
			</DrawerBody>
		</>
	);
}

function AreaOutcome({ preview }: { preview: CatalogPracticePreview }) {
	return (
		<Item variant="muted" size="sm" role="listitem">
			<ItemMedia variant="icon" className="bg-transparent">
				<AreaPill
					size="sm"
					slug={preview.area.slug}
					name={preview.area.definition?.name}
					icon={preview.area.definition?.icon}
					color={preview.area.definition?.color}
				/>
			</ItemMedia>
			<ItemContent>
				<ItemTitle>{areaTitle(preview)}</ItemTitle>
				{preview.area.definition?.description && (
					<ItemDescription className="line-clamp-none">
						{preview.area.definition.description}
					</ItemDescription>
				)}
			</ItemContent>
		</Item>
	);
}

function AutonomyOutcome({ preview }: { preview: CatalogPracticePreview }) {
	const autonomy = AUTONOMY_DEFS[preview.initialAutonomy];
	return (
		// No bare icon beside the badge: `StatusBadge` already renders the registry's icon, and the
		// same mark twice in one row reads as two facts.
		<Item variant="muted" size="sm" role="listitem">
			<ItemContent>
				<ItemTitle>
					<StatusBadge def={autonomy} />
				</ItemTitle>
				<ItemDescription className="line-clamp-none">{autonomy.description}</ItemDescription>
			</ItemContent>
		</Item>
	);
}

function areaTitle(preview: CatalogPracticePreview): string {
	if (preview.area.disposition === "UNASSIGNED") return "Stay unassigned";
	const name = preview.area.definition?.name ?? preview.area.slug ?? "catalog area";
	if (preview.area.disposition === "REUSE_EXISTING_AREA") return `Join “${name}”`;
	return `Create “${name}”`;
}
