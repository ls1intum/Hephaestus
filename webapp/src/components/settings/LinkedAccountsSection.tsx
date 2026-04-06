import { GithubIcon, GitlabIcon, LinkIcon, type LucideIcon, UnlinkIcon } from "lucide-react";
import type { LinkedAccount } from "@/api/types.gen";
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

const PROVIDER_META: Record<string, { icon: LucideIcon; label: string }> = {
	github: { icon: GithubIcon, label: "GitHub" },
	"gitlab-lrz": { icon: GitlabIcon, label: "GitLab LRZ" },
};

function getProviderMeta(alias: string, displayName: string) {
	return PROVIDER_META[alias] ?? { icon: LinkIcon, label: displayName };
}

export interface LinkedAccountsSectionProps {
	accounts: LinkedAccount[];
	onLink: (providerAlias: string) => void;
	onUnlink: (providerAlias: string) => void;
	isUnlinking?: boolean;
	isLoading?: boolean;
	isError?: boolean;
}

/**
 * Settings section for managing linked identity provider accounts.
 * Parent component controls visibility — this always renders when mounted.
 */
export function LinkedAccountsSection({
	accounts,
	onLink,
	onUnlink,
	isUnlinking = false,
	isLoading = false,
	isError = false,
}: LinkedAccountsSectionProps) {
	const connectedCount = accounts.filter((a) => a.connected).length;

	return (
		<section className="space-y-4" aria-labelledby="linked-accounts-heading">
			<div className="space-y-1">
				<h2 id="linked-accounts-heading" className="text-xl font-semibold">
					Connected Accounts
				</h2>
				<p className="text-sm text-muted-foreground">
					Link your identity providers to sign in with either
				</p>
			</div>

			{isLoading ? (
				<div className="flex justify-center py-6">
					<Spinner />
				</div>
			) : (
				<>
					{isError && (
						<p className="text-sm text-destructive">
							Failed to load connected accounts. Please try refreshing the page.
						</p>
					)}

					<div className="space-y-3">
						{accounts.map((account) => {
							const meta = getProviderMeta(account.providerAlias, account.providerName);
							const Icon = meta.icon;
							const canUnlink = account.connected && connectedCount > 1;

							return (
								<div
									key={account.providerAlias}
									className="flex items-center justify-between gap-4 rounded-lg border p-4"
								>
									<div className="flex items-center gap-3 min-w-0">
										<Icon className="size-5 shrink-0" aria-hidden="true" />
										<div className="min-w-0">
											<div className="flex items-center gap-2">
												<span className="text-sm font-medium">{meta.label}</span>
												{account.connected && (
													<Badge variant="secondary" className="text-xs">
														Connected
													</Badge>
												)}
											</div>
											{account.connected && account.linkedUsername && (
												<p className="text-xs text-muted-foreground truncate">
													{account.linkedUsername}
												</p>
											)}
										</div>
									</div>

									{account.connected ? (
										<AlertDialog>
											<AlertDialogTrigger
												render={
													<Button
														variant="outline"
														size="sm"
														disabled={!canUnlink || isUnlinking}
														aria-label={
															canUnlink
																? `Disconnect ${meta.label}`
																: "Cannot disconnect last provider"
														}
													>
														{isUnlinking ? (
															<Spinner className="mr-1.5" />
														) : (
															<UnlinkIcon className="size-3.5 mr-1.5" />
														)}
														Disconnect
													</Button>
												}
											/>
											<AlertDialogContent>
												<AlertDialogHeader>
													<AlertDialogTitle>Disconnect {meta.label}?</AlertDialogTitle>
													<AlertDialogDescription>
														You will no longer be able to sign in with {meta.label}. You can
														reconnect it later.
													</AlertDialogDescription>
												</AlertDialogHeader>
												<AlertDialogFooter>
													<AlertDialogCancel>Cancel</AlertDialogCancel>
													<AlertDialogAction
														onClick={() => onUnlink(account.providerAlias)}
														disabled={isUnlinking}
													>
														Disconnect
													</AlertDialogAction>
												</AlertDialogFooter>
											</AlertDialogContent>
										</AlertDialog>
									) : (
										<Button
											variant="outline"
											size="sm"
											onClick={() => onLink(account.providerAlias)}
											aria-label={`Connect ${meta.label}`}
										>
											<LinkIcon className="size-3.5 mr-1.5" />
											Connect
										</Button>
									)}
								</div>
							);
						})}
					</div>
				</>
			)}
		</section>
	);
}
