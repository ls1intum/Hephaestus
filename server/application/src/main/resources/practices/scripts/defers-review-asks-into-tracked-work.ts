// Finds each ask that a reviewer made, one lead each, with what the record shows beside it: if the
// change touches the file of the ask, if the author replied and if the reply names an issue, if the
// reviewer had a later word on it or approved after it, and if the thread was marked resolved. The
// review decides for each ask if it was addressed, deferred into tracked work, waived or merged past.
// The RESOLVED mark of a thread tells who clicked it, not what happened to the ask.
import { scm, expectKind, requireChange, type DiffFile } from "../kits/scm/index.ts";
import { issueNumberReferences } from "../kits/scm/references.ts";
import {
	type GeneralComment,
	type ReviewComment,
	changeNear,
	later,
	readGeneralComments,
	readReviewComments,
	readReviewThreads,
	reviewerCommentRows,
} from "../kits/scm/review.ts";
import { factsOf } from "../lib/core/bounded-text.ts";
import { definePracticeScript, type ReviewLead } from "../lib/core/practice-script.ts";

export const LABELS = {
	"inline-ask":
		"A comment by someone other than the author that opens a thread on a line of the change.",
	"conversation-ask": "A comment by someone other than the author in the conversation.",
};

/** Words a reply uses to put an ask off rather than answer it. */
const DEFERRAL =
	/\b(?:later|next (?:mr|pr|merge request|pull request|sprint|iteration)|follow[- ]?up|will (?:do|add|fix|revisit|look)|todo|not (?:in|for) this (?:mr|pr)|separate (?:mr|pr|issue)|future)\b/iu;

/** Words a reviewer uses to drop an ask rather than keep it. */
const WAIVER =
	/\b(?:fine to skip|not needed|no need|never mind|nevermind|ignore|optional|nit|non[- ]blocking|up to you|can be skipped)\b/iu;

/** One ask, with the facts that the record shows beside it. */
interface Ask {
	lead: ReviewLead;
	authorReplied: boolean;
	issueNamedInReply: boolean;
}

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const domain = scm(ctx);
		expectKind(domain, "scm.pull_request");
		// The practice reviews the asks that the work merged with, so a requested review of unmerged
		// work has none to decide yet.
		if (ctx.review.origin === "REQUEST" && !domain.item.is_merged) {
			return ctx.review({
				leads: [],
				directions: [
					"The work is not merged. This practice decides each review ask at the merge, so no ask is listed.",
				],
			});
		}
		const change = requireChange(domain.files);
		const author = domain.item.author ?? "";
		const inline = await readReviewComments(ctx.evidence);
		const general = await readGeneralComments(ctx.evidence);
		const record = await readReviewThreads(ctx.evidence);
		const decisions = record?.decisions ?? [];
		const asks: Ask[] = [];
		const byOthers = (comment: { author?: string }) =>
			comment.author !== undefined && comment.author !== author;
		const approvedAfter = (at: string | undefined) =>
			decisions.some(
				(d) => d.state === "APPROVED" && d.dismissed !== true && later(d.submittedAt, at),
			);
		// An inline ask: the first comment in a thread by someone other than the author. The author's
		// later comments in the thread are its answers; the others' later comments there are the
		// reviewer's last word on it, not new asks.
		const inlineAsks = reviewerCommentRows(inline ?? [], record?.threads ?? [], author).filter(
			(r) => r.opensThread,
		);
		for (const ask of inlineAsks) {
			asks.push(
				askOf(ask.comment, ask.replies, ask.followUps, change, {
					threadResolved: ask.thread?.state === "RESOLVED",
					approvedAfterAsk: approvedAfter(ask.comment.createdAt),
				}),
			);
		}
		// A conversation ask: a general comment by someone other than the author; the author's later
		// general comments are the candidate answers, the others' later ones the follow-ups.
		const conversationAsks = (general ?? []).filter(byOthers);
		for (const ask of conversationAsks) {
			const afterAsk = (c: GeneralComment) => later(c.createdAt, ask.createdAt);
			const replies = (general ?? []).filter((c) => c.author === author && afterAsk(c));
			const followUps = (general ?? []).filter((c) => byOthers(c) && afterAsk(c));
			asks.push(
				askOf(ask, replies, followUps, change, {
					threadResolved: false,
					approvedAfterAsk: approvedAfter(ask.createdAt),
				}),
			);
		}
		const directions: string[] = [];
		if (inline === null || general === null) {
			directions.push(
				"A required comments file is missing or malformed (comments.json or general_comments.json): report a collection gap and record no problem; an unread source does not establish that no ask exists.",
			);
		} else if (asks.length === 0) {
			directions.push(
				`No comment by anyone other than the author in the captured record (${inline.length} inline comment(s), ${general.length} conversation comment(s)): with no ask, the applicability did not arise.`,
			);
		} else {
			directions.push(
				`${String(asks.length)} comment(s) by others are listed, one lead each; a remark that asks nothing of this change is not an ask.`,
				"Decide every ask. One about the code is addressed when the head diff carries the change asked for.",
				"One about the pull request itself — mark it ready, retitle it, link an issue, fill in the description — is addressed when the captured record shows it done (metadata.json, description.md, the linked work items).",
				"A merged pull request was not a draft when it merged.",
				"An ask is deferred into tracked work when the author's reply names an issue or the description defers it to one; waived when the reviewer drops the ask in their own words, in the ask or in a follow-up; otherwise deferred bare.",
				'A later approval alone does not establish a waiver. A RESOLVED thread says nothing by itself, and "approved on the condition that you do it next time" is a deferral to track, not a waiver.',
				"A two-part ask is two asks: address one part and the other still stands.",
			);
		}
		return ctx.review({
			leads: asks.slice(0, 200).map((ask) => ask.lead),
			facts: {
				asks: asks.length,
				inlineAsks: inlineAsks.length,
				conversationAsks: conversationAsks.length,
				asksWithAuthorReply: asks.filter((ask) => ask.authorReplied).length,
				asksNamingAnIssueInReply: asks.filter((ask) => ask.issueNamedInReply).length,
				commentsFileAbsent: inline === null ? 1 : 0,
				generalCommentsFileAbsent: general === null ? 1 : 0,
			},
			directions,
		});
	},
});

/** An inline ask cites its body in `comments.json`, and a conversation ask in `general_comments.json`. */
function askOf(
	ask: ReviewComment | GeneralComment,
	replies: readonly { body: string }[],
	followUps: readonly { body: string }[],
	change: ReadonlyMap<string, DiffFile>,
	record: { threadResolved: boolean; approvedAfterAsk: boolean },
): Ask {
	const replyText = replies.map((r) => r.body).join("\n");
	const followUpText = followUps.map((r) => r.body).join("\n");
	const authorReplied = replies.length > 0;
	const issueNamedInReply = issueNumberReferences(replyText).length > 0;
	const facts = factsOf({
		by: ask.author ?? "",
		at: ask.createdAt ?? "",
		authorReplied,
		issueNamedInReply,
		deferralWordsInReply: DEFERRAL.test(replyText),
		waiverWordsInAsk: WAIVER.test(ask.body),
		reviewerFollowedUp: followUps.length > 0,
		waiverWordsInFollowUp: WAIVER.test(followUpText),
		approvedAfterAsk: record.approvedAfterAsk,
		threadResolved: record.threadResolved,
	});
	if (!("path" in ask)) {
		return {
			lead: {
				at: { record: "general_comments.json", line: ask.recordLine },
				label: LABELS["conversation-ask"],
				facts,
			},
			authorReplied,
			issueNamedInReply,
		};
	}
	return {
		lead: {
			at: { record: "comments.json", line: ask.recordLine },
			label: LABELS["inline-ask"],
			facts:
				ask.path === ""
					? facts
					: {
							...facts,
							path: ask.path,
							...(ask.line === undefined ? {} : { line: ask.line }),
							fileInChange: change.has(ask.path),
							changeNearLine: changeNear(change.get(ask.path), ask.line),
							outdated: ask.outdated,
						},
		},
		authorReplied,
		issueNamedInReply,
	};
}
