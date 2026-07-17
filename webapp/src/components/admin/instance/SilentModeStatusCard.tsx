import { Link } from "@tanstack/react-router";
import { ArrowRight, Volume2, VolumeX } from "lucide-react";
import type { InstanceSettings } from "@/api/types.gen";
import { relativeTime } from "@/components/admin/audit/auditFormat";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Card,
	CardAction,
	CardContent,
	CardDescription,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

interface SilentModeStatusCardProps {
	settings?: InstanceSettings;
	isLoading?: boolean;
}

/**
 * The overview's delivery-state tile: is the instance-wide silent mode engaged? Read-only — the
 * control itself (with its asymmetric friction) lives on the instance settings page.
 */
export function SilentModeStatusCard({ settings, isLoading = false }: SilentModeStatusCardProps) {
	const engaged = settings?.silentModeEngaged === true;
	return (
		<Card>
			<CardHeader>
				<CardTitle className="flex items-center gap-2">
					{engaged ? (
						<VolumeX className="size-4 text-destructive" aria-hidden />
					) : (
						<Volume2 className="size-4 text-muted-foreground" aria-hidden />
					)}
					Delivery
				</CardTitle>
				<CardDescription>Outbound feedback and messages, instance-wide</CardDescription>
				<CardAction>
					<Button variant="ghost" size="sm" render={<Link to="/admin/settings" />}>
						Manage
						<ArrowRight aria-hidden />
					</Button>
				</CardAction>
			</CardHeader>
			<CardContent>
				{isLoading ? (
					<Skeleton className="h-6 w-40" />
				) : engaged ? (
					<div className="space-y-1">
						<Badge variant="destructive">Silent mode engaged</Badge>
						<p className="text-sm text-muted-foreground">
							Nothing is being delivered
							{settings?.silentModeChangedBy ? ` — engaged by ${settings.silentModeChangedBy}` : ""}
							{settings?.silentModeChangedAt
								? ` ${relativeTime(settings.silentModeChangedAt)}`
								: ""}
							.
						</p>
					</div>
				) : (
					<div className="space-y-1">
						<Badge variant="success">Delivering</Badge>
						<p className="text-sm text-muted-foreground">
							Practice feedback and Slack messages go out normally.
						</p>
					</div>
				)}
			</CardContent>
		</Card>
	);
}
