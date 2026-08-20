import type { CatalogAreaAdoptionPreview, PracticeDefinitionOptions } from "@/api/types.gen";
import { PracticeDefinitionPreview } from "@/components/admin/practice-adoption/PracticeDefinitionPreview";
import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { Spinner } from "@/components/ui/spinner";

export interface CatalogAreaAdoptionDialogProps {
	open: boolean;
	preview?: CatalogAreaAdoptionPreview;
	isLoading: boolean;
	isError: boolean;
	isPending: boolean;
	onOpenChange: (open: boolean) => void;
	onRetry: () => void;
	onConfirm: () => void;
	definitionOptions: PracticeDefinitionOptions;
}

export function CatalogAreaAdoptionDialog({
	open,
	preview,
	isLoading,
	isError,
	isPending,
	onOpenChange,
	onRetry,
	onConfirm,
	definitionOptions,
}: CatalogAreaAdoptionDialogProps) {
	const actionBySlug = new Map(preview?.actions.map(({ slug, action }) => [slug, action]) ?? []);
	const additions =
		preview?.practices.filter((practice) => actionBySlug.get(practice.slug) === "ADD") ?? [];
	const moves =
		preview?.practices.filter((practice) => actionBySlug.get(practice.slug) === "MOVE_TO_AREA") ??
		[];
	const kept =
		preview?.practices.filter((practice) => actionBySlug.get(practice.slug) === "KEEP") ?? [];
	const blocked =
		preview?.practices.filter((practice) => actionBySlug.get(practice.slug) === "BLOCKED") ?? [];
	const changeCount = additions.length + moves.length;

	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			<DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
				<DialogHeader>
					<DialogTitle>Add an area from the library</DialogTitle>
					<DialogDescription>
						Review every practice that will be added. The resulting copies belong to this workspace
						and remain independently editable.
					</DialogDescription>
				</DialogHeader>
				{isLoading ? (
					<div className="flex min-h-32 items-center justify-center" role="status">
						<Spinner /> <span className="sr-only">Loading area preview</span>
					</div>
				) : isError ? (
					<div className="space-y-3 rounded-lg border p-4">
						<p>We couldn't load the current area definition.</p>
						<Button variant="outline" onClick={onRetry}>
							Try again
						</Button>
					</div>
				) : preview ? (
					<div className="space-y-5">
						<div className="rounded-lg bg-muted/50 p-4">
							<h3 className="font-medium">{preview.definition.name}</h3>
							{preview.definition.description && (
								<p className="mt-1 text-sm text-muted-foreground">
									{preview.definition.description}
								</p>
							)}
							<p className="mt-2 text-sm">
								{preview.disposition === "CREATE_CATALOG_AREA"
									? "Creates this area in the workspace."
									: "Uses the existing workspace area without changing it."}
							</p>
						</div>
						{additions.length > 0 && (
							<section aria-labelledby="area-practices-to-add" className="space-y-2">
								<h3 id="area-practices-to-add" className="font-medium">
									Practices to add <Badge variant="secondary">{additions.length}</Badge>
								</h3>
								<Accordion className="rounded-lg border" aria-label="Practices to add">
									{additions.map((practice) => (
										<AccordionItem key={practice.slug} value={practice.slug}>
											<AccordionTrigger className="px-3">
												{practice.definition.name}
											</AccordionTrigger>
											<AccordionContent className="px-3 pb-4">
												<PracticeDefinitionPreview
													definition={practice.definition}
													options={definitionOptions}
													idPrefix={`area-${preview.slug}-${practice.slug}`}
												/>
											</AccordionContent>
										</AccordionItem>
									))}
								</Accordion>
							</section>
						)}
						{moves.length > 0 && (
							<section aria-labelledby="area-practices-to-move" className="space-y-2">
								<h3 id="area-practices-to-move" className="font-medium">
									Practices to move <Badge variant="secondary">{moves.length}</Badge>
								</h3>
								<p className="text-sm text-muted-foreground">
									These workspace practices are currently unassigned. Restoring the area puts them
									back without replacing their local changes.
								</p>
								<ul className="divide-y rounded-lg border">
									{moves.map((practice) => (
										<li key={practice.slug} className="p-3 text-sm font-medium">
											{practice.definition.name}
										</li>
									))}
								</ul>
							</section>
						)}
						{(kept.length > 0 || blocked.length > 0) && (
							<section aria-labelledby="area-practices-not-added" className="space-y-2">
								<h3 id="area-practices-not-added" className="font-medium">
									Not changed
								</h3>
								<ul className="space-y-2 text-sm">
									{kept.map((practice) => (
										<li key={practice.slug}>{practice.definition.name} — already in this area</li>
									))}
									{blocked.map((practice) => (
										<li key={practice.slug}>{practice.definition.name} — name unavailable</li>
									))}
								</ul>
							</section>
						)}
					</div>
				) : null}
				<DialogFooter>
					<Button variant="outline" onClick={() => onOpenChange(false)} disabled={isPending}>
						Cancel
					</Button>
					<Button onClick={onConfirm} disabled={!preview || changeCount === 0 || isPending}>
						{isPending
							? "Adding…"
							: moves.length > 0 && additions.length === 0
								? "Restore area"
								: `Apply ${changeCount} ${changeCount === 1 ? "change" : "changes"}`}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
}
