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
 * The endpoint's orderings in the operator's words. Each label says what arrives at the *top*,
 * because that is the only part of an ordering a reader of the first screenful can check.
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
 * A list has no sortable column headers, so the ordering has to be offered explicitly.
 *
 * <p>`NEWEST` is reported as `undefined` rather than as the string: it is the server's default, so
 * writing it into the URL would put a parameter in every link that changes nothing.
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
