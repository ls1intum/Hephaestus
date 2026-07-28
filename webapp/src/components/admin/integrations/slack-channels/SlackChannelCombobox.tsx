import { ChevronsUpDownIcon, LockIcon } from "lucide-react";
import type { ReactNode } from "react";
import type { SlackChannelCandidate } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import {
	Combobox,
	ComboboxContent,
	ComboboxEmpty,
	ComboboxIcon,
	ComboboxItem,
	ComboboxItemIndicator,
	ComboboxList,
	ComboboxSearchInput,
	ComboboxTrigger,
	useComboboxFilter,
} from "@/components/ui/combobox";
import { cn } from "@/lib/utils";

export interface SlackChannelComboboxProps {
	/** Wired to the Field's label via `htmlFor`, so the trigger carries the field's name. */
	id?: string;
	candidates: SlackChannelCandidate[];
	/** The chosen stable Slack id. Held in state, shown only as a resolved `#channel-name`. */
	selectedChannelId?: string;
	/** Name for a selected id that Slack never listed (e.g. one pasted from a link). */
	selectedChannelName?: string;
	onSelect: (candidate: SlackChannelCandidate) => void;
	disabled?: boolean;
	invalid?: boolean;
	/** Per-candidate disable reason (e.g. "Needs invite"). Present ⇒ the option is disabled. */
	getDisabledReason?: (candidate: SlackChannelCandidate) => string | undefined;
	/** Extra, non-disabling informational badges (e.g. "Revoked"). */
	renderBadges?: (candidate: SlackChannelCandidate) => ReactNode;
	/** Shown on the trigger while nothing is selected. */
	placeholder?: string;
	className?: string;
	/** Accessible name for the search input inside the popup. */
	"aria-label"?: string;
}

/** Search matches the human name and the stable id, so a pasted id still finds its channel. */
function searchTextOf(candidate: SlackChannelCandidate) {
	return `${candidate.channelName} ${candidate.slackChannelId}`;
}

/**
 * The single control for choosing a Slack channel: a Base UI Combobox whose trigger shows the human
 * `#channel-name` and whose value — the stable Slack id — is kept in state and never surfaced as
 * editable text. Search, the roving highlight and the marked selection come from the primitive, so
 * the popup keyboards and paints like every other Base UI list in the app; disabled options keep a
 * visible reason instead of vanishing from the list.
 *
 * Shared by the add-channel dialog and the weekly-digest field so the two cannot drift.
 */
export function SlackChannelCombobox({
	id,
	candidates,
	selectedChannelId,
	selectedChannelName,
	onSelect,
	disabled = false,
	invalid = false,
	getDisabledReason,
	renderBadges,
	placeholder = "Select a channel…",
	className,
	"aria-label": ariaLabel = "Search Slack channels",
}: SlackChannelComboboxProps) {
	const { contains } = useComboboxFilter({ sensitivity: "base" });

	const selected = candidates.find((candidate) => candidate.slackChannelId === selectedChannelId);
	const selectedLabel = selected?.channelName ?? selectedChannelName;

	return (
		<Combobox
			items={candidates}
			value={selected ?? null}
			onValueChange={(candidate) => {
				if (candidate) onSelect(candidate);
			}}
			disabled={disabled}
			filter={(candidate, query) => contains(candidate, query, searchTextOf)}
			itemToStringLabel={(candidate) => candidate.channelName}
		>
			<ComboboxTrigger
				id={id}
				aria-invalid={invalid}
				className={cn("w-full justify-between font-normal", className)}
			>
				{selectedLabel ? (
					<span className="truncate">#{selectedLabel}</span>
				) : selectedChannelId ? (
					// A channel Slack never listed and that carried no name — the id is the only
					// truthful thing we can call it, so it is labelled rather than passed off as a name.
					<span className="text-muted-foreground truncate">
						Channel <span className="font-mono">{selectedChannelId}</span>
					</span>
				) : (
					<span className="text-muted-foreground truncate">{placeholder}</span>
				)}
				<ComboboxIcon render={<ChevronsUpDownIcon className="size-4 shrink-0 opacity-50" />} />
			</ComboboxTrigger>

			<ComboboxContent align="start" className="min-w-72">
				<ComboboxSearchInput placeholder="Search channels…" aria-label={ariaLabel} />
				<ComboboxEmpty>No channels found.</ComboboxEmpty>
				<ComboboxList>
					{(candidate: SlackChannelCandidate) => {
						const disabledReason = getDisabledReason?.(candidate);
						return (
							<ComboboxItem
								key={candidate.slackChannelId}
								value={candidate}
								disabled={disabledReason != null}
							>
								<div className="min-w-0 flex-1">
									<div className="flex flex-wrap items-center gap-2">
										<span className="truncate font-medium">#{candidate.channelName}</span>
										{candidate.privateChannel && (
											<LockIcon className="size-3.5" role="img" aria-label="Private" />
										)}
										{renderBadges?.(candidate)}
										{disabledReason && <Badge variant="outline">{disabledReason}</Badge>}
									</div>
									<div className="text-muted-foreground font-mono text-xs">
										{candidate.slackChannelId}
									</div>
								</div>
								<ComboboxItemIndicator />
							</ComboboxItem>
						);
					}}
				</ComboboxList>
			</ComboboxContent>
		</Combobox>
	);
}
