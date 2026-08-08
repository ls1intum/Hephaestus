export interface DetailRowProps {
	label: string;
	children: React.ReactNode;
}

/**
 * `dt`/`dd` is what associates label and value programmatically — neighbouring spans read as two
 * unrelated strings to a screen reader — and the value wraps unbroken tokens (model ids, user
 * agents) instead of pushing the row past the sheet.
 */
export function DetailRow({ label, children }: DetailRowProps) {
	return (
		<div className="grid grid-cols-[8rem_1fr] gap-2 py-1.5 text-sm">
			<dt className="text-muted-foreground">{label}</dt>
			<dd className="min-w-0 break-words">{children}</dd>
		</div>
	);
}
