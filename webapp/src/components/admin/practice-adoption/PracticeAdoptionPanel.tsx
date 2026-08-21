import { Link } from "@tanstack/react-router";
import { CircleAlert, Copy, ShieldCheck } from "lucide-react";
import { useId } from "react";
import type { CatalogPracticePreview, PracticeDefinitionOptions } from "@/api/types.gen";
import { PracticeDefinitionPreview } from "@/components/admin/practice-adoption/PracticeDefinitionPreview";
import { AreaPill } from "@/components/admin/practice-catalog/AreaPill";
import { PracticeAutomatedReviewValidationBadge } from "@/components/admin/practice-catalog/PracticeEvidenceSummary";
import { DetailRow } from "@/components/common/DetailRow";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { DetailDrawerHeader } from "@/components/core/detail-drawer/DetailDrawerHeader";
import { AUTONOMY_DEFS } from "@/components/practice-vocabulary/autonomy-defs";
import { CATALOG_AVAILABILITY_DEFS } from "@/components/practice-vocabulary/catalog-availability-defs";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
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
import { Spinner } from "@/components/ui/spinner";
import { artifactKindLabel } from "@/lib/artifact-kinds";
import { cn } from "@/lib/utils";

/**
 * The panel's body is one of three things, never a combination, so it is one union rather than
 * parallel `isLoading` / `isError` / `preview` a caller could set together. `onRetry` lives inside
 * the branch that can use it, and the three ways a loaded panel can be busy are one axis.
 */
export type PracticeAdoptionState =
	| { status: "loading" }
	| { status: "error"; error: unknown; onRetry: () => void }
	| {
			status: "ready";
			preview: CatalogPracticePreview;
			definitionOptions: PracticeDefinitionOptions;
			/** `stale` means a conditional add lost its race and the refreshed preview needs re-reading. */
			action: "idle" | "adding" | "stale";
	  };

export interface PracticeAdoptionPanelProps {
	workspaceSlug: string;
	state: PracticeAdoptionState;
	onAdopt: () => void;
	nested?: boolean;
}

/**
 * One catalog practice: what it will do to this workspace, then the definition that is the evidence
 * for it, then the action. The outcome leads because "what will this change" is the question being
 * answered — the definition is why the answer is trustworthy.
 */
export function PracticeAdoptionPanel({
	workspaceSlug,
	state,
	onAdopt,
	nested,
}: PracticeAdoptionPanelProps) {
	// Two levels of the stack can show a practice at once, so nothing here may hold a fixed DOM id.
	const idPrefix = useId();
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
				<div className="min-w-0 flex-1 space-y-0.5">
					<DrawerTitle className="break-words">{preview.definition.name}</DrawerTitle>
					<DrawerDescription>
						{artifactKindLabel(preview.definition.artifactKind)}
					</DrawerDescription>
				</div>
				{availability.badged && <StatusBadge def={availability} className="mt-0.5" />}
			</DetailDrawerHeader>

			<DrawerBody className="space-y-6">
				{action === "stale" && (
					<Alert variant="warning">
						<CircleAlert />
						<AlertTitle>The library changed while you were reading</AlertTitle>
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

				{!unavailable && (
					<section aria-labelledby={`${idPrefix}-outcome`} className="space-y-3">
						<h3 id={`${idPrefix}-outcome`} className="text-sm font-medium">
							Adding this practice will
						</h3>
						<ItemGroup className="gap-2">
							<Item variant="muted" size="sm" role="listitem">
								<ItemMedia variant="icon">
									<Copy />
								</ItemMedia>
								<ItemContent>
									<ItemTitle>Create a copy this workspace owns</ItemTitle>
									<ItemDescription>Edit it without changing the catalog.</ItemDescription>
								</ItemContent>
							</Item>
							<AreaOutcome preview={preview} />
							<AutonomyOutcome preview={preview} />
						</ItemGroup>
						<PracticeAutomatedReviewValidationBadge
							validation={preview.definition.automatedReviewValidation}
						/>
					</section>
				)}

				<Separator />

				<PracticeDefinitionPreview
					definition={preview.definition}
					options={definitionOptions}
					idPrefix={idPrefix}
				/>

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
					<Link
						to="/w/$workspaceSlug/admin/practices/$practiceSlug"
						params={{ workspaceSlug, practiceSlug: preview.slug }}
						search={{}}
						className={cn(buttonVariants(), "w-full sm:w-auto")}
					>
						Open workspace practice
					</Link>
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
 * A level that has not resolved still owes the drawer a title — Base UI requires one, and a panel
 * with no accessible name is unreachable by anything but sight.
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
					<div className="flex min-h-32 items-center justify-center" role="status">
						<Spinner />
						<span className="sr-only">Loading adoption preview</span>
					</div>
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
	if (preview.area.disposition === "UNASSIGNED") return "Belong to no area";
	const name = preview.area.definition?.name ?? preview.area.slug ?? "catalog area";
	if (preview.area.disposition === "REUSE_EXISTING_AREA") return `Join “${name}”`;
	return `Create “${name}”`;
}
