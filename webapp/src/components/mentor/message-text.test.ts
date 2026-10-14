import { describe, expect, it } from "vitest";

import type { ChatMessage } from "@/lib/types";

import { visibleTexts } from "./message-text";

describe("visibleTexts", () => {
	it("keeps the reply's text and the feedback it links, in order, and nothing else", () => {
		const message: ChatMessage = {
			id: "reply",
			role: "assistant",
			parts: [
				{ type: "reasoning", text: "The reader asked about the description.", state: "done" },
				{ type: "text", text: "Let me look at your pull request." },
				{
					type: "data-result",
					id: "link-1",
					data: {
						resultId: "3f0c2b4e-8a1d-4c6e-9b7f-2d5e8a1c4b6f",
						text: "Your description names the decision but not why it beat the alternative.",
					},
				},
				{
					type: "data-problem",
					id: "link-3",
					data: {
						resultId: "3f0c2b4e-8a1d-4c6e-9b7f-2d5e8a1c4b6f",
						problemId: "8d3e6f1a-2b4c-4d5e-9f6a-7b8c9d0e1f2a",
						text: "The commit `Fix stuff` does not say what it changes.",
					},
				},
				// Stored before links carried their feedback: it shows nothing.
				{
					type: "data-result",
					id: "link-2",
					data: { resultId: "c9bf9e57-1685-4c89-bafb-ff5af830be8a", text: "" },
				},
				{ type: "text", text: "  " },
				{ type: "text", text: "What made you pick it?" },
			],
		};

		expect(visibleTexts(message)).toStrictEqual([
			"Let me look at your pull request.",
			"Your description names the decision but not why it beat the alternative.",
			"The commit `Fix stuff` does not say what it changes.",
			"What made you pick it?",
		]);
	});
});
