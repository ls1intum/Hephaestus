import { describe, expect, it } from "vitest";
import type { ProviderType } from "../provider-terms";
import { getProviderSlug, getProviderTerms } from "../provider-terms";

describe("getProviderTerms", () => {
	it("returns GitHub terminology", () => {
		const terms = getProviderTerms("GITHUB");
		expect(terms.displayName).toBe("GitHub");
		expect(terms.pullRequest).toBe("Pull Request");
		expect(terms.pullRequests).toBe("Pull Requests");
		expect(terms.pullRequestShort).toBe("PR");
		expect(terms.pullRequestsShort).toBe("PRs");
		expect(terms.repository).toBe("Repository");
		expect(terms.repositories).toBe("Repositories");
		expect(terms.organization).toBe("Organization");
	});

	it("returns GitLab terminology", () => {
		const terms = getProviderTerms("GITLAB");
		expect(terms.displayName).toBe("GitLab");
		expect(terms.pullRequest).toBe("Merge Request");
		expect(terms.pullRequests).toBe("Merge Requests");
		expect(terms.pullRequestShort).toBe("MR");
		expect(terms.pullRequestsShort).toBe("MRs");
		expect(terms.repository).toBe("Project");
		expect(terms.repositories).toBe("Projects");
		expect(terms.organization).toBe("Group");
	});

	it("GitHub and GitLab terms differ for all keys", () => {
		const github = getProviderTerms("GITHUB");
		const gitlab = getProviderTerms("GITLAB");
		for (const key of Object.keys(github) as (keyof typeof github)[]) {
			expect(github[key]).not.toBe(gitlab[key]);
		}
	});

	it("covers all provider types", () => {
		const providers: ProviderType[] = ["GITHUB", "GITLAB"];
		for (const provider of providers) {
			expect(getProviderTerms(provider)).toBeDefined();
		}
	});
});

describe("getProviderSlug", () => {
	it("converts GITHUB to github", () => {
		expect(getProviderSlug("GITHUB")).toBe("github");
	});

	it("converts GITLAB to gitlab", () => {
		expect(getProviderSlug("GITLAB")).toBe("gitlab");
	});
});
