import { describe, expect, it } from "vitest";

import { workLinks } from "~/shared/web-app-links";

const APP = "https://heph.example.test";

describe("web app links", () => {
	it("leads every reader to their feedback on this work in the workspace that answered", () => {
		expect(
			workLinks(APP, "intro course", { kind: "scm.pull_request", id: "4009" }, false),
		).toStrictEqual({
			trace: `${APP}/w/intro%20course/feedback/scm.pull_request/4009`,
		});
	});

	it("adds this work's own reviews for a workspace admin, with its sheet open", () => {
		expect(workLinks(APP, "intro", { kind: "scm.issue", id: "12" }, true)).toStrictEqual({
			trace: `${APP}/w/intro/feedback/scm.issue/12`,
			reviewDetails: `${APP}/w/intro/admin/practices/reviews/runs?artifactKind=scm.issue&artifactId=12&detail=work%3Aissue%3A12`,
		});
	});
});
