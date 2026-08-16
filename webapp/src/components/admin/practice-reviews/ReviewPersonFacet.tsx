import { UserRoundIcon } from "lucide-react";
import type { FacetSource } from "@/components/common/FacetMultiSelect";
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

/**
 * The members endpoint takes `page` and `size` and nothing else — no query, no name filter — so the
 * search box here filters what has already arrived and cannot reach anyone past this page. A larger
 * workspace has people who are unselectable *and* whom the search answers "No matches" for, which
 * reads as "that person does not exist here", so the popover states the limit rather than hiding it.
 *
 * <p>Exported because `useReviewPeople`, which does the fetching, has to ask for exactly the page
 * this sentence promises.
 */
export const MEMBER_PAGE_SIZE = 100;

export interface PersonOption {
	userId: number;
	label: string;
	secondary?: string;
}

/**
 * The member list this facet offers, already reduced to what it draws. Whoever fetches it —
 * `useReviewPeople` in the app, a fixture in a story — owes the same four fields, so the facet never
 * learns where its people came from.
 *
 * A `FacetSource` and one field more, by extension rather than by resemblance: the two used to be
 * declared separately with a comment claiming they were the same shape, and they were not — the
 * flags were optional on one and required on the other.
 */
export interface ReviewPeople extends FacetSource<PersonOption> {
	/**
	 * The list is one page long and that page came back full, so there are probably people missing.
	 * The endpoint returns a bare array with no total, so a full page is the only signal there is.
	 */
	capped: boolean;
}

export interface ReviewPersonFacetProps {
	/** "Developer" on Observations, "Recipient" on Delivery — the two are not always the same person. */
	title: string;
	people: ReviewPeople;
	selected: number | undefined;
	onChange: (userId: number | undefined) => void;
	/**
	 * The name of the person `selected` identifies, shown before the member list loads and for a
	 * person who has since left the workspace. It must name *that* person: a caller reading it off the
	 * first row of the list it filters is right only while the filter is on, and otherwise labels one
	 * person's id with another's name.
	 */
	fallbackName?: string;
}

/**
 * Single-select rather than multi, because the search schema carries one id and the API takes one: a
 * multi-select trigger that silently kept only the last choice would lie about what it did.
 */
export function ReviewPersonFacet({
	title,
	people,
	selected,
	onChange,
	fallbackName,
}: ReviewPersonFacetProps) {
	const { contains } = useComboboxFilter({ sensitivity: "base" });
	const { options, capped } = people;
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
			disabled={people.isLoading}
		>
			<ComboboxTrigger
				type="button"
				disabled={people.isLoading}
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
					{people.isError
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
				{capped && (
					<p className="border-t px-2 py-1.5 text-xs text-muted-foreground">
						Showing the first {MEMBER_PAGE_SIZE} members. Search looks only at these — to filter by
						someone further down the list, open their work and follow the link from a row.
					</p>
				)}
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
