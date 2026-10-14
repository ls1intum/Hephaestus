// Finds each note by someone other than the author, inline and in the conversation, one lead each, with
// what the record shows beside it: the author's later reply, the resolution of the thread and who
// resolved it, the reviewer's own later note, if and how soon the same reviewer approved after it, if
// the note came after the work merged or closed, and the authored commits after it. The review decides
// for each note if it asked something of this change and if the author engaged. The script never does.
import { type ChangeCommit, readCapturedCommits } from "../kits/scm/change.ts";
import { scm, expectKind, requireChange } from "../kits/scm/index.ts";
import {
	type GeneralComment,
	type ReviewDecision,
	changeNear,
	commitsAfter,
	excerpt,
	later,
	millisBetween,
	readGeneralComments,
	readReviewComments,
	readReviewThreads,
	reviewerCommentRows,
} from "../kits/scm/review.ts";
import { factsOf } from "../lib/core/bounded-text.ts";
import { definePracticeScript, type Facts, type ReviewLead } from "../lib/core/practice-script.ts";

export const LABELS = {
	"reviewer-comment": "An inline comment by someone other than the author.",
	"conversation-comment": "A conversation comment by someone other than the author.",
};

/** An instant strictly between two others; false when any is missing. */
function between(
	at: string | undefined,
	from: string | undefined,
	to: string | undefined,
): boolean {
	return later(at, from) && !later(at, to);
}

/**
 * The same reviewer's first approval at or after the note, with what came between them: advice posted
 * with an approval reads differently from an approval given after the author answered. Empty when that
 * reviewer did not approve afterwards.
 */
function approvalAfter(
	decisions: readonly ReviewDecision[],
	reviewer: string | undefined,
	at: string | undefined,
	commits: readonly ChangeCommit[] | null,
	authorNotes: readonly { createdAt?: string }[],
): string {
	const approval = decisions
		.filter(
			(d) =>
				d.state === "APPROVED" &&
				d.dismissed !== true &&
				d.author === reviewer &&
				(millisBetween(at, d.submittedAt) ?? -1) >= 0,
		)
		.toSorted((a, b) => Date.parse(a.submittedAt ?? "") - Date.parse(b.submittedAt ?? ""))[0];
	if (approval === undefined) {
		return "";
	}
	const seconds = Math.round((millisBetween(at, approval.submittedAt) ?? 0) / 1000);
	const commitsBetween = (commits ?? []).filter(
		(c) => c.parents.length < 2 && between(c.committedAt, at, approval.submittedAt),
	).length;
	const notesBetween = authorNotes.filter((n) =>
		between(n.createdAt, at, approval.submittedAt),
	).length;
	return `${String(seconds)}s later; ${commits === null ? "unknown commit count" : `${String(commitsBetween)} commit(s)`} and ${String(notesBetween)} author note(s) between`;
}

/** One note, as a lead, with the facts that the counts read. */
interface Note {
	lead: ReviewLead;
	bot: boolean;
	replied: boolean;
	committedOnFile: boolean;
	afterHandOff: boolean;
}

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const domain = scm(ctx);
		expectKind(domain, "scm.pull_request");
		const change = requireChange(domain.files);
		const author = domain.item.author ?? "";
		const inline = await readReviewComments(ctx.evidence);
		const general = await readGeneralComments(ctx.evidence);
		const record = await readReviewThreads(ctx.evidence);
		const capturedCommits = await readCapturedCommits(ctx.evidence);
		const commits = capturedCommits ?? [];
		const decisions = record?.decisions ?? [];
		const handOff = domain.item.merged_at ?? domain.item.closed_at;
		const byOthers = (note: { author?: string }) =>
			note.author !== undefined && note.author !== author;
		const authorNotes = [
			...(inline ?? []).filter((c) => c.author === author),
			...(general ?? []).filter((c) => c.author === author),
		];
		/** The reviewer's next conversation note after `at`, where a settling "thank you" usually sits. */
		const reviewerNextNote = (reviewer: string | undefined, at: string | undefined) =>
			(general ?? []).find((c) => c.author === reviewer && later(c.createdAt, at))?.body ?? "";

		const inlineNotes: Note[] = reviewerCommentRows(
			inline ?? [],
			record?.threads ?? [],
			author,
		).map(({ comment, replies, followUps, thread }) => {
			const commitsAfterTouchingFile =
				capturedCommits === null
					? "unknown"
					: commitsAfter(commits, comment.createdAt, comment.path);
			const afterHandOff = later(comment.createdAt, handOff);
			const facts: Facts = factsOf({
				by: comment.author ?? "",
				bot: comment.bot,
				at: comment.createdAt ?? "",
				path: comment.path,
				side: comment.side ?? "",
				outdated: comment.outdated,
				authorReplied: replies.length > 0,
				replyExcerpt: excerpt(replies.map((r) => r.body).join(" "), 80),
				threadResolved: thread?.state === "RESOLVED",
				resolvedBy: thread?.resolvedBy ?? "",
				reviewerLaterNote: excerpt(
					followUps.find((f) => f.author === comment.author)?.body ??
						reviewerNextNote(comment.author, comment.createdAt),
					80,
				),
				reviewerApprovedAfter: approvalAfter(
					decisions,
					comment.author,
					comment.createdAt,
					capturedCommits,
					authorNotes,
				),
				afterHandOff,
				fileInChange: change.has(comment.path),
				changeNearLine: changeNear(change.get(comment.path), comment.line),
				commitsAfter:
					capturedCommits === null ? "unknown" : commitsAfter(commits, comment.createdAt),
				commitsAfterTouchingFile,
			});
			if (comment.line !== undefined) {
				facts.line = comment.line;
			}
			return {
				lead: {
					at: { record: "comments.json", line: comment.recordLine },
					label: LABELS["reviewer-comment"],
					facts,
				},
				bot: comment.bot,
				replied: replies.length > 0,
				committedOnFile: Number(commitsAfterTouchingFile) > 0,
				afterHandOff,
			};
		});
		const conversationNotes: Note[] = (general ?? [])
			.filter(byOthers)
			.map((note: GeneralComment) => {
				const authorAfter = (general ?? []).filter(
					(c) => c.author === author && later(c.createdAt, note.createdAt),
				);
				const afterHandOff = later(note.createdAt, handOff);
				return {
					lead: {
						at: { record: "general_comments.json", line: note.recordLine },
						label: LABELS["conversation-comment"],
						facts: factsOf({
							by: note.author ?? "",
							bot: note.bot,
							at: note.createdAt ?? "",
							authorNotesAfter: authorAfter.length,
							authorNoteExcerpt: excerpt(authorAfter[0]?.body ?? "", 80),
							reviewerLaterNote: excerpt(reviewerNextNote(note.author, note.createdAt), 80),
							reviewerApprovedAfter: approvalAfter(
								decisions,
								note.author,
								note.createdAt,
								capturedCommits,
								authorNotes,
							),
							afterHandOff,
							commitsAfter:
								capturedCommits === null ? "unknown" : commitsAfter(commits, note.createdAt),
						}),
					},
					bot: note.bot,
					replied: authorAfter.length > 0,
					committedOnFile: false,
					afterHandOff,
				};
			});
		const notes = [...inlineNotes, ...conversationNotes];
		const byPeople = notes.filter((note) => !note.bot);
		const inlineByPeople = inlineNotes.filter((note) => !note.bot).length;
		const conversationByPeople = conversationNotes.filter((note) => !note.bot).length;
		const replied = byPeople.filter((note) => note.replied).length;
		// Without the commit record each note's count is unknown, and so is their sum: never a measured 0.
		const committedAfter =
			capturedCommits === null ? null : byPeople.filter((note) => note.committedOnFile).length;
		const afterHandOff = byPeople.filter((note) => note.afterHandOff).length;
		const directions: string[] = [];
		if (inline === null && general === null) {
			directions.push(
				"No comments file was captured (neither comments.json nor general_comments.json): the record of reviewer notes is not available here.",
			);
		} else if (notes.length === 0) {
			directions.push(
				`No note by anyone other than the author in the captured record (${inline === null ? "comments.json not captured" : `${String(inline.length)} inline comment(s)`}, ${general === null ? "general_comments.json not captured" : `${String(general.length)} conversation comment(s)`}).`,
			);
		} else {
			const bots = notes.length - byPeople.length;
			directions.push(
				`${String(byPeople.length)} reviewer note(s) (${String(inlineByPeople)} inline, ${String(conversationByPeople)} conversation)${bots > 0 ? `, ${String(bots)} more by bots` : ""}; ${String(replied)} have a later author note.`,
				`${committedAfter === null ? "How many have a later authored commit touching the commented file is unknown" : `${String(committedAfter)} have a later authored commit touching the commented file`}; ${String(afterHandOff)} were posted after the work merged or closed.`,
				`Commit capture: ${capturedCommits === null ? "unavailable" : `${String(commits.length)} captured commit(s)`} in commits.json.`,
			);
		}
		return ctx.review({
			leads: notes.slice(0, 200).map((note) => note.lead),
			facts: {
				commentsFileAbsent: inline === null ? 1 : 0,
				generalCommentsFileAbsent: general === null ? 1 : 0,
				// An uncaptured record counts nothing: its count is left out, never a measured 0.
				...(inline === null ? {} : { reviewerComments: inlineByPeople }),
				...(general === null ? {} : { conversationComments: conversationByPeople }),
				botComments: notes.length - byPeople.length,
				withAuthorReply: replied,
				...(committedAfter === null ? {} : { withLaterCommitOnFile: committedAfter }),
				afterHandOff,
				commitsFileAbsent: capturedCommits === null ? 1 : 0,
			},
			directions,
		});
	},
});
