/**
 * Keeps a saved file's object URL after the click, then revokes it after 40 seconds, as FileSaver.js
 * does. The browser can read the URL after the click returns, so revoking it at once can lose the
 * file. No event says that a download finished: the delay does not confirm one, and a suspended page
 * can run the timer later.
 */
const OBJECT_URL_LIFETIME_MS = 40_000;

/**
 * Hands a file the page holds to the browser as a download named `filename`, through an anchor
 * attached to the document.
 */
export function saveBlobFile(blob: Blob, filename: string): void {
	const url = URL.createObjectURL(blob);
	const anchor = document.createElement("a");
	anchor.href = url;
	anchor.download = filename;
	document.body.append(anchor);
	anchor.click();
	anchor.remove();
	setTimeout(() => URL.revokeObjectURL(url), OBJECT_URL_LIFETIME_MS);
}

/**
 * The export needs the bearer token, so it cannot be a plain link; the response is handed to the
 * browser as a named file instead.
 */
export function saveTextFile(text: string, filename: string, mimeType: string): void {
	saveBlobFile(new Blob([text], { type: mimeType }), filename);
}
