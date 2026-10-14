import type { Wire } from "@/lib/dates";

/** Serialize a typed fixture through the same JSON boundary as an HTTP response. */
export function toWire<T>(value: T): Wire<T> {
	const serialized: unknown = JSON.parse(JSON.stringify(value));
	return serialized as Wire<T>;
}
