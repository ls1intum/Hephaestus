/**
 * A mentor turn succeeded only when its stream carries a non-error finish chunk before the `[DONE]`
 * sentinel; the server sends `[DONE]` to terminate an error response too.
 */
export function mentorStreamCompleted(body) {
	if (typeof body !== "string") return false;
	let finished = false;
	let done = false;
	for (const line of body.split(/\r?\n/)) {
		if (!line.startsWith("data:")) continue;
		if (done) return false;
		const data = line.slice(5).trim();
		if (data === "[DONE]") {
			done = true;
			continue;
		}
		try {
			const chunk = JSON.parse(data);
			if (!chunk || typeof chunk.type !== "string") return false;
			if (["error", "abort", "tool-output-error"].includes(chunk.type)) return false;
			if (chunk.type === "finish") {
				if (["error", "content-filter"].includes(chunk.finishReason)) return false;
				finished = true;
			}
		} catch {
			return false;
		}
	}
	return finished && done;
}
