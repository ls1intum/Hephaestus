import { Copy, KeyRound, Pencil, Trash2 } from "lucide-react";
import { toast } from "sonner";
import type { LoginProviderView } from "@/api/types.gen";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
	AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { Switch } from "@/components/ui/switch";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";

interface LoginProvidersTableProps {
	providers: LoginProviderView[];
	isLoading: boolean;
	isError: boolean;
	mutatingId: string | null;
	onEdit: (provider: LoginProviderView) => void;
	onToggleEnabled: (provider: LoginProviderView, enabled: boolean) => void;
	onDelete: (provider: LoginProviderView) => void;
}

export function LoginProvidersTable({
	providers,
	isLoading,
	isError,
	mutatingId,
	onEdit,
	onToggleEnabled,
	onDelete,
}: LoginProvidersTableProps) {
	if (isError) {
		return (
			<p className="py-8 text-center text-sm text-destructive">
				Could not load login providers. Please try again.
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
	if (providers.length === 0) {
		return (
			<div className="flex flex-col items-center gap-2 py-12 text-center text-muted-foreground">
				<KeyRound className="size-8" aria-hidden />
				<p className="text-sm">No login providers yet. Add one so users can sign in.</p>
			</div>
		);
	}

	const copyRedirect = async (uri: string) => {
		try {
			await navigator.clipboard.writeText(uri);
			toast.success("Redirect URI copied");
		} catch {
			toast.error("Could not copy to clipboard");
		}
	};

	return (
		<Table>
			<TableHeader>
				<TableRow>
					<TableHead>Provider</TableHead>
					<TableHead>Type</TableHead>
					<TableHead>Redirect URI</TableHead>
					<TableHead>Enabled</TableHead>
					<TableHead className="text-right">Actions</TableHead>
				</TableRow>
			</TableHeader>
			<TableBody>
				{providers.map((provider) => {
					const busy = mutatingId === provider.registrationId;
					return (
						<TableRow key={provider.registrationId}>
							<TableCell>
								<div className="font-medium">{provider.displayName}</div>
								<div className="text-xs text-muted-foreground">
									{provider.registrationId}
									{provider.seededFromEnv && (
										<Badge variant="outline" className="ml-2 align-middle">
											seeded
										</Badge>
									)}
								</div>
								<div className="text-xs text-muted-foreground">{provider.baseUrl}</div>
							</TableCell>
							<TableCell>
								<Badge variant="secondary">{provider.type}</Badge>
							</TableCell>
							<TableCell>
								<div className="flex items-center gap-2">
									<code className="max-w-[22rem] truncate rounded bg-muted px-1.5 py-0.5 text-xs">
										{provider.redirectUri}
									</code>
									<Button
										type="button"
										variant="ghost"
										size="icon"
										aria-label={`Copy redirect URI for ${provider.displayName}`}
										onClick={() => copyRedirect(provider.redirectUri)}
									>
										<Copy className="size-4" aria-hidden />
									</Button>
								</div>
							</TableCell>
							<TableCell>
								<Switch
									checked={provider.enabled}
									disabled={busy}
									aria-label={`${provider.enabled ? "Disable" : "Enable"} ${provider.displayName}`}
									onCheckedChange={(checked) => onToggleEnabled(provider, checked)}
								/>
							</TableCell>
							<TableCell className="text-right">
								<div className="flex justify-end gap-1">
									<Button
										type="button"
										variant="ghost"
										size="icon"
										aria-label={`Edit ${provider.displayName}`}
										disabled={busy}
										onClick={() => onEdit(provider)}
									>
										<Pencil className="size-4" aria-hidden />
									</Button>
									<AlertDialog>
										<AlertDialogTrigger
											render={
												<Button
													type="button"
													variant="ghost"
													size="icon"
													aria-label={`Delete ${provider.displayName}`}
													disabled={busy}
												>
													<Trash2 className="size-4 text-destructive" aria-hidden />
												</Button>
											}
										/>
										<AlertDialogContent>
											<AlertDialogHeader>
												<AlertDialogTitle>Delete “{provider.displayName}”?</AlertDialogTitle>
												<AlertDialogDescription>
													Users will no longer be able to sign in with this provider. Existing
													linked accounts are unaffected. This cannot be undone.
												</AlertDialogDescription>
											</AlertDialogHeader>
											<AlertDialogFooter>
												<AlertDialogCancel>Cancel</AlertDialogCancel>
												<AlertDialogAction onClick={() => onDelete(provider)}>
													Delete
												</AlertDialogAction>
											</AlertDialogFooter>
										</AlertDialogContent>
									</AlertDialog>
								</div>
							</TableCell>
						</TableRow>
					);
				})}
			</TableBody>
		</Table>
	);
}
