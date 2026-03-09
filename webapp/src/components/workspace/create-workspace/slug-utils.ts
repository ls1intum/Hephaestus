/**
 * Generates a URL-friendly slug from a display name.
 *
 * Rules: lowercase, non-alphanumeric chars replaced with hyphens,
 * consecutive hyphens collapsed, must start with alphanumeric,
 * trimmed to 51 characters (matching backend pattern `^[a-z0-9][a-z0-9-]{2,50}$`).
 */
export function generateSlug(displayName: string): string {
	return (
		displayName
			// Strip diacritics so "Ünïcödé" → "Unicode" instead of garbled fragments
			.normalize("NFD")
			.replace(/[\u0300-\u036f]/g, "")
			.toLowerCase()
			.replace(/[^a-z0-9]+/g, "-")
			.replace(/^-+/, "")
			.replace(/-+$/, "")
			.slice(0, 51)
			// Re-strip trailing hyphens in case truncation introduced one
			.replace(/-+$/, "") || ""
	);
}
