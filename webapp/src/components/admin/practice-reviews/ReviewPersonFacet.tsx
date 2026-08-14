import { useQuery } from "@tanstack/react-query";
import { UserRoundIcon } from "lucide-react";
import { listMembersOptions } from "@/api/@tanstack/react-query.gen";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Combobox,
	ComboboxContent,
	ComboboxEmpty,
	ComboboxItem,
	ComboboxItemIndicator,
	ComboboxList,
	ComboboxSearchInput,
	ComboboxSeparator,
	ComboboxTrigger,
	useComboboxFilter,
} from "@/components/ui/combobox";
import { Separator } from "@/components/ui/separator";
import { getInitials } from "@/lib/avatar";

/** The workspace's members are the only people who can be the subject of a review. */
const MEMBER_PAGE_SIZE = 100;

interface PersonOption {
	userId: number;
	label: string;
	secondary?: string;
}

export interface ReviewPersonFacetProps {
	workspaceSlug: string;
	/** "Developer" on Observations, "Recipient" on Delivery — the two are not always the same person. */
	title: string;
	selected: number | undefined;
	onChange: (userId: number | undefined) => void;
	/**
	 * The person already on screen, so a filter arrived at by link shows a name rather than an id
	 * before the member list loads — and still shows one if the person has since left the workspace.
	 */
	fallbackName?: string;
}

/**
 * Filter a review list down to one person.
 *
 * <p>Both list screens already understood a person filter: `subjectUserId` and `recipientUserId`
 * were in the search schema, the queries sent them, and an applied one rendered as a pill you could
 * clear. There was simply no control that could set one — the only way in was a link from somewhere
 * else, which meant "show me the delivery for one person" was a question the screen could answer and
 * an operator could not ask. Anything that can appear as an applied-filter pill has to be settable in
 * place; this is that control.
 *
 * <p>Single-select rather than multi, because the search schema carries one id and the API takes one.
 * A multi-select trigger that silently kept only the last choice would be a control that lies about
 * what it did.
 */
export function ReviewPersonFacet({
	workspaceSlug,
	title,
	selected,
	onChange,
	fallbackName,
}: ReviewPersonFacetProps) {
	const membersQuery = useQuery({
		...listMembersOptions({ path: { workspaceSlug }, query: { size: MEMBER_PAGE_SIZE } }),
	});
	const { contains } = useComboboxFilter({ sensitivity: "base" });
	const options: PersonOption[] = (membersQuery.data ?? [])
		.filter((member): member is typeof member & { userId: number } => member.userId != null)
		.map((member) => ({
			userId: member.userId,
			label: member.userName || member.userLogin || `#${member.userId}`,
			secondary: member.userName && member.userLogin ? member.userLogin : undefined,
		}));
	const selectedOption =
		options.find((option) => option.userId === selected) ??
		(selected != null ? { userId: selected, label: fallbackName ?? `#${selected}` } : null);

	return (
		<Combobox
			items={options}
			value={selectedOption}
			isItemEqualToValue={(option: PersonOption, value: PersonOption) =>
				option.userId === value.userId
			}
			onValueChange={(next: PersonOption | null) => onChange(next?.userId)}
			filter={(option: PersonOption, query) =>
				contains(option, query, (o) => (o.secondary ? `${o.label} ${o.secondary}` : o.label))
			}
			itemToStringLabel={(option: PersonOption) => option.label}
			disabled={membersQuery.isLoading}
		>
			<ComboboxTrigger
				type="button"
				disabled={membersQuery.isLoading}
				aria-label={selectedOption ? `${title}: ${selectedOption.label}` : title}
				className="h-8 max-w-full border-dashed font-normal"
			>
				<UserRoundIcon aria-hidden />
				{title}
				{selectedOption && (
					<>
						<Separator orientation="vertical" className="mx-0.5 data-[orientation=vertical]:h-4" />
						<Badge variant="secondary" className="max-w-36 rounded-sm px-1 font-normal">
							<span className="truncate">{selectedOption.label}</span>
						</Badge>
					</>
				)}
			</ComboboxTrigger>

			<ComboboxContent align="start" className="min-w-64" aria-label={`${title} filter`}>
				<ComboboxSearchInput
					placeholder="Search people…"
					aria-label={`Search ${title.toLowerCase()} options`}
				/>
				<ComboboxEmpty>
					{membersQuery.isError
						? "Could not load people"
						: options.length === 0
							? "No people in this workspace"
							: "No matches"}
				</ComboboxEmpty>
				{/* The list carries the name, not just the popup: in single-select mode Base UI puts
				    `role="listbox"` on this element, and an unnamed listbox fails axe. */}
				<ComboboxList aria-label={`${title} options`}>
					{(option: PersonOption) => (
						<ComboboxItem key={option.userId} value={option}>
							<ComboboxItemIndicator />
							<Avatar className="size-5 shrink-0">
								<AvatarFallback className="text-[0.625rem]">
									{getInitials(option.label)}
								</AvatarFallback>
							</Avatar>
							<span className="min-w-0 truncate">
								{option.label}
								{option.secondary && (
									<span className="ml-1.5 text-xs text-muted-foreground">{option.secondary}</span>
								)}
							</span>
						</ComboboxItem>
					)}
				</ComboboxList>
				{selectedOption && (
					<>
						<ComboboxSeparator />
						<Button
							variant="ghost"
							size="sm"
							className="h-8 w-full font-normal"
							onClick={() => onChange(undefined)}
						>
							Clear selection
						</Button>
					</>
				)}
			</ComboboxContent>
		</Combobox>
	);
}
