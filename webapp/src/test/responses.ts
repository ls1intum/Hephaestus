/**
 * Answers one request handler's repeated calls in order, keeping the last answer for every call
 * after the sequence runs out — "the first read fails, the retry succeeds", "the reconcile is still
 * running on the first poll and finished by the second".
 *
 * Each answer is a function because a `Response` body can only be read once. Naming the sequence
 * where the handler is registered keeps the arithmetic out of the test body: a hand-rolled call
 * counter puts the interesting case behind a branch, and a branch that never runs asserts nothing.
 */
export function respondInTurn<T>(first: () => T, ...rest: (() => T)[]): () => T {
	const remaining = [...rest];
	let current = first;
	return () => {
		const answer = current;
		current = remaining.shift() ?? current;
		return answer();
	};
}
