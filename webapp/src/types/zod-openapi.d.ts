import type { ZodEnum, ZodObject, ZodTypeAny } from "zod";

declare module "zod" {
	interface ZodTypeAny {
		openapi(...args: unknown[]): this;
	}
	interface ZodEnum<T> {
		openapi(...args: unknown[]): this;
	}
	interface ZodObject<
		T extends Record<string, ZodTypeAny>,
		UnknownKeys extends import("zod").UnknownKeysParam = "strip",
		Catchall extends ZodTypeAny = ZodTypeAny,
	> {
		openapi(...args: unknown[]): this;
	}
}
