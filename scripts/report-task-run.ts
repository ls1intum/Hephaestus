/**
 * Turns the report of the last `vp run` into what a CI job needs to say about it: one workflow error
 * annotation per task that did not pass, naming the command that reproduces it, and the report
 * itself in the job summary. Reads the report on stdin; exits non-zero when any task did not pass.
 *
 * The runner stops the tasks still running when one fails and reports them exactly like the task
 * that failed, so an annotation says a task did not pass rather than claiming it failed on its own.
 * The report in the summary keeps the order, which is where the first failure is.
 */
import { appendFileSync } from "node:fs";
import { text } from "node:stream/consumers";

/** Task names the report marks with a cross, from lines shaped `[n] package#task: $ command ✗`. */
export function unpassedTasks(report: string): string[] {
	const names = [...report.matchAll(/^\s*\[\d+\] [^#\n]+#(\S+): \$ [^\n]*✗/gmu)].flatMap(
		([, task]) => (task === undefined ? [] : [task]),
	);
	return [...new Set(names)];
}

if (import.meta.main) {
	const report = await text(process.stdin);
	const unpassed = unpassedTasks(report);
	for (const task of unpassed)
		console.log(`::error::${task} did not pass. Reproduce with: vp run ${task}`);
	const summary = process.env.GITHUB_STEP_SUMMARY;
	if (summary) appendFileSync(summary, `\n\`\`\`text\n${report.trim()}\n\`\`\`\n`);
	process.exitCode = unpassed.length === 0 ? 0 : 1;
}
