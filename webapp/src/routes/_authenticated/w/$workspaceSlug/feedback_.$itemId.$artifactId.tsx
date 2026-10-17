import { createFileRoute, notFound, redirect } from "@tanstack/react-router";

import { resolveLegacyFeedbackItem } from "@/api/sdk.gen";

const RELEASED_KINDS = new Set(["scm.pull_request", "scm.issue"]);
const SOURCE_ID = /^[1-9]\d{0,18}$/u;

// Already-posted provider comments carry the old source ID, not the workspace item ID.
export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/feedback_/$itemId/$artifactId",
)({
	loader: async ({ params: { workspaceSlug, itemId: artifactKind, artifactId } }) => {
		if (
			!RELEASED_KINDS.has(artifactKind) ||
			!SOURCE_ID.test(artifactId) ||
			BigInt(artifactId) > 9_223_372_036_854_775_807n
		) {
			throw notFound();
		}
		const resolution = await resolveLegacyFeedbackItem({
			path: { workspaceSlug, artifactKind, artifactId },
		});
		if (!resolution.data) {
			if (resolution.response?.status === 404) {
				throw notFound();
			}
			throw resolution.error;
		}
		throw redirect({
			to: "/w/$workspaceSlug/feedback/$itemId",
			params: { workspaceSlug, itemId: resolution.data.itemId },
			replace: true,
		});
	},
});
