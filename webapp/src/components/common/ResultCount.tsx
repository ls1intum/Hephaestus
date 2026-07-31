export interface ResultCountProps {
	total: number | undefined;
	noun: readonly [string, string];
	hasFilter?: boolean;
}

export function ResultCount({ total, noun, hasFilter = false }: ResultCountProps) {
	const text =
		total === undefined
			? ""
			: `${total.toLocaleString()} ${total === 1 ? noun[0] : noun[1]}${hasFilter ? " match your filters" : ""}.`;
	return text ? (
		<p role="status" aria-live="polite" className="text-sm text-muted-foreground">
			{text}
		</p>
	) : null;
}
