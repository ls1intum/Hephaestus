import { Link } from "@tanstack/react-router";
import { CircleAlert, Eye, ShieldCheck } from "lucide-react";
import type { CatalogPracticePreview, PracticeDefinitionOptions } from "@/api/types.gen";
import { PracticeDefinitionPreview } from "@/components/admin/practice-adoption/PracticeDefinitionPreview";
import { getAreaVisual } from "@/components/admin/practice-catalog/area-visuals";
import { AUTONOMY_DEFS } from "@/components/practice-vocabulary/autonomy-defs";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

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
	const initialAutonomy = AUTONOMY_DEFS[preview.initialAutonomy];
	return (
		<div className="space-y-6">
			{!unavailable && (
				<Alert variant="success">
					<Eye />
					<AlertTitle>Starts with {initialAutonomy.label}</AlertTitle>
					<AlertDescription>
						{initialAutonomy.description} Adoption never authorizes automatic sending.
					</AlertDescription>
				</Alert>
			)}

			<Card>
				<CardHeader>
					<CardTitle>
						<h2>Adoption outcome</h2>
					</CardTitle>
				</CardHeader>
				<CardContent className="space-y-4">
					<dl className="grid gap-3 sm:grid-cols-2">
						<div>
							<dt className="font-medium">Catalog source</dt>
							<dd className="mt-1 break-all text-muted-foreground">
								<code>{preview.slug}</code>
							</dd>
						</div>
						{preview.area.disposition === "CREATE_CATALOG_AREA" && preview.area.definition && (
							<div className="sm:col-span-2">
								<dt className="font-medium">Incoming area definition</dt>
								<dd className="mt-1 space-y-1 text-muted-foreground">
									<p>{preview.area.definition.name}</p>
									{preview.area.definition.description && (
										<p className="whitespace-pre-wrap">{preview.area.definition.description}</p>
									)}
									<AreaVisual preview={preview} />
								</dd>
							</div>
						)}
						<div>
							<dt className="font-medium">Source review-rule fingerprint</dt>
							<dd className="mt-1 break-all text-muted-foreground">
								<code>{preview.sourceReviewRuleFingerprint}</code>
							</dd>
						</div>
						<div>
							<dt className="font-medium">Workspace area</dt>
							<dd className="mt-1 text-muted-foreground">{areaOutcome(preview)}</dd>
						</div>
					</dl>
				</CardContent>
			</Card>

			<PracticeDefinitionPreview definition={preview.definition} options={definitionOptions} />

			{unavailable && (
				<Alert variant="warning">
					<CircleAlert />
					<AlertTitle>
						{preview.availability === "ADOPTED" ? "Already adopted" : "Slug conflict"}
					</AlertTitle>
					<AlertDescription>
						{preview.availability === "ADOPTED"
							? "This workspace already owns a copy of this catalog practice. Catalog changes never replace it."
							: "An unrelated workspace practice already uses this slug. Rename or remove it before adopting."}
					</AlertDescription>
				</Alert>
			)}

			<div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
				<Button
					variant="outline"
					nativeButton={false}
					render={
						<Link to="/w/$workspaceSlug/admin/practices/available" params={{ workspaceSlug }} />
					}
				>
					Back to available practices
				</Button>
				{unavailable && (
					<Button
						variant="outline"
						nativeButton={false}
						render={
							<Link
								to="/w/$workspaceSlug/admin/practices/$practiceSlug"
								params={{ workspaceSlug, practiceSlug: preview.slug }}
							/>
						}
					>
						Inspect workspace practice
					</Button>
				)}
				<Button onClick={onAdopt} disabled={unavailable || isPending} className="min-w-36">
					<ShieldCheck /> {isPending ? "Adopting…" : "Adopt practice"}
				</Button>
			</div>
		</div>
	);
}

function AreaVisual({ preview }: { preview: CatalogPracticePreview }) {
	const definition = preview.area.definition;
	if (!definition) return null;
	const { Icon, pill } = getAreaVisual(
		preview.area.slug ?? preview.slug,
		definition.name,
		definition.icon,
		definition.color,
	);
	return (
		<span className={`inline-flex items-center gap-1.5 rounded-full px-2 py-1 text-sm ${pill}`}>
			<Icon className="size-4" aria-hidden="true" />
			{definition.name}
		</span>
	);
}

function areaOutcome(preview: CatalogPracticePreview): string {
	if (preview.area.disposition === "UNASSIGNED") return "Leave unassigned";
	const areaName = preview.area.definition?.name ?? preview.area.slug ?? "the catalog area";
	if (preview.area.disposition === "REUSE_EXISTING_AREA") {
		return `Reuse existing area “${areaName}” without changing it`;
	}
	return `Create area “${areaName}”`;
}
