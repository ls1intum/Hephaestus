import { describe, expect, it } from "vitest";
import {
	inferTitleFromMessage,
	partsToPersist,
	uiMessageFromPersisted,
	uiPartsFromPersisted,
} from "@/mentor/chat/chat.transformer";
import type { PersistedMessage, PersistedPart } from "@/mentor/chat/data";

describe("uiPartsFromPersisted", () => {
	it("should extract text parts correctly", () => {
		const parts: PersistedPart[] = [{ type: "text", content: { text: "Hello world" } }];

		const result = uiPartsFromPersisted(parts);

		expect(result).toMatchInlineSnapshot(`
			[
			  {
			    "text": "Hello world",
			    "type": "text",
			  },
			]
		`);
	});

	it("should extract file parts with valid media types", () => {
		const parts: PersistedPart[] = [
			{
				type: "file",
				content: {
					url: "https://example.com/image.jpg",
					mediaType: "image/jpeg",
					name: "photo.jpg",
				},
			},
		];

		const result = uiPartsFromPersisted(parts);

		expect(result).toMatchInlineSnapshot(`
			[
			  {
			    "mediaType": "image/jpeg",
			    "name": "photo.jpg",
			    "type": "file",
			    "url": "https://example.com/image.jpg",
			  },
			]
		`);
	});

	it("should skip file parts with invalid media types", () => {
		const parts: PersistedPart[] = [
			{
				type: "file",
				content: {
					url: "https://example.com/doc.pdf",
					mediaType: "application/pdf",
				},
			},
		];

		const result = uiPartsFromPersisted(parts);

		expect(result).toMatchInlineSnapshot("[]");
	});

	it("should handle mixed parts", () => {
		const parts: PersistedPart[] = [
			{ type: "text", content: { text: "Check out this image:" } },
			{
				type: "file",
				content: { url: "https://example.com/img.png", mediaType: "image/png" },
			},
			{ type: "reasoning", content: { text: "This is reasoning" } },
		];

		const result = uiPartsFromPersisted(parts);

		expect(result).toMatchInlineSnapshot(`
			[
			  {
			    "text": "Check out this image:",
			    "type": "text",
			  },
			  {
			    "mediaType": "image/png",
			    "name": undefined,
			    "type": "file",
			    "url": "https://example.com/img.png",
			  },
			]
		`);
	});
});

describe("uiMessageFromPersisted", () => {
	it("should convert a persisted message to UI format", () => {
		const message: PersistedMessage = {
			id: "msg-123",
			role: "user",
			createdAt: new Date("2025-01-01T00:00:00.000Z"),
			parts: [{ type: "text", content: { text: "Hello" } }],
			threadId: "thread-456",
		};

		const result = uiMessageFromPersisted(message);

		expect(result).toMatchInlineSnapshot(`
			{
			  "id": "msg-123",
			  "parts": [
			    {
			      "text": "Hello",
			      "type": "text",
			    },
			  ],
			  "role": "user",
			}
		`);
	});

	it("should convert assistant message with multiple parts", () => {
		const message: PersistedMessage = {
			id: "msg-456",
			role: "assistant",
			createdAt: new Date("2025-01-01T00:00:00.000Z"),
			parts: [
				{ type: "text", content: { text: "Here's an image:" } },
				{ type: "file", content: { url: "https://example.com/img.png", mediaType: "image/png" } },
			],
			threadId: "thread-789",
		};

		const result = uiMessageFromPersisted(message);

		expect(result).toMatchInlineSnapshot(`
			{
			  "id": "msg-456",
			  "parts": [
			    {
			      "text": "Here's an image:",
			      "type": "text",
			    },
			    {
			      "mediaType": "image/png",
			      "name": undefined,
			      "type": "file",
			      "url": "https://example.com/img.png",
			    },
			  ],
			  "role": "assistant",
			}
		`);
	});
});

describe("partsToPersist", () => {
	it("should persist text parts correctly", () => {
		const parts = [{ type: "text", text: "Hello world" }];

		const result = partsToPersist(parts);

		expect(result).toMatchInlineSnapshot(`
			[
			  {
			    "content": {
			      "text": "Hello world",
			      "type": "text",
			    },
			    "originalType": "text",
			    "type": "text",
			  },
			]
		`);
	});

	it("should persist file parts correctly", () => {
		const parts = [
			{
				type: "file",
				url: "https://example.com/img.jpg",
				mediaType: "image/jpeg",
				name: "photo.jpg",
			},
		];

		const result = partsToPersist(parts);

		expect(result).toMatchInlineSnapshot(`
			[
			  {
			    "content": {
			      "mediaType": "image/jpeg",
			      "name": "photo.jpg",
			      "providerMetadata": undefined,
			      "type": "file",
			      "url": "https://example.com/img.jpg",
			    },
			    "originalType": "file",
			    "type": "file",
			  },
			]
		`);
	});

	it("should filter out data-* ephemeral parts", () => {
		const parts = [
			{ type: "text", text: "Hello" },
			{ type: "data-document-create", id: "doc-1" },
			{ type: "data-delta", delta: "..." },
		];

		const result = partsToPersist(parts);

		expect(result).toMatchInlineSnapshot(`
			[
			  {
			    "content": {
			      "text": "Hello",
			      "type": "text",
			    },
			    "originalType": "text",
			    "type": "text",
			  },
			]
		`);
	});
});

describe("inferTitleFromMessage", () => {
	it("should extract title from first text part", () => {
		const message = {
			id: "msg-1",
			role: "user" as const,
			parts: [{ type: "text" as const, text: "Help me with code review" }],
		};

		expect(inferTitleFromMessage(message)).toBe("Help me with code review");
	});

	it("should truncate long titles to 60 characters", () => {
		const longText =
			"This is a very long message that exceeds the maximum title length and should be truncated with ellipsis";
		const message = {
			id: "msg-1",
			role: "user" as const,
			parts: [{ type: "text" as const, text: longText }],
		};

		const result = inferTitleFromMessage(message);

		expect(result.length).toBe(60);
		expect(result.endsWith("...")).toBe(true);
	});

	it("should return 'New chat' for empty messages", () => {
		const message = {
			id: "msg-1",
			role: "user" as const,
			parts: [],
		};

		expect(inferTitleFromMessage(message)).toBe("New chat");
	});

	it("should return 'New chat' for whitespace-only messages", () => {
		const message = {
			id: "msg-1",
			role: "user" as const,
			parts: [{ type: "text" as const, text: "   \n\t   " }],
		};

		expect(inferTitleFromMessage(message)).toBe("New chat");
	});
});
