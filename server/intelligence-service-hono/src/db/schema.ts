import { pgTable, varchar, timestamp, integer, boolean, foreignKey, bigint, text, smallint, uuid, type AnyPgColumn, jsonb, unique, primaryKey, index } from "drizzle-orm/pg-core"
import { sql } from "drizzle-orm"



export const databasechangelog = pgTable("databasechangelog", {
	id: varchar({ length: 255 }).notNull(),
	author: varchar({ length: 255 }).notNull(),
	filename: varchar({ length: 255 }).notNull(),
	dateexecuted: timestamp({ mode: 'string' }).notNull(),
	orderexecuted: integer().notNull(),
	exectype: varchar({ length: 10 }).notNull(),
	md5Sum: varchar({ length: 35 }),
	description: varchar({ length: 255 }),
	comments: varchar({ length: 255 }),
	tag: varchar({ length: 255 }),
	liquibase: varchar({ length: 20 }),
	contexts: varchar({ length: 255 }),
	labels: varchar({ length: 255 }),
	deploymentId: varchar("deployment_id", { length: 10 }),
});

export const databasechangeloglock = pgTable("databasechangeloglock", {
	id: integer().primaryKey().notNull(),
	locked: boolean().notNull(),
	lockgranted: timestamp({ mode: 'string' }),
	lockedby: varchar({ length: 255 }),
});

export const pullRequestReview = pgTable("pull_request_review", {
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	id: bigint({ mode: "number" }).primaryKey().notNull(),
	// TODO: failed to parse database type 'oid'
	body: unknown("body"),
	commitId: varchar("commit_id", { length: 255 }),
	htmlUrl: varchar("html_url", { length: 255 }),
	isDismissed: boolean("is_dismissed").notNull(),
	state: varchar({ length: 255 }),
	submittedAt: timestamp("submitted_at", { withTimezone: true, mode: 'string' }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	authorId: bigint("author_id", { mode: "number" }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	pullRequestId: bigint("pull_request_id", { mode: "number" }),
}, (table) => [
	foreignKey({
			columns: [table.authorId],
			foreignColumns: [user.id],
			name: "fkeehfcwrodfu61gremlcvhgir5"
		}),
	foreignKey({
			columns: [table.pullRequestId],
			foreignColumns: [issue.id],
			name: "fkio96gq2jetvy6a4in9nl8vkvd"
		}),
]);

export const user = pgTable("user", {
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	id: bigint({ mode: "number" }).primaryKey().notNull(),
	createdAt: timestamp("created_at", { withTimezone: true, mode: 'string' }),
	updatedAt: timestamp("updated_at", { withTimezone: true, mode: 'string' }),
	avatarUrl: varchar("avatar_url", { length: 255 }),
	blog: varchar({ length: 255 }),
	company: varchar({ length: 255 }),
	description: varchar({ length: 255 }),
	email: varchar({ length: 255 }),
	followers: integer().notNull(),
	following: integer().notNull(),
	htmlUrl: varchar("html_url", { length: 255 }),
	location: varchar({ length: 255 }),
	login: varchar({ length: 255 }),
	name: varchar({ length: 255 }),
	type: varchar({ length: 255 }),
	leaguePoints: integer("league_points").default(0).notNull(),
	notificationsEnabled: boolean("notifications_enabled").default(true).notNull(),
});

export const issueComment = pgTable("issue_comment", {
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	id: bigint({ mode: "number" }).primaryKey().notNull(),
	createdAt: timestamp("created_at", { withTimezone: true, mode: 'string' }),
	updatedAt: timestamp("updated_at", { withTimezone: true, mode: 'string' }),
	authorAssociation: varchar("author_association", { length: 255 }),
	// TODO: failed to parse database type 'oid'
	body: unknown("body"),
	htmlUrl: varchar("html_url", { length: 255 }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	authorId: bigint("author_id", { mode: "number" }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	issueId: bigint("issue_id", { mode: "number" }),
}, (table) => [
	foreignKey({
			columns: [table.issueId],
			foreignColumns: [issue.id],
			name: "fk8wy5rxggrte2ntcq80g7o7210"
		}),
	foreignKey({
			columns: [table.authorId],
			foreignColumns: [user.id],
			name: "fkdy6oeojymud1wna20olqgyt31"
		}),
]);

export const milestone = pgTable("milestone", {
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	id: bigint({ mode: "number" }).primaryKey().notNull(),
	createdAt: timestamp("created_at", { withTimezone: true, mode: 'string' }),
	updatedAt: timestamp("updated_at", { withTimezone: true, mode: 'string' }),
	closedAt: timestamp("closed_at", { withTimezone: true, mode: 'string' }),
	// TODO: failed to parse database type 'oid'
	description: unknown("description"),
	dueOn: timestamp("due_on", { withTimezone: true, mode: 'string' }),
	htmlUrl: varchar("html_url", { length: 255 }),
	number: integer().notNull(),
	state: varchar({ length: 255 }),
	title: varchar({ length: 255 }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	creatorId: bigint("creator_id", { mode: "number" }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	repositoryId: bigint("repository_id", { mode: "number" }),
}, (table) => [
	foreignKey({
			columns: [table.repositoryId],
			foreignColumns: [repository.id],
			name: "fkbjhs37s6qmqtd330gu9mit6w0"
		}),
	foreignKey({
			columns: [table.creatorId],
			foreignColumns: [user.id],
			name: "fkg6ieho7gomiumy85puy6l13f1"
		}),
]);

export const repository = pgTable("repository", {
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	id: bigint({ mode: "number" }).primaryKey().notNull(),
	createdAt: timestamp("created_at", { withTimezone: true, mode: 'string' }),
	updatedAt: timestamp("updated_at", { withTimezone: true, mode: 'string' }),
	defaultBranch: varchar("default_branch", { length: 255 }),
	description: varchar({ length: 255 }),
	hasIssues: boolean("has_issues").notNull(),
	hasProjects: boolean("has_projects").notNull(),
	hasWiki: boolean("has_wiki").notNull(),
	homepage: varchar({ length: 255 }),
	htmlUrl: varchar("html_url", { length: 255 }),
	isArchived: boolean("is_archived").notNull(),
	isDisabled: boolean("is_disabled").notNull(),
	isPrivate: boolean("is_private").notNull(),
	name: varchar({ length: 255 }),
	nameWithOwner: varchar("name_with_owner", { length: 255 }),
	pushedAt: timestamp("pushed_at", { withTimezone: true, mode: 'string' }),
	stargazersCount: integer("stargazers_count").notNull(),
	visibility: varchar({ length: 255 }),
	watchersCount: integer("watchers_count").notNull(),
});

export const pullRequestReviewComment = pgTable("pull_request_review_comment", {
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	id: bigint({ mode: "number" }).primaryKey().notNull(),
	createdAt: timestamp("created_at", { withTimezone: true, mode: 'string' }),
	updatedAt: timestamp("updated_at", { withTimezone: true, mode: 'string' }),
	authorAssociation: varchar("author_association", { length: 255 }),
	// TODO: failed to parse database type 'oid'
	body: unknown("body"),
	commitId: varchar("commit_id", { length: 255 }),
	// TODO: failed to parse database type 'oid'
	diffHunk: unknown("diff_hunk"),
	htmlUrl: varchar("html_url", { length: 255 }),
	line: integer().notNull(),
	originalCommitId: varchar("original_commit_id", { length: 255 }),
	originalLine: integer("original_line").notNull(),
	originalPosition: integer("original_position").notNull(),
	originalStartLine: integer("original_start_line"),
	path: varchar({ length: 255 }),
	position: integer().notNull(),
	side: varchar({ length: 255 }),
	startLine: integer("start_line"),
	startSide: varchar("start_side", { length: 255 }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	authorId: bigint("author_id", { mode: "number" }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	pullRequestId: bigint("pull_request_id", { mode: "number" }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	reviewId: bigint("review_id", { mode: "number" }),
}, (table) => [
	foreignKey({
			columns: [table.reviewId],
			foreignColumns: [pullRequestReview.id],
			name: "fkbx1g5jpdegymhyv9pbk2jdgfw"
		}),
	foreignKey({
			columns: [table.pullRequestId],
			foreignColumns: [issue.id],
			name: "fkohqvdiswptbm0h8cniq7r1tgq"
		}),
	foreignKey({
			columns: [table.authorId],
			foreignColumns: [user.id],
			name: "fktl08ieowbl171xem2bciho7kw"
		}),
]);

export const issue = pgTable("issue", {
	issueType: varchar("issue_type", { length: 31 }).notNull(),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	id: bigint({ mode: "number" }).primaryKey().notNull(),
	createdAt: timestamp("created_at", { withTimezone: true, mode: 'string' }),
	updatedAt: timestamp("updated_at", { withTimezone: true, mode: 'string' }),
	// TODO: failed to parse database type 'oid'
	body: unknown("body"),
	closedAt: timestamp("closed_at", { withTimezone: true, mode: 'string' }),
	commentsCount: integer("comments_count").notNull(),
	htmlUrl: varchar("html_url", { length: 255 }),
	isLocked: boolean("is_locked").notNull(),
	number: integer().notNull(),
	state: varchar({ length: 255 }),
	title: varchar({ length: 255 }),
	additions: integer(),
	changedFiles: integer("changed_files"),
	commits: integer(),
	deletions: integer(),
	isDraft: boolean("is_draft"),
	isMergeable: boolean("is_mergeable"),
	isMerged: boolean("is_merged"),
	maintainerCanModify: boolean("maintainer_can_modify"),
	mergeCommitSha: varchar("merge_commit_sha", { length: 255 }),
	mergeableState: varchar("mergeable_state", { length: 255 }),
	mergedAt: timestamp("merged_at", { withTimezone: true, mode: 'string' }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	authorId: bigint("author_id", { mode: "number" }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	milestoneId: bigint("milestone_id", { mode: "number" }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	repositoryId: bigint("repository_id", { mode: "number" }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	mergedById: bigint("merged_by_id", { mode: "number" }),
	hasPullRequest: boolean("has_pull_request").notNull(),
	lastSyncAt: timestamp("last_sync_at", { precision: 6, mode: 'string' }),
	// TODO: failed to parse database type 'oid'
	badPracticeSummary: unknown("bad_practice_summary"),
	lastDetectionTime: timestamp("last_detection_time", { precision: 6, withTimezone: true, mode: 'string' }),
}, (table) => [
	foreignKey({
			columns: [table.repositoryId],
			foreignColumns: [repository.id],
			name: "fk76s4b6ncspm9bk35y49xh4s9t"
		}),
	foreignKey({
			columns: [table.milestoneId],
			foreignColumns: [milestone.id],
			name: "fk7t1o4tuel06m9bn4dppqmiod6"
		}),
	foreignKey({
			columns: [table.mergedById],
			foreignColumns: [user.id],
			name: "fkqvnu6vslj5txt8xencru8m6x4"
		}),
	foreignKey({
			columns: [table.authorId],
			foreignColumns: [user.id],
			name: "fkrwr6v8fiqetuiuvfjcvie8s85"
		}),
]);

export const label = pgTable("label", {
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	id: bigint({ mode: "number" }).primaryKey().notNull(),
	color: varchar({ length: 255 }),
	description: varchar({ length: 255 }),
	name: varchar({ length: 255 }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	repositoryId: bigint("repository_id", { mode: "number" }),
}, (table) => [
	foreignKey({
			columns: [table.repositoryId],
			foreignColumns: [repository.id],
			name: "fk2951edbl9g9y8ee1q97e2ff75"
		}),
]);

export const team = pgTable("team", {
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	id: bigint({ mode: "number" }).primaryKey().generatedByDefaultAsIdentity({ name: "team_id_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 9223372036854775807, cache: 1 }),
	name: varchar({ length: 255 }),
	hidden: boolean().default(false).notNull(),
	createdAt: timestamp("created_at", { precision: 6, withTimezone: true, mode: 'string' }),
	description: text(),
	htmlUrl: text("html_url"),
	lastSyncedAt: timestamp("last_synced_at", { precision: 6, withTimezone: true, mode: 'string' }),
	organization: varchar({ length: 255 }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	parentId: bigint("parent_id", { mode: "number" }),
	privacy: varchar({ length: 32 }),
	updatedAt: timestamp("updated_at", { precision: 6, withTimezone: true, mode: 'string' }),
});

export const workspace = pgTable("workspace", {
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	id: bigint({ mode: "number" }).primaryKey().generatedByDefaultAsIdentity({ name: "workspace_id_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 9223372036854775807, cache: 1 }),
	usersSyncedAt: timestamp("users_synced_at", { precision: 6, mode: 'string' }),
});

export const repositoryToMonitor = pgTable("repository_to_monitor", {
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	id: bigint({ mode: "number" }).primaryKey().generatedByDefaultAsIdentity({ name: "repository_to_monitor_id_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 9223372036854775807, cache: 1 }),
	issuesAndPullRequestsSyncedAt: timestamp("issues_and_pull_requests_synced_at", { precision: 6, mode: 'string' }),
	labelsSyncedAt: timestamp("labels_synced_at", { precision: 6, mode: 'string' }),
	milestonesSyncedAt: timestamp("milestones_synced_at", { precision: 6, mode: 'string' }),
	nameWithOwner: varchar("name_with_owner", { length: 255 }),
	repositorySyncedAt: timestamp("repository_synced_at", { precision: 6, mode: 'string' }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	workspaceId: bigint("workspace_id", { mode: "number" }),
}, (table) => [
	foreignKey({
			columns: [table.workspaceId],
			foreignColumns: [workspace.id],
			name: "FKdkxnkm4a2wyw0d5k63gh2st64"
		}),
]);

export const pullrequestbadpractice = pgTable("pullrequestbadpractice", {
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	id: bigint({ mode: "number" }).primaryKey().generatedByDefaultAsIdentity({ name: "pullrequestbadpractice_id_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 9223372036854775807, cache: 1 }),
	// TODO: failed to parse database type 'oid'
	description: unknown("description"),
	title: varchar({ length: 255 }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	pullrequestId: bigint("pullrequest_id", { mode: "number" }),
	state: smallint().default(0),
	detectionTime: timestamp("detection_time", { precision: 6, withTimezone: true, mode: 'string' }),
	lastUpdateTime: timestamp("last_update_time", { precision: 6, withTimezone: true, mode: 'string' }),
	userState: smallint("user_state"),
	detectionPullrequestLifecycleState: smallint("detection_pullrequest_lifecycle_state"),
	detectionTraceId: varchar("detection_trace_id", { length: 255 }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	badPracticeDetectionId: bigint("bad_practice_detection_id", { mode: "number" }),
}, (table) => [
	foreignKey({
			columns: [table.pullrequestId],
			foreignColumns: [issue.id],
			name: "FK1m1jhw92ublt7ya0d557sg5j"
		}),
	foreignKey({
			columns: [table.badPracticeDetectionId],
			foreignColumns: [badPracticeDetection.id],
			name: "FKdn50l1oul09kq3142ku39gnlp"
		}),
]);

export const badPracticeFeedback = pgTable("bad_practice_feedback", {
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	id: bigint({ mode: "number" }).primaryKey().generatedByDefaultAsIdentity({ name: "bad_practice_feedback_id_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 9223372036854775807, cache: 1 }),
	// TODO: failed to parse database type 'oid'
	explanation: unknown("explanation"),
	type: varchar({ length: 255 }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	pullRequestBadPracticeId: bigint("pull_request_bad_practice_id", { mode: "number" }),
	creationTime: timestamp("creation_time", { precision: 6, withTimezone: true, mode: 'string' }),
}, (table) => [
	foreignKey({
			columns: [table.pullRequestBadPracticeId],
			foreignColumns: [pullrequestbadpractice.id],
			name: "FK34k5tg4qb6gy4g7tn9q8uhogl"
		}),
]);

export const badPracticeDetection = pgTable("bad_practice_detection", {
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	id: bigint({ mode: "number" }).primaryKey().generatedByDefaultAsIdentity({ name: "bad_practice_detection_id_seq", startWith: 1, increment: 1, minValue: 1, maxValue: 9223372036854775807, cache: 1 }),
	detectionTime: timestamp("detection_time", { precision: 6, withTimezone: true, mode: 'string' }),
	// TODO: failed to parse database type 'oid'
	summary: unknown("summary"),
	traceId: varchar("trace_id", { length: 255 }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	pullrequestId: bigint("pullrequest_id", { mode: "number" }),
}, (table) => [
	foreignKey({
			columns: [table.pullrequestId],
			foreignColumns: [issue.id],
			name: "FKhk2vrsr2rdq2gb3cjnvieh3nw"
		}),
]);

export const chatMessageVote = pgTable("chat_message_vote", {
	messageId: uuid("message_id").primaryKey().notNull(),
	createdAt: timestamp("created_at", { precision: 6, withTimezone: true, mode: 'string' }).notNull(),
	isUpvoted: boolean("is_upvoted").notNull(),
	updatedAt: timestamp("updated_at", { precision: 6, withTimezone: true, mode: 'string' }).notNull(),
});

export const chatMessage = pgTable("chat_message", {
	id: uuid().primaryKey().notNull(),
	createdAt: timestamp("created_at", { precision: 6, withTimezone: true, mode: 'string' }).notNull(),
	metadata: jsonb(),
	role: varchar({ length: 16 }).notNull(),
	parentMessageId: uuid("parent_message_id"),
	threadId: uuid("thread_id").notNull(),
}, (table) => [
	foreignKey({
			columns: [table.threadId],
			foreignColumns: [chatThread.id],
			name: "FK8s34d909gxc4xrlvml8gag9kh"
		}),
	foreignKey({
			columns: [table.parentMessageId],
			foreignColumns: [table.id],
			name: "FKd0fewjs0l68rq2bww9h8o4cmb"
		}),
]);

export const chatThread = pgTable("chat_thread", {
	id: uuid().primaryKey().notNull(),
	createdAt: timestamp("created_at", { precision: 6, withTimezone: true, mode: 'string' }).notNull(),
	title: text(),
	selectedLeafMessageId: uuid("selected_leaf_message_id"),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	userId: bigint("user_id", { mode: "number" }),
}, (table) => [
	foreignKey({
			columns: [table.selectedLeafMessageId],
			foreignColumns: [chatMessage.id],
			name: "FK34beodgwi0g7kn66svlk4hlfr"
		}),
	foreignKey({
			columns: [table.userId],
			foreignColumns: [user.id],
			name: "FKikdxlx9viomcwrgxj7fbyfsew"
		}),
	unique("uc_chat_threadselected_leaf_message_id_col").on(table.selectedLeafMessageId),
]);

export const issueLabel = pgTable("issue_label", {
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	issueId: bigint("issue_id", { mode: "number" }).notNull(),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	labelId: bigint("label_id", { mode: "number" }).notNull(),
}, (table) => [
	foreignKey({
			columns: [table.issueId],
			foreignColumns: [issue.id],
			name: "fkit5n9c0frugu5m8xqsxtps63r"
		}),
	foreignKey({
			columns: [table.labelId],
			foreignColumns: [label.id],
			name: "fkxbk5rr30kkb6k4ech7x4vh9h"
		}),
	primaryKey({ columns: [table.issueId, table.labelId], name: "issue_label_pkey"}),
]);

export const issueAssignee = pgTable("issue_assignee", {
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	issueId: bigint("issue_id", { mode: "number" }).notNull(),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	userId: bigint("user_id", { mode: "number" }).notNull(),
}, (table) => [
	foreignKey({
			columns: [table.userId],
			foreignColumns: [user.id],
			name: "fk2cfu8w8wjb9vosy4hbrme0rqe"
		}),
	foreignKey({
			columns: [table.issueId],
			foreignColumns: [issue.id],
			name: "fkocgmsva4p5e8ic9k5dbjqa15u"
		}),
	primaryKey({ columns: [table.issueId, table.userId], name: "issue_assignee_pkey"}),
]);

export const pullRequestRequestedReviewers = pgTable("pull_request_requested_reviewers", {
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	pullRequestId: bigint("pull_request_id", { mode: "number" }).notNull(),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	userId: bigint("user_id", { mode: "number" }).notNull(),
}, (table) => [
	foreignKey({
			columns: [table.pullRequestId],
			foreignColumns: [issue.id],
			name: "fk6dld06xx8rh9xhqfnca070a0i"
		}),
	foreignKey({
			columns: [table.userId],
			foreignColumns: [user.id],
			name: "fkioq4g5aksr97l6qyl4g5l63tn"
		}),
	primaryKey({ columns: [table.pullRequestId, table.userId], name: "pull_request_requested_reviewers_pkey"}),
]);

export const teamLabels = pgTable("team_labels", {
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	teamId: bigint("team_id", { mode: "number" }).notNull(),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	labelId: bigint("label_id", { mode: "number" }).notNull(),
}, (table) => [
	foreignKey({
			columns: [table.teamId],
			foreignColumns: [team.id],
			name: "FK3f9iwbjmf3gyflex7xoofnnbh"
		}),
	foreignKey({
			columns: [table.labelId],
			foreignColumns: [label.id],
			name: "FKa8aajjyqj8uwnqtrrbg3a9v5o"
		}),
	primaryKey({ columns: [table.teamId, table.labelId], name: "team_labelsPK"}),
]);

export const teamMembership = pgTable("team_membership", {
	role: varchar({ length: 32 }),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	userId: bigint("user_id", { mode: "number" }).notNull(),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	teamId: bigint("team_id", { mode: "number" }).notNull(),
}, (table) => [
	foreignKey({
			columns: [table.userId],
			foreignColumns: [user.id],
			name: "FKnkpwi3whks92uvhn5qe71v4k6"
		}),
	foreignKey({
			columns: [table.teamId],
			foreignColumns: [team.id],
			name: "FKrf92vmiawfvyhxcmigcg10opm"
		}),
	primaryKey({ columns: [table.userId, table.teamId], name: "team_membershipPK"}),
]);

export const teamRepositoryPermission = pgTable("team_repository_permission", {
	permission: varchar({ length: 32 }).notNull(),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	repositoryId: bigint("repository_id", { mode: "number" }).notNull(),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	teamId: bigint("team_id", { mode: "number" }).notNull(),
}, (table) => [
	foreignKey({
			columns: [table.teamId],
			foreignColumns: [team.id],
			name: "FK7qxvqq8p6690vtdux47lsg8b1"
		}),
	foreignKey({
			columns: [table.repositoryId],
			foreignColumns: [repository.id],
			name: "FK92gtctw6ca02527qjja7gns9f"
		}),
	primaryKey({ columns: [table.repositoryId, table.teamId], name: "team_repository_permissionPK"}),
]);

export const chatMessagePart = pgTable("chat_message_part", {
	messageId: uuid("message_id").notNull(),
	orderIndex: integer("order_index").notNull(),
	content: jsonb(),
	originalType: varchar("original_type", { length: 128 }),
	type: varchar({ length: 32 }).notNull(),
}, (table) => [
	foreignKey({
			columns: [table.messageId],
			foreignColumns: [chatMessage.id],
			name: "FKkfle3niou3f9r63mc3u8vi1na"
		}),
	primaryKey({ columns: [table.messageId, table.orderIndex], name: "chat_message_partPK"}),
]);

export const document = pgTable("document", {
	id: uuid().notNull(),
	versionNumber: integer("version_number").notNull(),
	content: text(),
	createdAt: timestamp("created_at", { precision: 6, withTimezone: true, mode: 'string' }).notNull(),
	kind: varchar({ length: 255 }).notNull(),
	title: varchar({ length: 255 }).notNull(),
	// You can use { mode: "bigint" } if numbers are exceeding js number limitations
	userId: bigint("user_id", { mode: "number" }).notNull(),
}, (table) => [
	index("idx_document_created_at").using("btree", table.createdAt.asc().nullsLast().op("timestamptz_ops")),
	index("idx_document_id").using("btree", table.id.asc().nullsLast().op("uuid_ops")),
	index("idx_document_user_id").using("btree", table.userId.asc().nullsLast().op("int8_ops")),
	foreignKey({
			columns: [table.userId],
			foreignColumns: [user.id],
			name: "FKjhdxdv9sijhujiynqbb5jc010"
		}),
	primaryKey({ columns: [table.id, table.versionNumber], name: "documentPK"}),
]);
