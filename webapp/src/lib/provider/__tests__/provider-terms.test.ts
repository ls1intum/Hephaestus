import { describe, expect, it } from "vitest";
import { getProviderTerms } from "../provider-terms";

describe("getProviderTerms", () => {
	it("returns GitHub terminology", () => {
		const terms = getProviderTerms("GITHUB");
		expect(terms.pullRequest).toBe("Pull Request");
		expect(terms.pullRequests).toBe("Pull Requests");
		expect(terms.pr).toBe("PR");
		expect(terms.prs).toBe("PRs");
		expect(terms.repository).toBe("Repository");
		expect(terms.repositories).toBe("Repositories");
		expect(terms.organization).toBe("Organization");
	});

	it("returns GitLab terminology", () => {
		const terms = getProviderTerms("GITLAB");
		expect(terms.pullRequest).toBe("Merge Request");
		expect(terms.pullRequests).toBe("Merge Requests");
		expect(terms.pr).toBe("MR");
		expect(terms.prs).toBe("MRs");
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
});
