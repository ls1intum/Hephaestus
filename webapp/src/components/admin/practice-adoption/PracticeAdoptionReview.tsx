import { Link } from "@tanstack/react-router";
import { CircleAlert, CircleDashed, Copy, ShieldCheck } from "lucide-react";
import type { CatalogPracticePreview, PracticeDefinitionOptions } from "@/api/types.gen";
import { PracticeDefinitionPreview } from "@/components/admin/practice-adoption/PracticeDefinitionPreview";
import { getAreaVisual } from "@/components/admin/practice-catalog/area-visuals";
import { PracticeAutomatedReviewValidationBadge } from "@/components/admin/practice-catalog/PracticeEvidenceSummary";
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
	Card,
	CardContent,
	CardDescription,
	CardFooter,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";
import {
	Item,
	ItemContent,
	ItemDescription,
	ItemGroup,
	ItemMedia,
	ItemTitle,
} from "@/components/ui/item";
import { cn } from "@/lib/utils";

export interface PracticeAdoptionReviewProps {
	workspaceSlug: string;
	preview: CatalogPracticePreview;
	definitionOptions: PracticeDefinitionOptions;
	onAdopt: () => void;
	isPending: boolean;
}

export function PracticeAdoptionReview({
	workspaceSlug,
	preview,
	definitionOptions,
	onAdopt,
	isPending,
}: PracticeAdoptionReviewProps) {
	const unavailable = preview.availability !== "AVAILABLE";

	return (
		<div className="grid items-start gap-8 lg:grid-cols-[minmax(0,1fr)_20rem]">
			<div className="min-w-0 space-y-8">
				<PracticeDefinitionPreview definition={preview.definition} options={definitionOptions} />
				<CatalogDetails preview={preview} />
			</div>

			<aside className="space-y-4 lg:sticky lg:top-6">
				{unavailable && <UnavailableNotice preview={preview} />}
				<Card>
					<CardHeader>
						<CardTitle>{unavailable ? "Workspace status" : "Add to workspace"}</CardTitle>
						<CardDescription>
							{unavailable
								? preview.availability === "ADOPTED"
									? "This workspace owns an editable copy."
									: "This catalog practice cannot be added while its name is in use."
								: "Creates an editable copy owned by this workspace."}
						</CardDescription>
					</CardHeader>
					<CardContent>
						<ItemGroup className="gap-2">
							<CopyOutcome />
							<AreaOutcome preview={preview} />
							<AutonomyOutcome preview={preview} />
						</ItemGroup>
						<div className="mt-4">
							<PracticeAutomatedReviewValidationBadge
								validation={preview.definition.automatedReviewValidation}
							/>
						</div>
					</CardContent>
					<CardFooter className="flex-col gap-2">
						{unavailable ? (
							<Link
								to="/w/$workspaceSlug/admin/practices/$practiceSlug"
								params={{ workspaceSlug, practiceSlug: preview.slug }}
								className={cn(buttonVariants(), "w-full")}
							>
								Open workspace practice
							</Link>
						) : (
							<Button onClick={onAdopt} disabled={isPending} className="w-full">
								<ShieldCheck /> {isPending ? "Adding…" : "Add practice"}
							</Button>
						)}
						<Link
							to="/w/$workspaceSlug/admin/practices"
							params={{ workspaceSlug }}
							search={{ library: true }}
							className={cn(buttonVariants({ variant: "ghost" }), "w-full")}
						>
							Back to practice setup
						</Link>
					</CardFooter>
				</Card>
			</aside>
		</div>
	);
}

function CopyOutcome() {
	return (
		<Item variant="muted" size="sm" role="listitem">
			<ItemMedia variant="icon">
				<Copy />
			</ItemMedia>
			<ItemContent>
				<ItemTitle>Independent copy</ItemTitle>
				<ItemDescription>Edit it without changing the catalog.</ItemDescription>
			</ItemContent>
		</Item>
	);
}

function AreaOutcome({ preview }: { preview: CatalogPracticePreview }) {
	const name = preview.area.definition?.name ?? preview.area.slug ?? "Unassigned";
	const areaVisual = preview.area.slug
		? getAreaVisual(
				preview.area.slug,
				name,
				preview.area.definition?.icon,
				preview.area.definition?.color,
			)
		: { Icon: CircleDashed, pill: "bg-muted text-muted-foreground" };
	const { Icon, pill } = areaVisual;
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
					<AutonomyBadge autonomy={preview.initialAutonomy} />
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
