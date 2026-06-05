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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";

type ProviderType = "GITHUB" | "GITLAB";

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
	}, [open, editing]);

	const isGitlab = type === "GITLAB";

	const handleSubmit = (event: React.FormEvent) => {
		event.preventDefault();
		if (isEdit && editing) {
			const body: UpdateLoginProviderRequest = {
				displayName: displayName.trim() || undefined,
				baseUrl: isGitlab ? baseUrl.trim() || undefined : undefined,
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
			baseUrl: isGitlab ? baseUrl.trim() || undefined : undefined,
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
							A sign-in option offered on the login page. One provider per SCM instance — register
							the OAuth app on the instance and paste its client credentials here.
						</DialogDescription>
					</DialogHeader>

					<div className="space-y-2">
						<Label htmlFor="lp-registration-id">Registration ID</Label>
						<Input
							id="lp-registration-id"
							value={registrationId}
							onChange={(e) => setRegistrationId(e.target.value)}
							placeholder="gitlab-acme"
							disabled={isEdit}
							required={!isEdit}
							pattern="[a-z][a-z0-9-]{1,62}"
							autoComplete="off"
						/>
						<p className="text-xs text-muted-foreground">
							Stable id used in the OAuth callback path. Lowercase letters, digits, hyphens.
							Immutable once created.
						</p>
					</div>

					<div className="space-y-2">
						<Label htmlFor="lp-type">Provider type</Label>
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
							</SelectContent>
						</Select>
					</div>

					<div className="space-y-2">
						<Label htmlFor="lp-display-name">Display name</Label>
						<Input
							id="lp-display-name"
							value={displayName}
							onChange={(e) => setDisplayName(e.target.value)}
							placeholder="Defaults to the registration ID"
						/>
					</div>

					{isGitlab && (
						<div className="space-y-2">
							<Label htmlFor="lp-base-url">Instance base URL</Label>
							<Input
								id="lp-base-url"
								type="url"
								value={baseUrl}
								onChange={(e) => setBaseUrl(e.target.value)}
								placeholder="https://gitlab.example.com"
								required={isGitlab && !isEdit}
							/>
							<p className="text-xs text-muted-foreground">
								HTTPS only. GitHub is always github.com, so this field is GitLab-only.
							</p>
						</div>
					)}

					<div className="space-y-2">
						<Label htmlFor="lp-client-id">Client ID</Label>
						<Input
							id="lp-client-id"
							value={clientId}
							onChange={(e) => setClientId(e.target.value)}
							placeholder={isEdit ? "Leave blank to keep current" : ""}
							required={!isEdit}
							autoComplete="off"
						/>
					</div>

					<div className="space-y-2">
						<Label htmlFor="lp-client-secret">Client secret</Label>
						<Input
							id="lp-client-secret"
							type="password"
							value={clientSecret}
							onChange={(e) => setClientSecret(e.target.value)}
							placeholder={isEdit ? "Leave blank to keep current" : ""}
							required={!isEdit}
							autoComplete="off"
						/>
						<p className="text-xs text-muted-foreground">
							Sealed at rest; never displayed after saving.
						</p>
					</div>

					<div className="space-y-2">
						<Label htmlFor="lp-scopes">Scopes</Label>
						<Input
							id="lp-scopes"
							value={scopes}
							onChange={(e) => setScopes(e.target.value)}
							placeholder="Defaulted by provider type if blank"
						/>
					</div>

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
