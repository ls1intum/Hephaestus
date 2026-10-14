// Finds each issue that the change names as its own, with the time when it was opened, the time when
// the earliest commit of the change was authored, and the time between them. The review reads the two
// times beside each other.
import { readCapturedCommits } from "../kits/scm/change.ts";
import { scm, expectKind } from "../kits/scm/index.ts";
import {
	branchIssueReferences,
	closingReferences,
	issueNumberReferences,
	namedIssues,
	relatedReferences,
} from "../kits/scm/references.ts";
import { millisBetween, readLinkedWorkItemCapture } from "../kits/scm/review.ts";
import { factsOf, listWithin } from "../lib/core/bounded-text.ts";
import { REVIEW_LEAD_LIMIT } from "../lib/core/contract.ts";
import { definePracticeScript, type ReviewLead } from "../lib/core/practice-script.ts";

export const LABELS = {
	"candidate-issue":
		"An issue that the change names as its own, with when it was opened and when the earliest commit was authored.",
};

/** The two moments in one sentence, the way the cell reads them. */
function relationOf(issueToFirstCommit: number | null): string {
	if (issueToFirstCommit === null) {
		return "the captured commits do not establish the earliest authored time; the order is not known from the record";
	}
	return issueToFirstCommit >= 0
		? `the earliest commit was authored ${hours(issueToFirstCommit)} h after the issue was opened`
		: `the issue was opened ${hours(-issueToFirstCommit)} h after the earliest commit was authored`;
}

/** Hours with one decimal, signed: positive when `to` is later than `from`. */
function hours(millis: number): string {
	return (millis / 3_600_000).toFixed(1);
}

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const domain = scm(ctx);
		expectKind(domain, "scm.pull_request");
		const leads: ReviewLead[] = [];
		const directions: string[] = [];
		const body = domain.item.body ?? "";
		const title = domain.item.title ?? "";
		const capture = await readLinkedWorkItemCapture(ctx.evidence);
		const linked = capture?.items ?? null;
		const named = namedIssues([
			[
				"provider closing link",
				(linked ?? []).filter((item) => item.how === "closesOnMerge").map((item) => item.number),
			],
			["closing keyword", closingReferences(body)],
			["title", issueNumberReferences(title)],
			["branch", branchIssueReferences(domain.item.source_branch)],
			["Related to", relatedReferences(body)],
		]);
		const capturedCommits = await readCapturedCommits(ctx.evidence);
		const commitsCaptured = capturedCommits !== null;
		const commits = capturedCommits ?? [];
		const authored = commits
			.filter((commit) => Number.isFinite(Date.parse(commit.authoredAt)))
			.toSorted((a, b) => Date.parse(a.authoredAt) - Date.parse(b.authoredAt));
		const first = authored.length === commits.length ? authored[0] : undefined;
		let earliestDeltaMinutes = Number.NaN;
		let relatedOnly = false;
		for (const item of linked ?? []) {
			const namedBy = named.get(item.number)?.join(", ");
			if (namedBy === undefined) {
				continue;
			}
			relatedOnly ||= namedBy === "Related to";
			const toFirstCommit = millisBetween(item.createdAt, first?.authoredAt);
			if (toFirstCommit !== null) {
				const minutes = -toFirstCommit / 60_000;
				if (Number.isNaN(earliestDeltaMinutes) || minutes > earliestDeltaMinutes) {
					earliestDeltaMinutes = minutes;
				}
			}
			let relation =
				"the commit record is missing or malformed: a collection gap, not a timing problem";
			if (commitsCaptured) {
				relation =
					item.createdAt === undefined
						? "its opening time is not captured, so the order cannot be read"
						: relationOf(toFirstCommit);
			}
			leads.push({
				at: { record: `linked_work_items/${String(item.number)}.md`, line: 0 },
				label: LABELS["candidate-issue"],
				facts: factsOf({
					number: item.number,
					namedBy,
					providerLink: item.how ?? "",
					openedAt: item.createdAt ?? "",
					...(first === undefined
						? {}
						: { firstCommit: first.sha.slice(0, 7), firstCommitAuthoredAt: first.authoredAt }),
					...(toFirstCommit === null ? {} : { hoursFromIssueToFirstCommit: hours(toFirstCommit) }),
					relation,
				}),
			});
		}
		const numbers = [...named.keys()].map((n) => `#${String(n)}`);
		// A named number this repository holds no synchronized issue for may be an issue not yet
		// synchronized or a reference elsewhere; the record cannot say which.
		const unresolved = [...named.keys()].filter((n) => capture?.unresolved.includes(n) ?? false);
		if (!commitsCaptured) {
			directions.push(
				"The commit record is missing or malformed: report a collection gap and record no problem.",
			);
		} else if (capture === null) {
			directions.push(
				"linked_work_items.json was not captured: a collection gap, not a change without an issue.",
			);
		} else if (leads.length === 0 && unresolved.length === 0) {
			directions.push(
				"No issue of this repository is named by a provider closing link, a closing keyword, the title, the branch or a `Related to #N`; without an issue this change implements there is nothing to have planned in.",
			);
		} else {
			if (unresolved.length > 0) {
				directions.push(
					`The named issue(s) ${listWithin(
						unresolved.map((n) => `#${String(n)}`),
						100,
					)} are among the capture's unresolved references: this repository holds no synchronized issue with that number, so no opening time.`,
					"Whether one is the issue this change implements is read from the description: if it is, the record cannot settle the order, so name that issue and its missing opening time (never a timing judgement on another issue instead); if the description rules it out, it is no applicability.",
				);
			}
			if (leads.length > 0) {
				directions.push(
					"The cell is decided by the issue's opening time against the earliest authored commit of the change (commits.json), never against the pull request's creation.",
					"A pull request is opened when the work is handed off, and the practice asks whether the plan preceded the work.",
					"Compare the issue opening and earliest captured authored commit exactly. These dates establish the recorded order, not when all work began or why the issue was written.",
					...(first === undefined
						? [
								"If the complete captured commit record is empty or any commit lacks a usable authored date, the earliest time cannot be established, so the record cannot settle the timing: the criteria say how to decide it.",
								"The pull request's creation dates the handoff, not the start of work; it cannot replace the missing date. Missing commit capture is a collection gap, not an unsettled order.",
							]
						: []),
				);
				if (relatedOnly) {
					directions.push(
						"An issue named only by `Related to` is the applicability only when the description or the issue shows this change implements part of it, not when the sentence is about other work.",
						"It was captured as a mention, not a closing candidate: nothing here says the issue closed or will close on merge.",
					);
				}
				if (leads.length > 1) {
					directions.push(
						`Several issues are named (${listWithin(numbers, 60)}): judge the one this change implements, read from the description and the issues. If more than one remains plausible and their timing falls in different cells, do not choose the favourable one.`,
					);
				}
			}
		}
		return ctx.review({
			leads: leads.slice(0, REVIEW_LEAD_LIMIT),
			limitations:
				leads.length > REVIEW_LEAD_LIMIT
					? [
							{
								what: "linked issue leads",
								count: leads.length - REVIEW_LEAD_LIMIT,
								reason: "selection",
							},
						]
					: [],
			facts: {
				namedIssues: named.size,
				unresolvedNamedIssues: unresolved.length,
				linkedItems: linked?.length ?? -1,
				commits: commits.length,
				issueOpenedAfterFirstCommitMinutes: Number.isNaN(earliestDeltaMinutes)
					? -1
					: Math.max(0, Math.round(earliestDeltaMinutes)),
			},
			directions,
		});
	},
});
