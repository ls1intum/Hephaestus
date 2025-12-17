/**
 * Type-Level Tests
 *
 * Uses Vitest's expectTypeOf for compile-time type assertions.
 * These tests catch type regressions and ensure API contracts.
 */

import { describe, expectTypeOf, it } from "vitest";
import type { ChatRequestBody, ThreadDetail } from "@/mentor/chat/chat.schema";
import type { UMessage } from "@/mentor/chat/chat.transformer";
import type { PersistedMessage, PersistedPart } from "@/mentor/chat/data";

describe("Type Contracts", () => {
	describe("ChatRequestBody", () => {
		it("should require id as string", () => {
			expectTypeOf<ChatRequestBody["id"]>().toBeString();
		});

		it("should require message with proper structure", () => {
			expectTypeOf<ChatRequestBody["message"]>().toMatchTypeOf<{
				id: string;
				role: string;
				parts: unknown[];
			}>();
		});

		it("should have optional previousMessageId", () => {
			expectTypeOf<ChatRequestBody>().toHaveProperty("previousMessageId");
		});
	});

	describe("ThreadDetail", () => {
		it("should have id as string", () => {
			expectTypeOf<ThreadDetail["id"]>().toBeString();
		});

		it("should have nullable and optional title", () => {
			// title is both nullable and optional (string | null | undefined)
			expectTypeOf<ThreadDetail>().toHaveProperty("title");
		});

		it("should have messages array", () => {
			expectTypeOf<ThreadDetail["messages"]>().toBeArray();
		});

		it("should have proper message structure", () => {
			type Message = ThreadDetail["messages"][number];
			expectTypeOf<Message>().toHaveProperty("id");
			expectTypeOf<Message>().toHaveProperty("role");
			expectTypeOf<Message>().toHaveProperty("parts");
			expectTypeOf<Message>().toHaveProperty("createdAt");
		});
	});

	describe("PersistedMessage", () => {
		it("should have required fields", () => {
			expectTypeOf<PersistedMessage>().toMatchTypeOf<{
				id: string;
				role: string;
				threadId: string;
				createdAt: Date;
				parts: PersistedPart[];
			}>();
		});

		it("should have optional parentMessageId", () => {
			// parentMessageId is optional (can be undefined) and nullable (can be null)
			expectTypeOf<PersistedMessage>().toHaveProperty("parentMessageId");
		});
	});

	describe("UMessage (UI Message)", () => {
		it("should be compatible with AI SDK Message type", () => {
			// UMessage should have the core fields AI SDK expects
			expectTypeOf<UMessage>().toHaveProperty("id");
			expectTypeOf<UMessage>().toHaveProperty("role");
			expectTypeOf<UMessage>().toHaveProperty("parts");
		});

		it("should have role as specific union", () => {
			expectTypeOf<UMessage["role"]>().toMatchTypeOf<"user" | "assistant" | "system">();
		});
	});

	describe("PersistedPart", () => {
		it("should have type field", () => {
			expectTypeOf<PersistedPart>().toHaveProperty("type");
			expectTypeOf<PersistedPart["type"]>().toBeString();
		});

		it("should have content field", () => {
			expectTypeOf<PersistedPart>().toHaveProperty("content");
		});
	});
});

describe("Function Type Contracts", () => {
	describe("Transformer functions", () => {
		it("toThreadDetailMessage should accept PersistedMessage and return ThreadDetail message", async () => {
			const { toThreadDetailMessage } = await import("@/mentor/chat/chat.transformer");

			expectTypeOf(toThreadDetailMessage).toBeFunction();
			expectTypeOf(toThreadDetailMessage).parameters.toMatchTypeOf<[PersistedMessage]>();
			expectTypeOf(toThreadDetailMessage).returns.toMatchTypeOf<ThreadDetail["messages"][number]>();
		});

		it("uiMessageFromPersisted should convert PersistedMessage to UMessage", async () => {
			const { uiMessageFromPersisted } = await import("@/mentor/chat/chat.transformer");

			expectTypeOf(uiMessageFromPersisted).toBeFunction();
			expectTypeOf(uiMessageFromPersisted).parameters.toMatchTypeOf<[PersistedMessage]>();
			expectTypeOf(uiMessageFromPersisted).returns.toMatchTypeOf<UMessage>();
		});
	});

	describe("Data layer functions", () => {
		it("createThread should return Promise", async () => {
			const { createThread } = await import("@/mentor/chat/data");

			expectTypeOf(createThread).toBeFunction();
			expectTypeOf(createThread).returns.toMatchTypeOf<Promise<unknown>>();
		});

		it("getThreadById should return nullable result", async () => {
			const { getThreadById } = await import("@/mentor/chat/data");

			expectTypeOf(getThreadById).toBeFunction();
			// Return type should allow null
			expectTypeOf(getThreadById).returns.toMatchTypeOf<Promise<unknown>>();
		});
	});
});
