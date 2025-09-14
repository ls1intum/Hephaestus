import { relations } from "drizzle-orm/relations";
import {
	badPracticeDetection,
	badPracticeFeedback,
	chatMessage,
	chatMessagePart,
	chatThread,
	document,
	issue,
	issueAssignee,
	issueComment,
	issueLabel,
	label,
	milestone,
	pullRequestRequestedReviewers,
	pullRequestReview,
	pullRequestReviewComment,
	pullrequestbadpractice,
	repository,
	repositoryToMonitor,
	team,
	teamLabels,
	teamMembership,
	teamRepositoryPermission,
	user,
	workspace,
} from "./schema";

export const issueCommentRelations = relations(issueComment, ({ one }) => ({
	issue: one(issue, {
		fields: [issueComment.issueId],
		references: [issue.id],
	}),
	user: one(user, {
		fields: [issueComment.authorId],
		references: [user.id],
	}),
}));

export const issueRelations = relations(issue, ({ one, many }) => ({
	issueComments: many(issueComment),
	pullRequestReviews: many(pullRequestReview),
	pullRequestReviewComments: many(pullRequestReviewComment),
	repository: one(repository, {
		fields: [issue.repositoryId],
		references: [repository.id],
	}),
	milestone: one(milestone, {
		fields: [issue.milestoneId],
		references: [milestone.id],
	}),
	user_mergedById: one(user, {
		fields: [issue.mergedById],
		references: [user.id],
		relationName: "issue_mergedById_user_id",
	}),
	user_authorId: one(user, {
		fields: [issue.authorId],
		references: [user.id],
		relationName: "issue_authorId_user_id",
	}),
	badPracticeDetections: many(badPracticeDetection),
	pullrequestbadpractices: many(pullrequestbadpractice),
	issueLabels: many(issueLabel),
	issueAssignees: many(issueAssignee),
	pullRequestRequestedReviewers: many(pullRequestRequestedReviewers),
}));

export const userRelations = relations(user, ({ many }) => ({
	issueComments: many(issueComment),
	milestones: many(milestone),
	pullRequestReviews: many(pullRequestReview),
	pullRequestReviewComments: many(pullRequestReviewComment),
	issues_mergedById: many(issue, {
		relationName: "issue_mergedById_user_id",
	}),
	issues_authorId: many(issue, {
		relationName: "issue_authorId_user_id",
	}),
	chatThreads: many(chatThread),
	issueAssignees: many(issueAssignee),
	pullRequestRequestedReviewers: many(pullRequestRequestedReviewers),
	teamMemberships: many(teamMembership),
	documents: many(document),
}));

export const milestoneRelations = relations(milestone, ({ one, many }) => ({
	repository: one(repository, {
		fields: [milestone.repositoryId],
		references: [repository.id],
	}),
	user: one(user, {
		fields: [milestone.creatorId],
		references: [user.id],
	}),
	issues: many(issue),
}));

export const repositoryRelations = relations(repository, ({ many }) => ({
	milestones: many(milestone),
	issues: many(issue),
	labels: many(label),
	teamRepositoryPermissions: many(teamRepositoryPermission),
}));

export const pullRequestReviewRelations = relations(
	pullRequestReview,
	({ one, many }) => ({
		user: one(user, {
			fields: [pullRequestReview.authorId],
			references: [user.id],
		}),
		issue: one(issue, {
			fields: [pullRequestReview.pullRequestId],
			references: [issue.id],
		}),
		pullRequestReviewComments: many(pullRequestReviewComment),
	}),
);

export const pullRequestReviewCommentRelations = relations(
	pullRequestReviewComment,
	({ one }) => ({
		pullRequestReview: one(pullRequestReview, {
			fields: [pullRequestReviewComment.reviewId],
			references: [pullRequestReview.id],
		}),
		issue: one(issue, {
			fields: [pullRequestReviewComment.pullRequestId],
			references: [issue.id],
		}),
		user: one(user, {
			fields: [pullRequestReviewComment.authorId],
			references: [user.id],
		}),
	}),
);

export const labelRelations = relations(label, ({ one, many }) => ({
	repository: one(repository, {
		fields: [label.repositoryId],
		references: [repository.id],
	}),
	issueLabels: many(issueLabel),
	teamLabels: many(teamLabels),
}));

export const repositoryToMonitorRelations = relations(
	repositoryToMonitor,
	({ one }) => ({
		workspace: one(workspace, {
			fields: [repositoryToMonitor.workspaceId],
			references: [workspace.id],
		}),
	}),
);

export const workspaceRelations = relations(workspace, ({ many }) => ({
	repositoryToMonitors: many(repositoryToMonitor),
}));

export const badPracticeDetectionRelations = relations(
	badPracticeDetection,
	({ one, many }) => ({
		issue: one(issue, {
			fields: [badPracticeDetection.pullrequestId],
			references: [issue.id],
		}),
		pullrequestbadpractices: many(pullrequestbadpractice),
	}),
);

export const badPracticeFeedbackRelations = relations(
	badPracticeFeedback,
	({ one }) => ({
		pullrequestbadpractice: one(pullrequestbadpractice, {
			fields: [badPracticeFeedback.pullRequestBadPracticeId],
			references: [pullrequestbadpractice.id],
		}),
	}),
);

export const pullrequestbadpracticeRelations = relations(
	pullrequestbadpractice,
	({ one, many }) => ({
		badPracticeFeedbacks: many(badPracticeFeedback),
		issue: one(issue, {
			fields: [pullrequestbadpractice.pullrequestId],
			references: [issue.id],
		}),
		badPracticeDetection: one(badPracticeDetection, {
			fields: [pullrequestbadpractice.badPracticeDetectionId],
			references: [badPracticeDetection.id],
		}),
	}),
);

export const chatMessageRelations = relations(chatMessage, ({ one, many }) => ({
	chatThread: one(chatThread, {
		fields: [chatMessage.threadId],
		references: [chatThread.id],
		relationName: "chatMessage_threadId_chatThread_id",
	}),
	chatMessage: one(chatMessage, {
		fields: [chatMessage.parentMessageId],
		references: [chatMessage.id],
		relationName: "chatMessage_parentMessageId_chatMessage_id",
	}),
	chatMessages: many(chatMessage, {
		relationName: "chatMessage_parentMessageId_chatMessage_id",
	}),
	chatThreads: many(chatThread, {
		relationName: "chatThread_selectedLeafMessageId_chatMessage_id",
	}),
	chatMessageParts: many(chatMessagePart),
}));

export const chatThreadRelations = relations(chatThread, ({ one, many }) => ({
	chatMessages: many(chatMessage, {
		relationName: "chatMessage_threadId_chatThread_id",
	}),
	chatMessage: one(chatMessage, {
		fields: [chatThread.selectedLeafMessageId],
		references: [chatMessage.id],
		relationName: "chatThread_selectedLeafMessageId_chatMessage_id",
	}),
	user: one(user, {
		fields: [chatThread.userId],
		references: [user.id],
	}),
}));

export const issueLabelRelations = relations(issueLabel, ({ one }) => ({
	issue: one(issue, {
		fields: [issueLabel.issueId],
		references: [issue.id],
	}),
	label: one(label, {
		fields: [issueLabel.labelId],
		references: [label.id],
	}),
}));

export const issueAssigneeRelations = relations(issueAssignee, ({ one }) => ({
	user: one(user, {
		fields: [issueAssignee.userId],
		references: [user.id],
	}),
	issue: one(issue, {
		fields: [issueAssignee.issueId],
		references: [issue.id],
	}),
}));

export const pullRequestRequestedReviewersRelations = relations(
	pullRequestRequestedReviewers,
	({ one }) => ({
		issue: one(issue, {
			fields: [pullRequestRequestedReviewers.pullRequestId],
			references: [issue.id],
		}),
		user: one(user, {
			fields: [pullRequestRequestedReviewers.userId],
			references: [user.id],
		}),
	}),
);

export const teamLabelsRelations = relations(teamLabels, ({ one }) => ({
	team: one(team, {
		fields: [teamLabels.teamId],
		references: [team.id],
	}),
	label: one(label, {
		fields: [teamLabels.labelId],
		references: [label.id],
	}),
}));

export const teamRelations = relations(team, ({ many }) => ({
	teamLabels: many(teamLabels),
	teamMemberships: many(teamMembership),
	teamRepositoryPermissions: many(teamRepositoryPermission),
}));

export const teamMembershipRelations = relations(teamMembership, ({ one }) => ({
	user: one(user, {
		fields: [teamMembership.userId],
		references: [user.id],
	}),
	team: one(team, {
		fields: [teamMembership.teamId],
		references: [team.id],
	}),
}));

export const teamRepositoryPermissionRelations = relations(
	teamRepositoryPermission,
	({ one }) => ({
		team: one(team, {
			fields: [teamRepositoryPermission.teamId],
			references: [team.id],
		}),
		repository: one(repository, {
			fields: [teamRepositoryPermission.repositoryId],
			references: [repository.id],
		}),
	}),
);

export const chatMessagePartRelations = relations(
	chatMessagePart,
	({ one }) => ({
		chatMessage: one(chatMessage, {
			fields: [chatMessagePart.messageId],
			references: [chatMessage.id],
		}),
	}),
);

export const documentRelations = relations(document, ({ one }) => ({
	user: one(user, {
		fields: [document.userId],
		references: [user.id],
	}),
}));
