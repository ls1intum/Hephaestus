import type { Wire } from "@/lib/dates";

/** JSON serialization converts fixture Dates to the strings an HTTP response carries. */
export function toWire<T>(value: T): Wire<T>;
export function toWire(value: unknown): unknown {
	const encoded = JSON.stringify(value);
	const serialized: unknown = JSON.parse(encoded);
	return serialized;
}
