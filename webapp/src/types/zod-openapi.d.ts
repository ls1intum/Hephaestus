import "zod";

declare module "zod" {
	interface ZodTypeAny {
		openapi(...args: unknown[]): this;
	}
	interface ZodEnum<_T> {
		openapi(...args: unknown[]): this;
	}
	interface ZodObject<
		_T extends Record<string, import("zod").ZodTypeAny>,
		_UnknownKeys extends import("zod").UnknownKeysParam = "strip",
		_Catchall extends import("zod").ZodTypeAny = import("zod").ZodTypeAny,
	> {
		openapi(...args: unknown[]): this;
	}
}
