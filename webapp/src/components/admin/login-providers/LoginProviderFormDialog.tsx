import { useEffect, useState } from "react";
import type {
	CreateLoginProviderRequest,
	LoginProviderView,
	UpdateLoginProviderRequest,
} from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";

/** Mirrors `CreateLoginProviderRequest["type"]` — every provider type the server accepts. */
type ProviderType = "GITHUB" | "GITLAB" | "SLACK" | "OUTLINE";

/** Scope hint per provider type — Outline's OAuth app only needs `read` for identity linking. */
function scopesPlaceholder(type: ProviderType): string {
	if (type === "SLACK") return "openid profile email";
	if (type === "OUTLINE") return "read";
	return "Defaulted by provider type if blank";
}

interface LoginProviderFormDialogProps {
	open: boolean;
	onOpenChange: (open: boolean) => void;
	/** When set, the dialog edits this provider; otherwise it creates a new one. */
	editing: LoginProviderView | null;
	isSubmitting: boolean;
	onCreate: (body: CreateLoginProviderRequest) => void;
	onUpdate: (registrationId: string, body: UpdateLoginProviderRequest) => void;
}

/**
 * Create/edit form for an instance login provider. On edit, the registration id + type are immutable
 * (they're the provider's identity) and the client secret is write-only — left blank, it is unchanged.
 */
export function LoginProviderFormDialog({
	open,
	onOpenChange,
	editing,
	isSubmitting,
	onCreate,
	onUpdate,
}: LoginProviderFormDialogProps) {
	const isEdit = editing !== null;
	const [registrationId, setRegistrationId] = useState("");
	const [type, setType] = useState<ProviderType>("GITLAB");
	const [displayName, setDisplayName] = useState("");
	const [baseUrl, setBaseUrl] = useState("");
	const [clientId, setClientId] = useState("");
	const [clientSecret, setClientSecret] = useState("");
	const [scopes, setScopes] = useState("");
	const [errors, setErrors] = useState<{ registrationId?: string; baseUrl?: string }>({});

	// Re-seed the form whenever the dialog opens for a different target.
	useEffect(() => {
		if (!open) return;
		setRegistrationId(editing?.registrationId ?? "");
		setType((editing?.type as ProviderType) ?? "GITLAB");
		setDisplayName(editing?.displayName ?? "");
		setBaseUrl(editing?.baseUrl ?? "");
		setClientId("");
		setClientSecret("");
		setScopes(editing?.scopes ?? "");
		setErrors({});
	}, [open, editing]);

	// GitHub is always github.com and Slack is always slack.com; GitLab and Outline are self-hosted
	// per instance, so only those two carry a base URL.
	const needsBaseUrl = type === "GITLAB" || type === "OUTLINE";
	const isSlack = type === "SLACK";
	const isOutline = type === "OUTLINE";

	// The redirect URI the operator must register on the upstream OAuth app. On edit the server
	// already computed it; on create we derive the same shape from this instance's origin.
	const redirectUri =
		editing?.redirectUri ??
		`${window.location.origin}/api/login/oauth2/code/${registrationId.trim() || "<registration-id>"}`;

	// Mirror the server-side constraints so the operator sees the problem inline before a round-trip:
	// the registration id is the immutable callback-path segment, and an instance base URL must be HTTPS.
	const REGISTRATION_ID_PATTERN = /^[a-z][a-z0-9-]{1,62}$/;
	const validate = (): boolean => {
		const next: { registrationId?: string; baseUrl?: string } = {};
		if (!isEdit && !REGISTRATION_ID_PATTERN.test(registrationId.trim())) {
			next.registrationId = "Lowercase letters, digits and hyphens; must start with a letter.";
		}
		if (needsBaseUrl && (!isEdit || baseUrl.trim())) {
			const value = baseUrl.trim();
			if (!isEdit && !value) {
				next.baseUrl = "An instance base URL is required.";
			} else if (value && !value.startsWith("https://")) {
				next.baseUrl = "Must be an HTTPS URL.";
			}
		}
		setErrors(next);
		return Object.keys(next).length === 0;
	};

	const handleSubmit = (event: React.FormEvent) => {
		event.preventDefault();
		if (!validate()) {
			return;
		}
		if (isEdit && editing) {
			const body: UpdateLoginProviderRequest = {
				displayName: displayName.trim() || undefined,
				baseUrl: needsBaseUrl ? baseUrl.trim() || undefined : undefined,
				clientId: clientId.trim() || undefined,
				clientSecret: clientSecret.trim() || undefined,
				scopes: scopes.trim() || undefined,
			};
			onUpdate(editing.registrationId, body);
			return;
		}
		const body: CreateLoginProviderRequest = {
			registrationId: registrationId.trim(),
			type,
			displayName: displayName.trim() || undefined,
			baseUrl: needsBaseUrl ? baseUrl.trim() || undefined : undefined,
			clientId: clientId.trim(),
			clientSecret: clientSecret.trim(),
			scopes: scopes.trim() || undefined,
		};
		onCreate(body);
	};

	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			<DialogContent className="sm:max-w-lg">
				<form onSubmit={handleSubmit} className="space-y-4">
					<DialogHeader>
						<DialogTitle>{isEdit ? "Edit login provider" : "Add login provider"}</DialogTitle>
						<DialogDescription>
							Configure sign-in and account-link providers. Slack and Outline are link-only: they
							appear in Settings for account linking, not on the public sign-in page.
						</DialogDescription>
					</DialogHeader>

					<Field data-invalid={errors.registrationId ? "true" : undefined}>
						<FieldLabel htmlFor="lp-registration-id">Registration ID</FieldLabel>
						<Input
							id="lp-registration-id"
							value={registrationId}
							onChange={(e) => setRegistrationId(e.target.value)}
							placeholder="gitlab-acme"
							disabled={isEdit}
							required={!isEdit}
							aria-invalid={errors.registrationId ? "true" : undefined}
							aria-describedby="lp-registration-id-description"
							autoComplete="off"
						/>
						<FieldDescription id="lp-registration-id-description">
							Stable id used in the OAuth callback path. Lowercase letters, digits, hyphens.
							Immutable once created.
						</FieldDescription>
						{errors.registrationId && <FieldError>{errors.registrationId}</FieldError>}
					</Field>

					<Field>
						<FieldLabel htmlFor="lp-type">Provider type</FieldLabel>
						<Select
							value={type}
							onValueChange={(v) => setType(v as ProviderType)}
							disabled={isEdit}
						>
							<SelectTrigger id="lp-type">
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value="GITHUB">GitHub</SelectItem>
								<SelectItem value="GITLAB">GitLab / self-hosted GitLab</SelectItem>
								<SelectItem value="SLACK">Slack / Sign in with Slack</SelectItem>
								<SelectItem value="OUTLINE">Outline (link-only)</SelectItem>
							</SelectContent>
						</Select>
						{isSlack && (
							<FieldDescription>
								Use the same Slack app client ID and secret. Add this provider's redirect URI to the
								Slack app redirect URLs.
							</FieldDescription>
						)}
						{isOutline && (
							<FieldDescription>
								Outline is <strong>link-only</strong>: users connect it from Settings so their
								documents are attributed to them — nobody signs in to Hephaestus with it. Create an
								OAuth app in Outline under <strong>Settings → Applications</strong> and register
								this redirect URI: <code className="break-all">{redirectUri}</code>
							</FieldDescription>
						)}
					</Field>

					<Field>
						<FieldLabel htmlFor="lp-display-name">Display name</FieldLabel>
						<Input
							id="lp-display-name"
							value={displayName}
							onChange={(e) => setDisplayName(e.target.value)}
							placeholder="Defaults to the registration ID"
						/>
					</Field>

					{needsBaseUrl && (
						<Field data-invalid={errors.baseUrl ? "true" : undefined}>
							<FieldLabel htmlFor="lp-base-url">Instance base URL</FieldLabel>
							<Input
								id="lp-base-url"
								type="url"
								value={baseUrl}
								onChange={(e) => setBaseUrl(e.target.value)}
								placeholder={
									isOutline ? "https://outline.example.com" : "https://gitlab.example.com"
								}
								required={!isEdit}
								aria-invalid={errors.baseUrl ? "true" : undefined}
								aria-describedby="lp-base-url-description"
							/>
							<FieldDescription id="lp-base-url-description">
								HTTPS only. GitHub and Slack are always at a fixed host, so this field applies to
								self-hosted GitLab and Outline instances.
							</FieldDescription>
							{errors.baseUrl && <FieldError>{errors.baseUrl}</FieldError>}
						</Field>
					)}

					<Field>
						<FieldLabel htmlFor="lp-client-id">Client ID</FieldLabel>
						<Input
							id="lp-client-id"
							value={clientId}
							onChange={(e) => setClientId(e.target.value)}
							placeholder={isEdit ? "Leave blank to keep current" : ""}
							required={!isEdit}
							autoComplete="off"
						/>
					</Field>

					<Field>
						<FieldLabel htmlFor="lp-client-secret">Client secret</FieldLabel>
						<Input
							id="lp-client-secret"
							type="password"
							value={clientSecret}
							onChange={(e) => setClientSecret(e.target.value)}
							placeholder={isEdit ? "Leave blank to keep current" : ""}
							required={!isEdit}
							autoComplete="off"
							aria-describedby="lp-client-secret-description"
						/>
						<FieldDescription id="lp-client-secret-description">
							Sealed at rest; never displayed after saving.
						</FieldDescription>
					</Field>

					<Field>
						<FieldLabel htmlFor="lp-scopes">Scopes</FieldLabel>
						<Input
							id="lp-scopes"
							value={scopes}
							onChange={(e) => setScopes(e.target.value)}
							placeholder={scopesPlaceholder(type)}
						/>
					</Field>

					<DialogFooter>
						<Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
							Cancel
						</Button>
						<Button type="submit" disabled={isSubmitting}>
							{isEdit ? "Save changes" : "Add provider"}
						</Button>
					</DialogFooter>
				</form>
			</DialogContent>
		</Dialog>
	);
}
