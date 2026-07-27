export interface DetailRowProps {
	label: string;
	children: React.ReactNode;
}

/**
 * One label/value pair of a detail sheet's `<dl>`. `dt`/`dd` is what associates the two
 * programmatically — a pair of neighbouring spans reads as two unrelated strings to a screen
 * reader — and `min-w-0 break-words` is what wraps unbroken tokens (model ids, user agents)
 * instead of pushing the row past the sheet.
 */
export function DetailRow({ label, children }: DetailRowProps) {
	return (
		<div className="grid grid-cols-[8rem_1fr] gap-2 py-1.5 text-sm">
			<dt className="text-muted-foreground">{label}</dt>
			<dd className="min-w-0 break-words">{children}</dd>
		</div>
	);
}
