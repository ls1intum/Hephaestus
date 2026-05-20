import { describe, expect, it } from "vitest";
import { buildGitLabSubject } from "@/utils/gitlab-subject";

describe("buildGitLabSubject", () => {
	it("should build subject from project path_with_namespace", () => {
		const payload = {
			object_kind: "push",
			project: {
				path_with_namespace: "group/subgroup/myproject",
			},
		};

		expect(buildGitLabSubject(payload)).toBe("gitlab.group~subgroup.myproject.push");
	});

	it("should build subject from top-level path_with_namespace", () => {
		const payload = {
			event_name: "merge_request",
			path_with_namespace: "myorg/repo",
		};

		expect(buildGitLabSubject(payload)).toBe("gitlab.myorg.repo.merge_request");
	});

	it("should handle group-scoped events", () => {
		const payload = {
			object_kind: "group_member",
			group: {
				full_path: "parent/child",
			},
		};

		expect(buildGitLabSubject(payload)).toBe("gitlab.parent~child.?.group_member");
	});

	it("should parse from object_attributes url", () => {
		const payload = {
			object_kind: "note",
			object_attributes: {
				project_id: 123,
				url: "https://gitlab.lrz.de/ga84xah/codereviewtest/-/merge_requests/1#note_4108500",
			},
		};

		expect(buildGitLabSubject(payload)).toBe("gitlab.ga84xah.codereviewtest.note");
	});

	it("should sanitize dots in paths", () => {
		const payload = {
			object_kind: "push",
			project: {
				path_with_namespace: "my.org/my.repo",
			},
		};

		expect(buildGitLabSubject(payload)).toBe("gitlab.my~org.my~repo.push");
	});

	it("should fallback to instance-level for unknown payloads", () => {
		const payload = {};

		expect(buildGitLabSubject(payload)).toBe("gitlab.?.?.unknown");
	});

	it("should use object_kind over event_name", () => {
		const payload = {
			object_kind: "issue",
			event_name: "issue_open",
			project: {
				path_with_namespace: "org/repo",
			},
		};

		expect(buildGitLabSubject(payload)).toBe("gitlab.org.repo.issue");
	});

	describe("special characters in paths", () => {
		it("should preserve wildcards", () => {
			const payload = {
				object_kind: "push",
				project: {
					path_with_namespace: "org*/repo",
				},
			};
			expect(buildGitLabSubject(payload)).toBe("gitlab.org*.repo.push");
		});

		it("should preserve unicode", () => {
			const payload = {
				object_kind: "push",
				project: {
					path_with_namespace: "org🚀/repo📦",
				},
			};
			expect(buildGitLabSubject(payload)).toBe("gitlab.org🚀.repo📦.push");
		});

		it("should preserve special characters", () => {
			const payload = {
				object_kind: "push",
				project: {
					path_with_namespace: "café/naïve",
				},
			};
			expect(buildGitLabSubject(payload)).toBe("gitlab.café.naïve.push");
		});

		it("should handle empty namespace with ? placeholder", () => {
			const payload = {
				object_kind: "push",
				project: {
					path_with_namespace: "",
				},
			};
			expect(buildGitLabSubject(payload)).toBe("gitlab.?.?.push");
		});

		it("should handle very long namespace paths", () => {
			const longName = "a".repeat(1000);
			const payload = {
				object_kind: "push",
				project: {
					path_with_namespace: `${longName}/${longName}`,
				},
			};
			const subject = buildGitLabSubject(payload);
			expect(subject).toContain("gitlab.");
			expect(subject.endsWith(".push")).toBe(true);
		});
	});
});
