import {
	GitMergeIcon,
	GitPullRequestClosedIcon,
	GitPullRequestDraftIcon,
	GitPullRequestIcon,
} from "@primer/octicons-react";
import { describe, expect, it } from "vitest";
import {
	GitLabMergeIcon,
	GitLabMergeRequestClosedIcon,
	GitLabMergeRequestDraftIcon,
	GitLabMergeRequestIcon,
} from "../gitlab-icons";
import { getPullRequestStateIcon } from "../provider-icons";

describe("getPullRequestStateIcon", () => {
	describe("GitHub provider", () => {
		it("returns GitPullRequestIcon with open-foreground color", () => {
			const result = getPullRequestStateIcon("GITHUB", "OPEN");
			expect(result.icon).toBe(GitPullRequestIcon);
			expect(result.colorClass).toBe("text-provider-open-foreground");
		});

		it("returns GitPullRequestDraftIcon with muted-foreground color when open + draft", () => {
			const result = getPullRequestStateIcon("GITHUB", "OPEN", true);
			expect(result.icon).toBe(GitPullRequestDraftIcon);
			expect(result.colorClass).toBe("text-provider-muted-foreground");
		});

		it("returns GitMergeIcon with done-foreground color", () => {
			const result = getPullRequestStateIcon("GITHUB", "MERGED");
			expect(result.icon).toBe(GitMergeIcon);
			expect(result.colorClass).toBe("text-provider-done-foreground");
		});

		it("returns GitPullRequestClosedIcon with closed-foreground color", () => {
			const result = getPullRequestStateIcon("GITHUB", "CLOSED");
			expect(result.icon).toBe(GitPullRequestClosedIcon);
			expect(result.colorClass).toBe("text-provider-closed-foreground");
		});

		it("ignores isDraft when state is not OPEN", () => {
			const merged = getPullRequestStateIcon("GITHUB", "MERGED", true);
			expect(merged.icon).toBe(GitMergeIcon);
			const closed = getPullRequestStateIcon("GITHUB", "CLOSED", true);
			expect(closed.icon).toBe(GitPullRequestClosedIcon);
		});
	});

	describe("GitLab provider", () => {
		it("returns GitLab open MR icon with open-foreground color", () => {
			const result = getPullRequestStateIcon("GITLAB", "OPEN");
			expect(result.icon).toBe(GitLabMergeRequestIcon);
			expect(result.colorClass).toBe("text-provider-open-foreground");
		});

		it("returns GitLab draft MR icon with muted-foreground color", () => {
			const result = getPullRequestStateIcon("GITLAB", "OPEN", true);
			expect(result.icon).toBe(GitLabMergeRequestDraftIcon);
			expect(result.colorClass).toBe("text-provider-muted-foreground");
		});

		it("returns GitLab merged icon with done-foreground color", () => {
			const result = getPullRequestStateIcon("GITLAB", "MERGED");
			expect(result.icon).toBe(GitLabMergeIcon);
			expect(result.colorClass).toBe("text-provider-done-foreground");
		});

		it("returns GitLab closed MR icon with closed-foreground color", () => {
			const result = getPullRequestStateIcon("GITLAB", "CLOSED");
			expect(result.icon).toBe(GitLabMergeRequestClosedIcon);
			expect(result.colorClass).toBe("text-provider-closed-foreground");
		});
	});

	it("uses same color classes for both providers", () => {
		const states: Array<"OPEN" | "CLOSED" | "MERGED"> = ["OPEN", "CLOSED", "MERGED"];
		for (const state of states) {
			const gh = getPullRequestStateIcon("GITHUB", state);
			const gl = getPullRequestStateIcon("GITLAB", state);
			expect(gh.colorClass).toBe(gl.colorClass);
		}
	});

	it("uses different icon components per provider", () => {
		const states: Array<"OPEN" | "CLOSED" | "MERGED"> = ["OPEN", "CLOSED", "MERGED"];
		for (const state of states) {
			const gh = getPullRequestStateIcon("GITHUB", state);
			const gl = getPullRequestStateIcon("GITLAB", state);
			expect(gh.icon).not.toBe(gl.icon);
		}
	});
});
