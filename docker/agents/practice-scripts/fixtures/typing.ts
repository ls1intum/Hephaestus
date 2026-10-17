// Compiled, never run: the slot types follow `meta.models`.
import type { Experimental_DecisionModel, LanguageModel } from "ai";

import { scm } from "../kits/scm/index.ts";
import { definePracticeScript } from "../lib/core/practice-script.ts";

export default definePracticeScript({
	meta: { models: { decision: "required", chat: "optional" } },
	async run(ctx) {
		// @ts-expect-error The neutral item has no provider fields until a kit validates it.
		void ctx.item.pr_number;
		// @ts-expect-error File reads belong to the SCM kit.
		void ctx.files;
		const domain = scm(ctx);
		if (domain.kind === "scm.pull_request") {
			void domain.item.pr_number;
		}
		if (domain.kind === "scm.file") {
			void domain.item.blobId;
		}
		if (domain.kind === "scm.repository") {
			void domain.item.commitId;
		}
		const decision: Experimental_DecisionModel = ctx.models.decision;
		// @ts-expect-error an optional slot may be undefined
		const chat: LanguageModel = ctx.models.chat;
		// @ts-expect-error an undeclared slot does not exist
		void ctx.models.embedding;
		void decision;
		void chat;
		return ctx.review();
	},
});
