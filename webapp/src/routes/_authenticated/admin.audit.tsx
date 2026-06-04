import { useInfiniteQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { ScrollText } from "lucide-react";
import { useState } from "react";
import { adminListAuthEventsInfiniteOptions } from "@/api/@tanstack/react-query.gen";
import type { AuthEventView, PageAuthEventView } from "@/api/types.gen";
import { AdminAuditTable } from "@/components/admin/audit/AdminAuditTable";
import { Label } from "@/components/ui/label";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";

const PAGE_SIZE = 50;
const ALL = "ALL";

// Mirrors AuthEvent.EventType server-side; kept explicit so the filter stays a typed union.
const EVENT_TYPES = [
	"LOGIN",
	"LOGIN_FAILED",
	"LOGOUT",
	"TOKEN_REFRESH",
	"JWT_REVOKED",
	"IDENTITY_LINKED",
	"IDENTITY_UNLINKED",
	"IMPERSONATION_BEGIN",
	"IMPERSONATION_END",
	"ACCOUNT_DELETED",
	"EXPORT_REQUESTED",
	"FEATURE_FLAG_CHANGED",
	"APP_ROLE_CHANGED",
] as const;

type EventTypeFilter = (typeof EVENT_TYPES)[number];

export const Route = createFileRoute("/_authenticated/admin/audit")({
	component: AdminAuditPage,
});

function AdminAuditPage() {
	const [eventType, setEventType] = useState<EventTypeFilter | undefined>(undefined);

	const listQuery = useInfiniteQuery({
		...adminListAuthEventsInfiniteOptions({ query: { size: PAGE_SIZE, eventType } }),
		initialPageParam: 0,
		// The endpoint returns a Spring Page; advance by page number until the last page.
		getNextPageParam: (lastPage: PageAuthEventView) =>
			lastPage.last ? undefined : (lastPage.number ?? 0) + 1,
	});

	const events: AuthEventView[] = listQuery.data?.pages.flatMap((p) => p.content ?? []) ?? [];

	return (
		<div className="mx-auto w-full max-w-6xl space-y-6 py-6">
			<header className="space-y-1">
				<div className="flex items-center gap-2">
					<ScrollText className="size-6 text-muted-foreground" aria-hidden />
					<h1 className="text-2xl font-semibold">Audit log</h1>
				</div>
				<p className="text-sm text-muted-foreground">
					Read-only record of authentication and admin events (logins, impersonation, role changes,
					deletions). Append-only — entries can't be edited or removed.
				</p>
			</header>

			<div className="flex w-full flex-col gap-2 sm:max-w-xs">
				<Label htmlFor="audit-event-type" className="text-sm">
					Event type
				</Label>
				<Select
					value={eventType ?? ALL}
					onValueChange={(value) =>
						setEventType(value === ALL ? undefined : (value as EventTypeFilter))
					}
				>
					<SelectTrigger id="audit-event-type">
						<SelectValue placeholder="All events" />
					</SelectTrigger>
					<SelectContent>
						<SelectItem value={ALL}>All events</SelectItem>
						{EVENT_TYPES.map((type) => (
							<SelectItem key={type} value={type}>
								{type}
							</SelectItem>
						))}
					</SelectContent>
				</Select>
			</div>

			<AdminAuditTable
				events={events}
				isLoading={listQuery.isLoading}
				isError={listQuery.isError}
				hasFilter={eventType !== undefined}
				hasNextPage={Boolean(listQuery.hasNextPage)}
				isFetchingNextPage={listQuery.isFetchingNextPage}
				onLoadMore={() => listQuery.fetchNextPage()}
			/>
		</div>
	);
}
