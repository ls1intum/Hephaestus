/**
 * A machine token read back as a sentence: `LOGIN_PROVIDER_CREATED` becomes "Login provider
 * created", `whatGoodLooksLike` becomes "What good looks like".
 *
 * The fallback a label map reaches for when the server ships a constant this bundle does not know.
 */
export function humanizeToken(token: string): string {
	const words = token
		.replace(/_/g, " ")
		.replace(/([a-z])([A-Z])/g, "$1 $2")
		.toLowerCase();
	return words.charAt(0).toUpperCase() + words.slice(1);
}
