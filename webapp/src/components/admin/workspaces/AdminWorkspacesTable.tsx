import { Building2 } from "lucide-react";
import type { AdminWorkspaceView } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { Spinner } from "@/components/ui/spinner";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";

export interface AdminWorkspacesTableProps {
	workspaces: AdminWorkspaceView[];
	isLoading: boolean;
	isError: boolean;
	hasSearch: boolean;
}

function statusVariant(status: string): "secondary" | "destructive" | "outline" {
	if (status === "ACTIVE") return "secondary";
	if (status === "SUSPENDED" || status === "PURGED") return "destructive";
	return "outline";
}

/**
 * Read-only, metadata-only table of every workspace (instance-admin overview). Pure/presentational.
 * No tenant content — reaching a workspace's content is done via audited impersonation of a member.
 */
export function AdminWorkspacesTable({
	workspaces,
	isLoading,
	isError,
	hasSearch,
}: AdminWorkspacesTableProps) {
	if (isError) {
		return (
			<p className="py-8 text-center text-sm text-destructive">
				Failed to load workspaces. Please try again.
			</p>
		);
	}
	if (isLoading) {
		return (
			<div className="flex items-center justify-center py-12">
				<Spinner />
			</div>
		);
	}
	if (workspaces.length === 0) {
		return (
			<div className="flex flex-col items-center gap-2 py-12 text-center text-muted-foreground">
				<Building2 className="size-8" aria-hidden />
				<p className="text-sm">{hasSearch ? "No matching workspaces." : "No workspaces yet."}</p>
			</div>
		);
	}

	return (
		<div className="rounded-md border">
			<Table>
				<TableHeader>
					<TableRow>
						<TableHead scope="col">Name</TableHead>
						<TableHead scope="col">Slug</TableHead>
						<TableHead scope="col">Status</TableHead>
						<TableHead scope="col">Provider</TableHead>
						<TableHead scope="col">Owner</TableHead>
						<TableHead scope="col" className="text-right">
							Members
						</TableHead>
						<TableHead scope="col">Created</TableHead>
					</TableRow>
				</TableHeader>
				<TableBody>
					{workspaces.map((ws) => (
						<TableRow key={ws.id}>
							<TableCell className="font-medium">{ws.displayName}</TableCell>
							<TableCell className="font-mono text-xs text-muted-foreground">
								{ws.workspaceSlug}
							</TableCell>
							<TableCell>
								<Badge variant={statusVariant(ws.status)}>{ws.status}</Badge>
							</TableCell>
							<TableCell>
								{ws.providerType ? (
									<Badge variant="outline" className="text-xs">
										{ws.providerType}
									</Badge>
								) : (
									<span className="text-muted-foreground">—</span>
								)}
							</TableCell>
							<TableCell className="text-muted-foreground">{ws.ownerLogin ?? "—"}</TableCell>
							<TableCell className="text-right tabular-nums">{ws.memberCount}</TableCell>
							<TableCell className="whitespace-nowrap text-sm text-muted-foreground">
								{ws.createdAt.toLocaleDateString()}
							</TableCell>
						</TableRow>
					))}
				</TableBody>
			</Table>
		</div>
	);
}
