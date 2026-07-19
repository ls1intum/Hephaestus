/** A label/value row for the audit detail sheets: a two-column grid inside a definition list. */
export function DetailRow({ label, children }: { label: string; children: React.ReactNode }) {
	return (
		<div className="grid grid-cols-[8rem_1fr] gap-2 py-1.5 text-sm">
			<dt className="text-muted-foreground">{label}</dt>
			<dd className="min-w-0 break-words">{children}</dd>
		</div>
	);
}
