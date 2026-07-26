/** An account/actor reference: the shape both audit surfaces resolve to a display label. */
export interface AuditRef {
	id?: number;
	displayName?: string;
	email?: string;
}

/**
 * Render an account reference as a human label, falling back to `#id` when the account no longer exists
 * (audit rows outlive accounts — deletion, GDPR redaction). `id` is the raw subject/actor id when no
 * resolved ref is available.
 */
export function refLabel(ref: AuditRef | undefined, id: number | undefined): string | null {
	if (ref) return ref.displayName || ref.email || `#${ref.id}`;
	if (id != null) return `#${id}`;
	return null;
}
