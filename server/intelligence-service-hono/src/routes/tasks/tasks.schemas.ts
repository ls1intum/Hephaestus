import { z } from "@hono/zod-openapi";

export const selectTasksSchema = z
	.object({
		id: z.number().openapi({ example: 1 }),
		name: z.string().min(1).openapi({ example: "Learn hono" }),
		done: z.boolean().default(false).openapi({ example: false }),
	})
	.openapi("Task");

export const insertTasksSchema = selectTasksSchema
	.omit({ id: true })
	.openapi("InsertTask");

export const patchTasksSchema = insertTasksSchema
	.partial()
	.openapi("PatchTask");

export type Task = z.infer<typeof selectTasksSchema>;
