import type { AuditRef } from "./refLabel";

/**
 * The display name for an account id, taken from the rows already on screen — the audit rows embed
 * their actor refs precisely so a filter pill needs no second fetch.
 */
export function nameForRef(
	rows: readonly { account?: AuditRef; actor?: AuditRef; actingActor?: AuditRef }[],
	id: number,
): string | undefined {
	for (const row of rows) {
		for (const ref of [row.account, row.actor, row.actingActor]) {
			if (ref?.id === id) return ref.displayName ?? undefined;
		}
	}
	return undefined;
}
