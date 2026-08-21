import { Link, type LinkComponentProps } from "@tanstack/react-router";
import { type DetailStackEntry, detailStackKey } from "./detail-stack";

export interface DetailStackLinkProps extends Omit<LinkComponentProps<"a">, "to" | "search"> {
	entry: DetailStackEntry;
}

/**
 * Opens `entry` as the next level of the detail-drawer stack.
 *
 * It is a real link to the current route with one more `detail` param, which is what makes the
 * whole pattern shallow routing rather than hidden state: the row can be opened in a new tab,
 * copied, and reloaded, and Back closes exactly the drawer it opened. Appending to `previous`
 * rather than to a captured stack means the same component works at every depth.
 */
export function DetailStackLink({ entry, ...props }: DetailStackLinkProps) {
	return (
		<Link
			to="."
			search={(previous: Record<string, unknown>) => ({
				...previous,
				detail: [...toStack(previous.detail), detailStackKey(entry)],
			})}
			{...props}
		/>
	);
}

function toStack(detail: unknown): string[] {
	if (Array.isArray(detail)) return detail.filter((value) => typeof value === "string");
	return typeof detail === "string" ? [detail] : [];
}
