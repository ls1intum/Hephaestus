import type { PanelState } from "@/components/common/panel-state";
import { dedupeById } from "@/lib/dedupe-by-id";
import type { MorePages } from "@/runtime/tanstack-query/infinite-list";
import type { SpringPage } from "@/runtime/tanstack-query/spring-page";

/** An audit trail as a table shows it: its rows, the count the server gave and its paging. */
export type AuditListState<TRow> = PanelState<{ rows: TRow[]; total?: number } & MorePages>;

/**
 * Flat Spring pages as one list. Offset pages over an append-only log can repeat a row across two
 * reads, and only the first page's count describes the whole filtered trail.
 */
export function auditRows<TRow extends { id?: number }>(pages: readonly SpringPage<TRow>[]) {
	return {
		rows: dedupeById(pages.flatMap((page) => page.content ?? [])),
		total: pages[0]?.totalElements,
	};
}
