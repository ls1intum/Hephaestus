import { LinkIcon, type LucideIcon, Unlink } from "lucide-react";
import type { IdentityProviderView, IdentityView } from "@/api/types.gen";
import { GithubIcon, GitlabIcon } from "@/components/icons/brand";
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

const PROVIDER_ICONS: Record<string, LucideIcon> = {
	GITHUB: GithubIcon,
	GITLAB: GitlabIcon,
};

const PROVIDER_LABELS: Record<string, string> = {
	GITHUB: "GitHub",
	GITLAB: "GitLab",
};

/**
 * Resolve a brand icon from a provider type (e.g. "GITHUB", "GITLAB"). Falls back
 * to a generic link icon for unknown providers so new IdPs render gracefully.
 */
function getProviderIcon(providerType?: string): LucideIcon {
	if (!providerType) return LinkIcon;
	return PROVIDER_ICONS[providerType.toUpperCase()] ?? LinkIcon;
}

/** Human-friendly provider name for prose ("GITHUB" → "GitHub"). */
function friendlyProvider(providerType?: string): string {
	if (!providerType) return "that provider";
	return PROVIDER_LABELS[providerType.toUpperCase()] ?? providerType;
}

function formatLastLogin(lastLoginAt?: Date): string | undefined {
	if (!lastLoginAt) return undefined;
	const date = lastLoginAt instanceof Date ? lastLoginAt : new Date(lastLoginAt);
	if (Number.isNaN(date.getTime())) return undefined;
	return date.toLocaleDateString(undefined, {
		year: "numeric",
		month: "short",
		day: "numeric",
	});
}

export interface LinkedAccountsSectionProps {
	/** Identities already federated to this account. */
	identities: IdentityView[];
	/** Sign-in providers available to link (offered when not already linked). */
	providers: IdentityProviderView[];
	/**
	 * Start the link flow for a provider. Re-runs sign-in with that provider and
	 * attaches the resulting identity to the current account (top-level redirect).
	 */
	onLink: (registrationId: string) => void;
	/**
	 * Disconnect a linked identity. Disabled for the account's only remaining sign-in
	 * method — removing the last identity would lock the user out, so they delete the
	 * account instead.
	 */
	onUnlink: (identityId: number) => void;
	/** Id of the identity currently being disconnected — shows a spinner and blocks repeat clicks. */
	unlinkingId?: number | null;
	isLoading?: boolean;
	isError?: boolean;
}

/**
 * Settings section for federated identities (ADR 0017 native auth).
 *
 * Users can connect additional providers (re-running sign-in, which links the resulting
 * identity to this account) and disconnect ones they no longer want — except the last
 * remaining identity, which is kept so the account can never be locked out of sign-in.
 */
export function LinkedAccountsSection({
	identities,
	providers,
	onLink,
	onUnlink,
	unlinkingId = null,
	isLoading = false,
	isError = false,
}: LinkedAccountsSectionProps) {
	const linkedProviderTypes = new Set(
		identities
			.map((identity) => identity.providerType?.toUpperCase())
			.filter((type): type is string => Boolean(type)),
	);

	// Providers the account can still link: not already represented among the
	// linked identities (compared by provider type).
	const linkableProviders = providers.filter((provider) => {
		const type = provider.providerType?.toUpperCase();
		return !type || !linkedProviderTypes.has(type);
	});

	// Lockout guard: the account's only remaining sign-in method cannot be removed.
	const isOnlyIdentity = identities.length <= 1;

	return (
		<section className="space-y-4" aria-labelledby="linked-accounts-heading">
			<div className="space-y-1">
				<h2 id="linked-accounts-heading" className="text-xl font-semibold">
					Connected Accounts
				</h2>
				<p className="text-sm text-muted-foreground">
					The identity providers you can sign in to Hephaestus with. Connect another provider, or
					disconnect ones you no longer use.
				</p>
			</div>

			{isLoading ? (
				<div className="flex justify-center py-6">
					<Spinner aria-label="Loading connected accounts" />
				</div>
			) : (
				<>
					{isError && (
						<p className="text-sm text-destructive" role="alert">
							Failed to load connected accounts. Please try refreshing the page.
						</p>
					)}

					{!isError && identities.length === 0 && (
						<p className="text-sm text-muted-foreground">No connected accounts yet.</p>
					)}

					<div className="space-y-3">
						{identities.map((identity) => {
							const Icon = getProviderIcon(identity.providerType);
							const name =
								identity.displayName || identity.username || identity.subject || "Account";
							const lastLogin = formatLastLogin(identity.lastLoginAt);

							return (
								<div
									key={identity.id ?? `${identity.providerType}:${identity.subject}`}
									className="flex items-center justify-between gap-4 rounded-lg border p-4"
								>
									<div className="flex items-center gap-3 min-w-0">
										<Icon className="size-5 shrink-0" aria-hidden="true" />
										<div className="min-w-0">
											<div className="flex items-center gap-2">
												<span className="text-sm font-medium truncate">{name}</span>
												{identity.providerType && (
													<Badge variant="secondary" className="text-xs">
														{identity.providerType}
													</Badge>
												)}
											</div>
											{lastLogin && (
												<p className="text-xs text-muted-foreground truncate">
													Last sign-in {lastLogin}
												</p>
											)}
										</div>
									</div>

									{identity.id != null && (
										<UnlinkControl
											name={name}
											providerType={identity.providerType}
											isOnlyIdentity={isOnlyIdentity}
											isUnlinking={unlinkingId === identity.id}
											onConfirm={() => onUnlink(identity.id as number)}
										/>
									)}
								</div>
							);
						})}
					</div>

					{linkableProviders.length > 0 && (
						<div className="space-y-2 pt-2">
							<h3 className="text-sm font-medium">Connect another account</h3>
							<p className="text-xs text-muted-foreground">
								Connecting a provider sends you to its sign-in page; the identity you sign in with
								is then linked to this account.
							</p>
							<div className="flex flex-wrap gap-2 pt-1">
								{linkableProviders.map((provider) => {
									const Icon = getProviderIcon(provider.providerType);
									const label = provider.displayName || provider.registrationId || "provider";
									return (
										<Button
											key={provider.registrationId ?? label}
											variant="outline"
											size="sm"
											onClick={() => provider.registrationId && onLink(provider.registrationId)}
											disabled={!provider.registrationId}
											aria-label={`Connect ${label}`}
										>
											<Icon className="size-3.5 mr-1.5" aria-hidden="true" />
											Connect {label}
										</Button>
									);
								})}
							</div>
						</div>
					)}
				</>
			)}
		</section>
	);
}

interface UnlinkControlProps {
	name: string;
	providerType?: string;
	isOnlyIdentity: boolean;
	isUnlinking: boolean;
	onConfirm: () => void;
}

/**
 * The per-identity disconnect control. For the account's only remaining identity it renders a
 * disabled button with a tooltip explaining the lockout guard; otherwise a confirmation dialog
 * gates the (reversible) disconnect.
 */
function UnlinkControl({
	name,
	providerType,
	isOnlyIdentity,
	isUnlinking,
	onConfirm,
}: UnlinkControlProps) {
	if (isOnlyIdentity) {
		// The only sign-in method can't be removed (lockout guard). Disabled + a native hint;
		// removing it is done by deleting the account in the Danger Zone.
		return (
			<Button
				variant="ghost"
				size="sm"
				disabled
				aria-label={`Disconnect ${name}`}
				title="This is your only sign-in method — to remove it, delete your account in the Danger Zone."
				className="shrink-0 text-muted-foreground"
			>
				<Unlink className="size-3.5 mr-1.5" aria-hidden="true" />
				Disconnect
			</Button>
		);
	}

	return (
		<AlertDialog>
			<AlertDialogTrigger
				render={
					<Button
						variant="ghost"
						size="sm"
						disabled={isUnlinking}
						aria-label={`Disconnect ${name}`}
						className="shrink-0 text-muted-foreground hover:text-destructive"
					>
						{isUnlinking ? (
							<Spinner className="size-3.5 mr-1.5" aria-hidden="true" />
						) : (
							<Unlink className="size-3.5 mr-1.5" aria-hidden="true" />
						)}
						Disconnect
					</Button>
				}
			/>
			<AlertDialogContent>
				<AlertDialogHeader>
					<AlertDialogTitle>Disconnect {name}?</AlertDialogTitle>
					<AlertDialogDescription>
						You'll no longer be able to sign in to Hephaestus with this{" "}
						{friendlyProvider(providerType)} account. You can reconnect it anytime by signing in
						with {friendlyProvider(providerType)} again.
					</AlertDialogDescription>
				</AlertDialogHeader>
				<AlertDialogFooter>
					<AlertDialogCancel>Cancel</AlertDialogCancel>
					<AlertDialogAction onClick={onConfirm}>Disconnect</AlertDialogAction>
				</AlertDialogFooter>
			</AlertDialogContent>
		</AlertDialog>
	);
}
