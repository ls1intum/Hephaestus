import { ChevronRight, CircleAlert, CircleCheck, CornerDownLeft, Plus } from "lucide-react";
import type { CatalogAreaAdoptionPreview, CatalogAreaPracticeAction } from "@/api/types.gen";
import { getAreaVisual } from "@/components/admin/practice-catalog/area-visuals";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { DetailDrawerPanel } from "@/components/core/detail-drawer/DetailDrawerPanel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Item,
	ItemActions,
	ItemContent,
	ItemDescription,
	ItemGroup,
	ItemMedia,
	ItemTitle,
} from "@/components/ui/item";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";

/**
 * What adding the area does to each of its practices. Every row states its own outcome, so the
 * panel needs no prose explaining which of four lists a practice ended up in.
 */
const AREA_ACTION_DEFS = {
	ADD: { icon: Plus, label: "Adds", description: "A new copy this workspace owns." },
	MOVE_TO_AREA: {
		icon: CornerDownLeft,
		label: "Moves back",
		description: "An unassigned copy returns here. Local edits are kept.",
	},
	KEEP: { icon: CircleCheck, label: "Already here", description: "Nothing changes." },
	BLOCKED: {
		icon: CircleAlert,
		label: "Blocked",
		description: "Another practice already uses this name.",
	},
} as const satisfies Record<CatalogAreaPracticeAction["action"], unknown>;

export interface AreaAdoptionPanelProps {
	preview?: CatalogAreaAdoptionPreview;
	isLoading: boolean;
	isError: boolean;
	error?: unknown;
	isPending: boolean;
	onRetry: () => void;
	onConfirm: () => void;
	/** Opens one practice as a level on top of this one. */
	onOpenPractice: (catalogSlug: string) => void;
}

/**
 * A whole catalog area, with every practice it would touch. Each practice opens on top of this
 * panel rather than expanding inside it, so the same definition looks the same whether it was
 * reached from the library list or from its area.
 */
export function AreaAdoptionPanel({
	preview,
	isLoading,
	isError,
	error,
	isPending,
	onRetry,
	onConfirm,
	onOpenPractice,
}: AreaAdoptionPanelProps) {
	const actionBySlug = new Map(preview?.actions.map(({ slug, action }) => [slug, action]) ?? []);
	const changeCount =
		preview?.practices.filter((practice) => {
			const action = actionBySlug.get(practice.slug);
			return action === "ADD" || action === "MOVE_TO_AREA";
		}).length ?? 0;
	const restoring =
		changeCount > 0 &&
		preview?.practices.every((practice) => actionBySlug.get(practice.slug) !== "ADD") === true;
	const { Icon, pill } = getAreaVisual(
		preview?.slug ?? "",
		preview?.definition.name ?? "",
		preview?.definition.icon,
		preview?.definition.color,
	);

	return (
		<DetailDrawerPanel
			title={preview?.definition.name ?? "Area"}
			description={
				preview
					? preview.disposition === "CREATE_CATALOG_AREA"
						? "Creates this area in the workspace."
						: "Uses the existing workspace area without changing it."
					: undefined
			}
			media={
				<ItemMedia className={cn("size-9 rounded-md", pill)} aria-hidden="true">
					<Icon className="size-4" />
				</ItemMedia>
			}
			footer={
				preview && (
					<Button onClick={onConfirm} disabled={changeCount === 0 || isPending}>
						{isPending
							? "Adding…"
							: restoring
								? "Restore area"
								: `Add ${changeCount} ${changeCount === 1 ? "practice" : "practices"}`}
					</Button>
				)
			}
		>
			{isLoading ? (
				<div className="flex min-h-32 items-center justify-center" role="status">
					<Spinner />
					<span className="sr-only">Loading area preview</span>
				</div>
			) : isError ? (
				<QueryErrorAlert
					error={error}
					title="Couldn't load the current area definition"
					onRetry={onRetry}
				/>
			) : preview ? (
				<>
					{preview.definition.description && (
						<p className="text-sm text-muted-foreground">{preview.definition.description}</p>
					)}
					<ItemGroup>
						{preview.practices.map((practice) => {
							const action = actionBySlug.get(practice.slug) ?? "KEEP";
							const { icon: ActionIcon, label, description } = AREA_ACTION_DEFS[action];
							return (
								<div key={practice.slug} role="listitem">
									<Item
										variant="outline"
										render={<button type="button" />}
										onClick={() => onOpenPractice(practice.slug)}
										aria-label={`${practice.definition.name}, ${label.toLowerCase()}`}
									>
										<ItemMedia variant="icon">
											<ActionIcon />
										</ItemMedia>
										<ItemContent className="min-w-0 text-left">
											<ItemTitle className="line-clamp-none break-words">
												{practice.definition.name}
											</ItemTitle>
											<ItemDescription className="line-clamp-none">{description}</ItemDescription>
										</ItemContent>
										<ItemActions>
											<Badge variant={action === "BLOCKED" ? "outline" : "secondary"}>
												{label}
											</Badge>
											<ChevronRight className="size-4 text-muted-foreground" aria-hidden="true" />
										</ItemActions>
									</Item>
								</div>
							);
						})}
					</ItemGroup>
				</>
			) : null}
		</DetailDrawerPanel>
	);
}
