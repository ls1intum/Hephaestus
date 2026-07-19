/**
 * Drops rows already seen, keeping order.
 *
 * Offset pagination over an append-only log is not stable: a row written between fetching page N and
 * page N+1 pushes everything down one slot, so the next page repeats what the previous one ended
 * with. On an audit surface that is the normal case, because the admin actions being watched are the
 * ones writing the rows.
 */
export function dedupeById<T extends { id?: number }>(rows: T[]): T[] {
	const seen = new Set<number>();
	return rows.filter((row) => {
		if (row.id == null) return true;
		if (seen.has(row.id)) return false;
		seen.add(row.id);
		return true;
	});
}
