import { Link } from "@tanstack/react-router";
import { ArrowRight, ScrollText } from "lucide-react";
import type { AuthEventView } from "@/api/types.gen";
import {
	accountLabel,
	eventLabel,
	eventSeverity,
	relativeTime,
	SEVERITY_DOT,
} from "@/components/admin/audit/auditFormat";
import { Button } from "@/components/ui/button";
import {
	Card,
	CardAction,
	CardContent,
	CardDescription,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";

interface RecentAuthActivityCardProps {
	events: AuthEventView[];
	isLoading?: boolean;
}

/**
 * The overview's "is anything unusual happening" panel: the latest auth/audit events, one line each,
 * drilling into the full audit log.
 */
export function RecentAuthActivityCard({ events, isLoading = false }: RecentAuthActivityCardProps) {
	return (
		<Card>
			<CardHeader>
				<CardTitle>Recent activity</CardTitle>
				<CardDescription>Latest authentication and admin events</CardDescription>
				<CardAction>
					<Button variant="ghost" size="sm" render={<Link to="/admin/audit" />}>
						View audit log
						<ArrowRight aria-hidden />
					</Button>
				</CardAction>
			</CardHeader>
			<CardContent>
				{isLoading ? (
					<div className="space-y-3">
						{["a", "b", "c", "d"].map((row) => (
							<Skeleton key={row} className="h-5 w-full" />
						))}
					</div>
				) : events.length === 0 ? (
					<Empty className="py-8">
						<EmptyHeader>
							<EmptyMedia variant="icon">
								<ScrollText aria-hidden />
							</EmptyMedia>
							<EmptyTitle>No activity yet</EmptyTitle>
							<EmptyDescription>
								Sign-ins, role changes, and impersonations will show up here as they happen.
							</EmptyDescription>
						</EmptyHeader>
					</Empty>
				) : (
					<ul className="space-y-2.5">
						{events.map((event) => {
							const severity = eventSeverity(event.eventType, event.result);
							const actor =
								accountLabel(event.actor, event.actingAccountId) ??
								accountLabel(event.account, event.accountId);
							return (
								<li key={event.id} className="flex items-center gap-2 text-sm">
									<span
										className={`size-1.5 shrink-0 rounded-full ${SEVERITY_DOT[severity]}`}
										aria-hidden
									/>
									<span className="truncate">{eventLabel(event.eventType)}</span>
									{actor ? <span className="truncate text-muted-foreground">{actor}</span> : null}
									<span className="ml-auto shrink-0 whitespace-nowrap text-xs text-muted-foreground">
										{relativeTime(event.occurredAt)}
									</span>
								</li>
							);
						})}
					</ul>
				)}
			</CardContent>
		</Card>
	);
}
