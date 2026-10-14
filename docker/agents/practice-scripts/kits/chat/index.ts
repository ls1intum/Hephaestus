import { z } from "zod";
import { decimalIdSchema } from "../../lib/core/contract.ts";
import type { PracticeScriptContext, ModelNeeds } from "../../lib/core/practice-script.ts";

export const threadRecordSchema = z.object({
	channel: z.string(),
	threadTs: z.string(),
	messages: z.array(
		z.object({
			ts: z.string(),
			author: z.string().nullable(),
			accountId: decimalIdSchema.nullable(),
			authorLogin: z.string().optional(),
			authorName: z.string().optional(),
			text: z.string().nullable(),
			edited: z.literal(true).optional(),
		}),
	),
	messageCount: z.int(),
	truncated: z.boolean(),
});

export function chat<M extends ModelNeeds>(ctx: PracticeScriptContext<M>) {
	if (ctx.kind !== "chat.thread") {
		throw new Error("the script uses the wrong domain kit");
	}
	return { ...ctx, kind: "chat.thread" as const, item: threadRecordSchema.parse(ctx.item) };
}
