/**
 * Copy rich text to the clipboard as both HTML and plain text: try the modern ClipboardItem API so
 * pasting into rich targets keeps links, and fall back to plain text when ClipboardItem is
 * unsupported or the rich write is rejected. Resolves `true` only when one of the writes actually
 * succeeded, so callers can surface failure (e.g. permission denied) instead of claiming success.
 */
export async function copyHtmlAndText(html: string, plainText: string): Promise<boolean> {
	try {
		const clipboardItem = new ClipboardItem({
			"text/html": new Blob([html], { type: "text/html" }),
			"text/plain": new Blob([plainText], { type: "text/plain" }),
		});
		await navigator.clipboard.write([clipboardItem]);
		return true;
	} catch {
		// Fall through to the plain-text fallback below.
	}
	try {
		await navigator.clipboard.writeText(plainText);
		return true;
	} catch {
		return false;
	}
}
