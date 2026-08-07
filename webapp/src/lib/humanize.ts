/**
 * A machine token read back as a sentence: `LOGIN_PROVIDER_CREATED` becomes "Login provider
 * created", `whatGoodLooksLike` becomes "What good looks like".
 *
 * <p>This is the fallback a label map reaches for when it does not know a value — which happens
 * whenever a server ships a constant before this bundle does. Rendering it as words rather than as
 * the constant is what makes the new thing legible instead of merely visible.
 */
export function humanizeToken(token: string): string {
	const words = token
		.replace(/_/g, " ")
		.replace(/([a-z])([A-Z])/g, "$1 $2")
		.toLowerCase();
	return words.charAt(0).toUpperCase() + words.slice(1);
}
