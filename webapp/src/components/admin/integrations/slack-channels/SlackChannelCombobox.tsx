import { ChevronsUpDownIcon, LockIcon } from "lucide-react";
import { type ReactNode, useState } from "react";
import type { SlackChannelCandidate } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Command,
	CommandEmpty,
	CommandGroup,
	CommandInput,
	CommandItem,
	CommandList,
} from "@/components/ui/command";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
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

/**
 * The single control for choosing a Slack channel: a combobox (Popover + Command) whose trigger
 * shows the human `#channel-name` and whose value — the stable Slack id — is kept in state and
 * never surfaced as editable text. Search, roving keyboard focus and a marked selection come
 * from cmdk; disabled options keep a visible reason instead of vanishing from the list.
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
	const [open, setOpen] = useState(false);

	const selected = candidates.find((candidate) => candidate.slackChannelId === selectedChannelId);
	const selectedLabel = selected?.channelName ?? selectedChannelName;

	return (
		<Popover open={open} onOpenChange={setOpen}>
			<PopoverTrigger
				render={
					<Button
						id={id}
						type="button"
						variant="outline"
						role="combobox"
						aria-expanded={open}
						aria-invalid={invalid}
						disabled={disabled}
						className={cn("w-full justify-between font-normal", className)}
					/>
				}
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
				<ChevronsUpDownIcon className="size-4 shrink-0 opacity-50" aria-hidden />
			</PopoverTrigger>

			<PopoverContent align="start" className="w-(--anchor-width) min-w-72 p-0">
				{/* cmdk renders a visually-hidden <label> internally and points the search input's
				    aria-labelledby at it (which wins accessible-name computation over a plain
				    aria-label on the input) — so the name has to be set here, via `label`. */}
				<Command label={ariaLabel}>
					<CommandInput placeholder="Search channels…" />
					<CommandList>
						<CommandEmpty>No channels found.</CommandEmpty>
						<CommandGroup>
							{candidates.map((candidate) => {
								const disabledReason = getDisabledReason?.(candidate);
								const isDisabled = disabledReason != null;
								const isSelected = selectedChannelId === candidate.slackChannelId;
								return (
									<CommandItem
										key={candidate.slackChannelId}
										value={`${candidate.channelName} ${candidate.slackChannelId}`}
										disabled={isDisabled}
										data-checked={isSelected}
										onSelect={() => {
											if (isDisabled) return;
											onSelect(candidate);
											setOpen(false);
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
												{isSelected && <span className="sr-only">, selected</span>}
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
			</PopoverContent>
		</Popover>
	);
}
