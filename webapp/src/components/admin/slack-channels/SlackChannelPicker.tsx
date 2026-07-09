import { LockIcon } from "lucide-react";
import type { ReactNode } from "react";
import type { SlackChannelCandidate } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import {
	Command,
	CommandEmpty,
	CommandGroup,
	CommandInput,
	CommandItem,
	CommandList,
} from "@/components/ui/command";
import { cn } from "@/lib/utils";

export interface SlackChannelPickerProps {
	candidates: SlackChannelCandidate[];
	selectedChannelId?: string;
	onSelect: (candidate: SlackChannelCandidate) => void;
	disabled?: boolean;
	/** Per-candidate disable reason (e.g. "Needs invite"). Present ⇒ the item is disabled. */
	getDisabledReason?: (candidate: SlackChannelCandidate) => string | undefined;
	/** Extra, non-disabling informational badges (e.g. "Already listed", "Revoked"). */
	renderBadges?: (candidate: SlackChannelCandidate) => ReactNode;
	className?: string;
	/** Accessible name for the search input (the Field's own label describes the group). */
	"aria-label"?: string;
}

/**
 * Searchable single-select Slack channel combobox (cmdk-backed): filter input, roving
 * keyboard focus between options, and a visibly (+ SR-announced) marked selection. Shared by
 * the add-channel dialog and the weekly-digest channel field so both get the same a11y and
 * search behavior instead of a scrollable list of `aria-pressed` buttons.
 */
export function SlackChannelPicker({
	candidates,
	selectedChannelId,
	onSelect,
	disabled = false,
	getDisabledReason,
	renderBadges,
	className,
	"aria-label": ariaLabel = "Search Slack channels",
}: SlackChannelPickerProps) {
	return (
		// cmdk renders a visually-hidden <label> internally and points the search input's
		// aria-labelledby at it (which wins accessible-name computation over a plain aria-label
		// on the input itself) — so the accessible name has to be set here, via `label`.
		<Command className={cn("rounded-lg border", className)} label={ariaLabel}>
			<CommandInput placeholder="Search channels…" disabled={disabled} />
			<CommandList>
				<CommandEmpty>No channels found.</CommandEmpty>
				<CommandGroup>
					{candidates.map((candidate) => {
						const disabledReason = getDisabledReason?.(candidate);
						const isDisabled = disabled || disabledReason != null;
						const selected = selectedChannelId === candidate.slackChannelId;
						return (
							<CommandItem
								key={candidate.slackChannelId}
								value={`${candidate.channelName} ${candidate.slackChannelId}`}
								disabled={isDisabled}
								data-checked={selected}
								onSelect={() => {
									if (isDisabled) return;
									onSelect(candidate);
								}}
							>
								<div className="min-w-0 flex-1">
									<div className="flex flex-wrap items-center gap-2">
										<span className="truncate font-medium">#{candidate.channelName}</span>
										{candidate.privateChannel && (
											<LockIcon className="size-3.5" role="img" aria-label="Private" />
										)}
										{renderBadges?.(candidate)}
										{disabledReason && <Badge variant="outline">{disabledReason}</Badge>}
										{selected && <span className="sr-only">, selected</span>}
									</div>
									<div className="text-muted-foreground font-mono text-xs">
										{candidate.slackChannelId}
									</div>
								</div>
							</CommandItem>
						);
					})}
				</CommandGroup>
			</CommandList>
		</Command>
	);
}
