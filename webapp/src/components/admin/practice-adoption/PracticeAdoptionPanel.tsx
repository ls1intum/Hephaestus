import { Link } from "@tanstack/react-router";
import { CircleAlert, CircleDashed, Copy, ShieldCheck } from "lucide-react";
import type { CatalogPracticePreview, PracticeDefinitionOptions } from "@/api/types.gen";
import { PracticeDefinitionPreview } from "@/components/admin/practice-adoption/PracticeDefinitionPreview";
import { getAreaVisual } from "@/components/admin/practice-catalog/area-visuals";
import { PracticeAutomatedReviewValidationBadge } from "@/components/admin/practice-catalog/PracticeEvidenceSummary";
import { DetailDrawerPanel } from "@/components/core/detail-drawer/DetailDrawerPanel";
import { AutonomyBadge } from "@/components/practice-vocabulary/AutonomyBadge";
import { AUTONOMY_DEFS } from "@/components/practice-vocabulary/autonomy-defs";
import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button, buttonVariants } from "@/components/ui/button";
import {
	Item,
	ItemContent,
	ItemDescription,
	ItemGroup,
	ItemMedia,
	ItemTitle,
} from "@/components/ui/item";
import { Separator } from "@/components/ui/separator";
import { artifactKindLabel } from "@/lib/artifact-kinds";
import { cn } from "@/lib/utils";

export interface PracticeAdoptionPanelProps {
	workspaceSlug: string;
	preview: CatalogPracticePreview;
	definitionOptions: PracticeDefinitionOptions;
	onAdopt: () => void;
	isPending: boolean;
	/** Set after a failed conditional add refreshed the preview, so the reader re-reads before retrying. */
	isStale?: boolean;
}

/**
 * One catalog practice, everything an administrator needs to decide, and the action — in the shape
 * every other detail drawer uses. The outcome summary sits above the definition because "what will
 * this do to my workspace" is the question being answered; the definition is the evidence for it.
 */
export function PracticeAdoptionPanel({
	workspaceSlug,
	preview,
	definitionOptions,
	onAdopt,
	isPending,
	isStale = false,
}: PracticeAdoptionPanelProps) {
	const unavailable = preview.availability !== "AVAILABLE";
	const { Icon, pill } = areaVisualOf(preview);

	return (
		<DetailDrawerPanel
			title={preview.definition.name}
			// Not the destination area: that is one of the outcome rows below, where it has an icon
			// and a consequence attached to it.
			description={artifactKindLabel(preview.definition.artifactKind)}
			media={
				<ItemMedia className={cn("size-9 rounded-md", pill)} aria-hidden="true">
					<Icon className="size-4" />
				</ItemMedia>
			}
			footer={
				unavailable ? (
					<Link
						to="/w/$workspaceSlug/admin/practices/$practiceSlug"
						params={{ workspaceSlug, practiceSlug: preview.slug }}
						search={{}}
						className={cn(buttonVariants(), "w-full sm:w-auto")}
					>
						Open workspace practice
					</Link>
				) : (
					<Button onClick={onAdopt} disabled={isPending} className="w-full sm:w-auto">
						<ShieldCheck /> {isPending ? "Adding…" : "Add practice"}
					</Button>
				)
			}
		>
			{isStale && (
				<Alert variant="warning">
					<CircleAlert />
					<AlertTitle>The library changed while you were reading</AlertTitle>
					<AlertDescription>
						This is the current definition and outcome. Nothing was added.
					</AlertDescription>
				</Alert>
			)}
			{unavailable && <UnavailableNotice preview={preview} />}

			<section aria-labelledby="adoption-outcome" className="space-y-3">
				<h2 id="adoption-outcome" className="text-sm font-medium">
					{unavailable ? "Workspace status" : "Adding this practice will"}
				</h2>
				<ItemGroup className="gap-2">
					<CopyOutcome />
					<AreaOutcome preview={preview} />
					<AutonomyOutcome preview={preview} />
				</ItemGroup>
				<PracticeAutomatedReviewValidationBadge
					validation={preview.definition.automatedReviewValidation}
				/>
			</section>

			<Separator />

			<PracticeDefinitionPreview definition={preview.definition} options={definitionOptions} />
			<CatalogDetails preview={preview} />
		</DetailDrawerPanel>
	);
}

function areaVisualOf(preview: CatalogPracticePreview) {
	if (!preview.area.slug) return { Icon: CircleDashed, pill: "bg-muted text-muted-foreground" };
	return getAreaVisual(
		preview.area.slug,
		preview.area.definition?.name ?? preview.area.slug,
		preview.area.definition?.icon,
		preview.area.definition?.color,
	);
}

function CopyOutcome() {
	return (
		<Item variant="muted" size="sm" role="listitem">
			<ItemMedia variant="icon">
				<Copy />
			</ItemMedia>
			<ItemContent>
				<ItemTitle>Create an independent copy</ItemTitle>
				<ItemDescription>Edit it without changing the catalog.</ItemDescription>
			</ItemContent>
		</Item>
	);
}

function AreaOutcome({ preview }: { preview: CatalogPracticePreview }) {
	const { Icon, pill } = areaVisualOf(preview);
	return (
		<Item variant="muted" size="sm" role="listitem">
			<ItemMedia className={cn("size-7 rounded-md", pill)} aria-hidden="true">
				<Icon className="size-4" />
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
	const AutonomyIcon = autonomy.icon;
	return (
		<Item variant="muted" size="sm" role="listitem">
			<ItemMedia variant="icon">
				<AutonomyIcon />
			</ItemMedia>
			<ItemContent>
				<ItemTitle>
					Start at <AutonomyBadge autonomy={preview.initialAutonomy} />
				</ItemTitle>
				<ItemDescription className="line-clamp-none">{autonomy.description}</ItemDescription>
			</ItemContent>
		</Item>
	);
}

function areaTitle(preview: CatalogPracticePreview): string {
	if (preview.area.disposition === "UNASSIGNED") return "No area";
	const name = preview.area.definition?.name ?? preview.area.slug ?? "catalog area";
	if (preview.area.disposition === "REUSE_EXISTING_AREA") return `Uses “${name}”`;
	return `Creates “${name}”`;
}

function CatalogDetails({ preview }: { preview: CatalogPracticePreview }) {
	return (
		<Accordion aria-label="Catalog provenance">
			<AccordionItem value="catalog-details">
				<AccordionTrigger>Catalog details</AccordionTrigger>
				<AccordionContent>
					<dl className="grid gap-3 text-sm sm:grid-cols-2">
						<div>
							<dt className="font-medium">Source</dt>
							<dd className="mt-1 break-all text-muted-foreground">
								<code>{preview.slug}</code>
							</dd>
						</div>
						<div>
							<dt className="font-medium">Review version</dt>
							<dd className="mt-1 break-all text-muted-foreground">
								<code>{preview.sourceReviewRuleFingerprint}</code>
							</dd>
						</div>
					</dl>
				</AccordionContent>
			</AccordionItem>
		</Accordion>
	);
}

function UnavailableNotice({ preview }: { preview: CatalogPracticePreview }) {
	const adopted = preview.availability === "ADOPTED";
	return (
		<Alert variant="warning">
			<CircleAlert />
			<AlertTitle>{adopted ? "Already in this workspace" : "Name unavailable"}</AlertTitle>
			<AlertDescription>
				{adopted
					? "Open the workspace copy to review or edit it."
					: "Another workspace practice already uses this identifier."}
			</AlertDescription>
		</Alert>
	);
}
