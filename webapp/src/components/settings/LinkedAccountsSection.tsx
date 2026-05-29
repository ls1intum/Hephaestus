import { LinkIcon, type LucideIcon } from "lucide-react";
import type { IdentityProviderView, IdentityView } from "@/api/types.gen";
import { GithubIcon, GitlabIcon } from "@/components/icons/brand";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";

const PROVIDER_ICONS: Record<string, LucideIcon> = {
	GITHUB: GithubIcon,
	GITLAB: GitlabIcon,
};

/**
 * Resolve a brand icon from a provider type (e.g. "GITHUB", "GITLAB"). Falls back
 * to a generic link icon for unknown providers so new IdPs render gracefully.
 */
function getProviderIcon(providerType?: string): LucideIcon {
	if (!providerType) return LinkIcon;
	return PROVIDER_ICONS[providerType.toUpperCase()] ?? LinkIcon;
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
	/** Identities already federated to this account (read-only in this view). */
	identities: IdentityView[];
	/** Sign-in providers available to link (offered when not already linked). */
	providers: IdentityProviderView[];
	/**
	 * Start the link flow for a provider. Re-runs sign-in with that provider and
	 * attaches the resulting identity to the current account (top-level redirect).
	 */
	onLink: (registrationId: string) => void;
	isLoading?: boolean;
	isError?: boolean;
}

/**
 * Settings section for federated identities (ADR 0017 native auth).
 *
 * Linked identities are read-only — there is no unlink endpoint. To add another
 * identity the user re-runs sign-in with that provider via {@link onLink}, which
 * links the resulting identity to the current account.
 */
export function LinkedAccountsSection({
	identities,
	providers,
	onLink,
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

	return (
		<section className="space-y-4" aria-labelledby="linked-accounts-heading">
			<div className="space-y-1">
				<h2 id="linked-accounts-heading" className="text-xl font-semibold">
					Connected Accounts
				</h2>
				<p className="text-sm text-muted-foreground">
					The identity providers linked to your account. Linking another provider re-runs sign-in
					with it and attaches that login to this account.
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
							const name = identity.displayName || identity.username || identity.subject;
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
								</div>
							);
						})}
					</div>

					{linkableProviders.length > 0 && (
						<div className="space-y-2 pt-2">
							<h3 className="text-sm font-medium">Link another account</h3>
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
