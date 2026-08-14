import { ArrowDownWideNarrowIcon } from "lucide-react";
import { useId } from "react";
import { Field, FieldLabel } from "@/components/ui/field";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import type { ObservationSort } from "./review-search";

/**
 * The two orderings the endpoint offers, in the operator's words rather than the enum's.
 *
 * "Most actionable first" is `ACTIONABILITY`: shortfalls first, critical down to informational, then
 * strengths, then the observations that judged nothing. The words say what arrives at the top,
 * because that is the only part of the ordering a reader of the first screenful can check.
 */
const SORT_ITEMS: { value: ObservationSort; label: string }[] = [
	{ value: "NEWEST", label: "Newest first" },
	{ value: "ACTIONABILITY", label: "Most actionable first" },
];

export interface ObservationSortSelectProps {
	value: ObservationSort | undefined;
	onChange: (sort: ObservationSort | undefined) => void;
}

/**
 * How the observations list is ordered.
 *
 * <p>A list gives up the one thing a table hands you for nothing — sorting a column (Baymard, "Use
 * Product Tables for Desktop Product Listings") — so a list has to offer the ordering explicitly.
 * The server has ordered by actionability since this endpoint shipped and no screen ever asked for
 * it: "show me the worst thing first" was unanswerable on the screen that exists to answer it.
 *
 * <p>`NEWEST` is reported as `undefined`, not as the string: it is the server's default, so writing
 * it into the URL would put a parameter in every link that changes nothing.
 */
export function ObservationSortSelect({ value, onChange }: ObservationSortSelectProps) {
	const sortId = useId();
	return (
		<Field orientation="horizontal" className="w-auto max-w-full flex-wrap text-sm">
			<FieldLabel htmlFor={sortId} className="text-muted-foreground">
				Sort
			</FieldLabel>
			<Select
				items={SORT_ITEMS}
				value={value ?? "NEWEST"}
				onValueChange={(next: string | null) =>
					onChange(next === "ACTIONABILITY" ? "ACTIONABILITY" : undefined)
				}
			>
				<SelectTrigger id={sortId} size="sm" className="w-52 max-w-full">
					<ArrowDownWideNarrowIcon aria-hidden className="text-muted-foreground" />
					<SelectValue />
				</SelectTrigger>
				<SelectContent>
					{SORT_ITEMS.map((item) => (
						<SelectItem key={item.value} value={item.value}>
							{item.label}
						</SelectItem>
					))}
				</SelectContent>
			</Select>
		</Field>
	);
}
