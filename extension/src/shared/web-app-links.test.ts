import { describe, expect, it } from "vitest";

import { workLinks } from "~/shared/web-app-links";

const APP = "https://heph.example.test";

describe("web app links", () => {
	it("leads every reader to their feedback on this item in the workspace that answered", () => {
		expect(workLinks(APP, "intro course", { id: "4009" }, false)).toStrictEqual({
			trace: `${APP}/w/intro%20course/feedback/4009`,
		});
	});

	it("adds this item's own reviews for a workspace admin, with its detail open", () => {
		expect(workLinks(APP, "intro", { id: "12" }, true)).toStrictEqual({
			trace: `${APP}/w/intro/feedback/12`,
			reviewDetails: `${APP}/w/intro/admin/practices/reviews/reviews?itemId=12&detail=item%3A12`,
		});
	});
});
