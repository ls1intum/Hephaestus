import { ChevronRight, CircleAlert } from "lucide-react";
import type { CatalogAreaAdoptionPreview } from "@/api/types.gen";
import { AreaPill } from "@/components/admin/practice-catalog/AreaPill";
import { PracticeListSkeleton } from "@/components/admin/practices/PracticeSkeletons";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { DetailDrawerHeader } from "@/components/core/detail-drawer/DetailDrawerHeader";
import {
	CATALOG_AREA_ACTION_DEFS,
	CATALOG_AREA_CHANGE_ACTIONS,
} from "@/components/practice-vocabulary/catalog-area-action-defs";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { DrawerBody, DrawerDescription, DrawerFooter, DrawerTitle } from "@/components/ui/drawer";
import {
	Item,
	ItemActions,
	ItemContent,
	ItemDescription,
	ItemGroup,
	ItemMedia,
	ItemTitle,
} from "@/components/ui/item";

export type AreaAdoptionState =
	| { status: "loading" }
	| { status: "error"; error: unknown; onRetry: () => void }
	| {
			status: "ready";
			preview: CatalogAreaAdoptionPreview;
			/** `stale` means a conditional add lost its race and the refreshed preview needs re-reading. */
			action: "idle" | "adding" | "stale";
	  };

export interface AreaAdoptionPanelProps {
	state: AreaAdoptionState;
	onConfirm: () => void;
	/** Opens one practice as a level on top of this one. */
	onOpenPractice: (catalogSlug: string) => void;
	nested?: boolean;
}

/**
 * A whole catalog area, with every practice it would touch. Each practice opens *on top of* this
 * panel rather than expanding inside it, so one definition looks the same whether it was reached
 * from the library list or from its area.
 */
export function AreaAdoptionPanel({
	state,
	onConfirm,
	onOpenPractice,
	nested,
}: AreaAdoptionPanelProps) {
	// No area colour before the preview loads: an invented one is indistinguishable from the real one.
	const preview = state.status === "ready" ? state.preview : undefined;
	const changes =
		preview?.actions.filter(({ action }) => CATALOG_AREA_CHANGE_ACTIONS.includes(action)) ?? [];
	const restoring = changes.length > 0 && changes.every(({ action }) => action === "MOVE_TO_AREA");

	return (
		<>
			<DetailDrawerHeader nested={nested}>
				{preview && (
					<AreaPill
						size="lg"
						slug={preview.slug}
						name={preview.definition.name}
						icon={preview.definition.icon}
						color={preview.definition.color}
					/>
				)}
				<div className="min-w-0 flex-1 space-y-0.5">
					<DrawerTitle className="break-words">
						{preview?.definition.name ?? "Practice group"}
					</DrawerTitle>
					{preview && (
						<DrawerDescription>
							{preview.disposition === "CREATE_CATALOG_AREA"
								? "Creates this group in the workspace."
								: "Uses the existing workspace group without changing it."}
						</DrawerDescription>
					)}
				</div>
			</DetailDrawerHeader>

			<DrawerBody className="space-y-6">
				{state.status === "ready" && state.action === "stale" && (
					<Alert variant="warning">
						<CircleAlert />
						<AlertTitle>The catalog changed while you were reading</AlertTitle>
						<AlertDescription>
							This is the current plan for the group. Nothing was added.
						</AlertDescription>
					</Alert>
				)}
				{state.status === "loading" && <PracticeListSkeleton rows={4} />}
				{state.status === "error" && (
					<QueryErrorAlert
						error={state.error}
						title="Couldn't load the current group definition"
						onRetry={state.onRetry}
					/>
				)}
				{state.status === "ready" && (
					<>
						{state.preview.definition.description && (
							<p className="text-sm text-muted-foreground">
								{state.preview.definition.description}
							</p>
						)}
						<ItemGroup>
							{state.preview.practices.map((practice) => {
								const action =
									state.preview.actions.find(({ slug }) => slug === practice.slug)?.action ??
									"KEEP";
								const def = CATALOG_AREA_ACTION_DEFS[action];
								return (
									<div key={practice.slug} role="listitem">
										<Item
											variant="outline"
											render={<button type="button" />}
											onClick={() => onOpenPractice(practice.slug)}
										>
											<ItemMedia variant="icon">
												<def.icon />
											</ItemMedia>
											<ItemContent className="min-w-0 text-left">
												<ItemTitle className="line-clamp-none break-words">
													{practice.definition.name}
												</ItemTitle>
												<ItemDescription className="line-clamp-none">
													{def.description}
												</ItemDescription>
											</ItemContent>
											<ItemActions>
												<StatusBadge def={def} />
												<ChevronRight className="size-4 text-muted-foreground" aria-hidden="true" />
											</ItemActions>
										</Item>
									</div>
								);
							})}
						</ItemGroup>
					</>
				)}
			</DrawerBody>

			{state.status === "ready" && (
				<DrawerFooter>
					<Button onClick={onConfirm} disabled={changes.length === 0 || state.action === "adding"}>
						{state.action === "adding"
							? "Adding…"
							: restoring
								? "Restore group"
								: `Add ${changes.length} ${changes.length === 1 ? "practice" : "practices"}`}
					</Button>
				</DrawerFooter>
			)}
		</>
	);
}
