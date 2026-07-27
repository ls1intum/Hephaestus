export interface DetailRowProps {
	label: string;
	children: React.ReactNode;
}

/**
 * One label/value pair of a detail sheet's `<dl>` — the row renderer the "inspect this record"
 * sheets use, so a job sheet and an audit sheet read the same way.
 *
 * `dt`/`dd` is what associates the two programmatically; a pair of neighbouring spans reads as two
 * unrelated strings to a screen reader. The fixed label column aligns every row's value on one edge,
 * and `min-w-0` + `break-words` on the value is what makes unbroken tokens (model ids, error strings,
 * user agents) wrap instead of pushing the row past the sheet at phone widths.
 */
export function DetailRow({ label, children }: DetailRowProps) {
	return (
		<div className="grid grid-cols-[8rem_1fr] gap-2 py-1.5 text-sm">
			<dt className="text-muted-foreground">{label}</dt>
			<dd className="min-w-0 break-words">{children}</dd>
		</div>
	);
}
