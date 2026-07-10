/**
 * Copy rich text to the clipboard as both HTML and plain text: try the modern ClipboardItem API so
 * pasting into rich targets keeps links, and fall back to plain text on any failure or in older
 * browsers. Clipboard rejections (e.g. permission denied) are swallowed — copy is best-effort.
 */
export function copyHtmlAndText(html: string, plainText: string): void {
	try {
		const clipboardItem = new ClipboardItem({
			"text/html": new Blob([html], { type: "text/html" }),
			"text/plain": new Blob([plainText], { type: "text/plain" }),
		});
		navigator.clipboard.write([clipboardItem]).catch(() => {
			navigator.clipboard.writeText(plainText).catch(() => {});
		});
	} catch {
		// Basic fallback for browsers without ClipboardItem support.
		navigator.clipboard.writeText(plainText).catch(() => {});
	}
}
