/**
 * Writes the end-of-test summary k6 documents for `handleSummary` into the run directory the runner
 * mounts at `/results`. Each key of the returned object is a file path; returning one at all replaces
 * the summary k6 would otherwise print, which the generated baseline document reports in full.
 */
export function handleSummary(data) {
	return { "/results/summary.json": JSON.stringify(data, null, 2) };
}
