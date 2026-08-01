import type { ReactNode } from "react";
import type { CatalogEntryStatus } from "@/api/types.gen";
import { Item, ItemActions, ItemContent, ItemDescription, ItemTitle } from "@/components/ui/item";
import { Spinner } from "@/components/ui/spinner";
import { Switch } from "@/components/ui/switch";
import { CuratedEntryBadges } from "./CuratedEntryBadges";

export interface CuratedEntryRowProps {
	name: string;
	kind: "practice" | "area";
	status: CatalogEntryStatus;
	/** The name, wrapped in whatever navigates to the editor. */
	title: ReactNode;
	/** One line under the name — the work type for a practice, the practice count for an area. */
	meta?: ReactNode;
	pending: boolean;
	onOfferedChange: (offered: boolean) => void;
	/** The kebab, so each caller can add the actions only it has. */
	actions: ReactNode;
}

/**
 * One practice in the catalog, read and acted on in a single line. Areas are the accordion headers
 * these rows sit under, so they are laid out there rather than here — but they say the same things
 * in the same order, with the same controls.
 *
 * <p>The switch is the offered state rather than a control beside it: whether a new workspace would
 * receive this entry is the one axis an administrator changes from a list, and reading it and
 * changing it should not be two different places to look.
 */
export function CuratedEntryRow({
	name,
	kind,
	status,
	title,
	meta,
	pending,
	onOfferedChange,
	actions,
}: CuratedEntryRowProps) {
	return (
		// ItemGroup is role="list"; a list may only contain listitems, and this row holds a switch,
		// a menu button and a link.
		<Item role="listitem" size="xs" className="flex-nowrap hover:bg-muted/60">
			<ItemContent className="min-w-0">
				<ItemTitle className="w-full min-w-0 line-clamp-none">{title}</ItemTitle>
				<ItemDescription className="flex flex-wrap items-center gap-1.5">
					{meta}
					<CuratedEntryBadges status={status} kind={kind} />
				</ItemDescription>
			</ItemContent>
			<ItemActions className="ml-auto">
				{pending && <Spinner className="size-4 text-muted-foreground" />}
				<Switch
					className="hidden sm:inline-flex"
					checked={status.offered}
					onCheckedChange={onOfferedChange}
					disabled={pending}
					aria-busy={pending}
					aria-label={`Offer ${name} to new workspaces`}
				/>
				{actions}
			</ItemActions>
		</Item>
	);
}
