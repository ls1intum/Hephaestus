import { EyeIcon, EyeOffIcon, OctagonXIcon } from "lucide-react";
import { useId, useState } from "react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
	DialogClose,
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
import { Spinner } from "@/components/ui/spinner";
import { IDENTITY_LOGIN_KINDS, type LoginProviderKind, PROVIDER_LABELS } from "./loginProviders";

export interface AddLoginProviderPayload {
	kind: LoginProviderKind;
	userInput: Record<string, string>;
}

export interface AddLoginProviderDialogProps {
	isSubmitting: boolean;
	/** Server-side failure message (e.g. issuer-discovery probe rejection) shown inline. */
	submitError?: string;
	onSubmit: (payload: AddLoginProviderPayload) => void;
}

const KIND_OPTIONS: Array<{ value: LoginProviderKind; label: string }> = IDENTITY_LOGIN_KINDS.map(
	(kind) => ({ value: kind, label: PROVIDER_LABELS[kind] }),
);

type FieldKey = "issuerUrl" | "clientId" | "clientSecret" | "displayName";

/**
 * Add-provider form rendered inside the parent `<Dialog>`. Collects the inline
 * credentials the OIDC-login ConnectionStrategy requires: `issuer_url`, `client_id`,
 * `client_secret`, `display_name`, plus optional `scopes`. Submitting fires
 * the `initiate` mutation; the synchronous SSRF-protected issuer probe either creates the
 * connection or returns a 400 whose `detail` is shown via `submitError`.
 */
export function AddLoginProviderDialog({
	isSubmitting,
	submitError,
	onSubmit,
}: AddLoginProviderDialogProps) {
	const ids = useId();
	const fieldId = (key: string) => `${ids}-${key}`;

	const [kind, setKind] = useState<LoginProviderKind>("OIDC_LOGIN_GITHUB");
	const [displayName, setDisplayName] = useState("");
	const [issuerUrl, setIssuerUrl] = useState("");
	const [clientId, setClientId] = useState("");
	const [clientSecret, setClientSecret] = useState("");
	const [scopes, setScopes] = useState("");
	const [showSecret, setShowSecret] = useState(false);
	const [errors, setErrors] = useState<Partial<Record<FieldKey, string>>>({});

	const validate = (): boolean => {
		const next: Partial<Record<FieldKey, string>> = {};
		if (!displayName.trim()) {
			next.displayName = "Give this provider a name your members will recognize.";
		}
		const trimmedIssuer = issuerUrl.trim();
		if (!trimmedIssuer) {
			next.issuerUrl = "The issuer / base URL is required.";
		} else {
			try {
				const url = new URL(trimmedIssuer);
				if (url.protocol !== "https:") {
					next.issuerUrl = "The issuer URL must use HTTPS.";
				}
			} catch {
				next.issuerUrl = "Enter a valid URL, e.g. https://git.example.com.";
			}
		}
		if (!clientId.trim()) {
			next.clientId = "The OAuth client ID is required.";
		}
		if (!clientSecret.trim()) {
			next.clientSecret = "The OAuth client secret is required.";
		}
		setErrors(next);
		return Object.keys(next).length === 0;
	};

	const handleSubmit = () => {
		if (isSubmitting) return;
		if (!validate()) return;
		const userInput: Record<string, string> = {
			issuer_url: issuerUrl.trim(),
			client_id: clientId.trim(),
			client_secret: clientSecret,
			display_name: displayName.trim(),
		};
		if (scopes.trim()) {
			userInput.scopes = scopes.trim();
		}
		onSubmit({ kind, userInput });
	};

	return (
		<DialogContent className="sm:max-w-lg" aria-describedby={fieldId("desc")}>
			<DialogHeader>
				<DialogTitle>Add a login provider</DialogTitle>
				<DialogDescription id={fieldId("desc")}>
					Bring your own GitHub Enterprise or self-hosted GitLab as a sign-in option. We verify the
					issuer is reachable before saving your OAuth app credentials.
				</DialogDescription>
			</DialogHeader>

			<form
				className="space-y-4"
				onSubmit={(e) => {
					e.preventDefault();
					handleSubmit();
				}}
			>
				<Field>
					<FieldLabel htmlFor={fieldId("kind")}>Provider type</FieldLabel>
					<Select
						value={kind}
						onValueChange={(value) => value && setKind(value as LoginProviderKind)}
						items={KIND_OPTIONS}
					>
						<SelectTrigger id={fieldId("kind")} className="w-full">
							<SelectValue placeholder="Select a provider type" />
						</SelectTrigger>
						<SelectContent>
							{KIND_OPTIONS.map((option) => (
								<SelectItem key={option.value} value={option.value}>
									{option.label}
								</SelectItem>
							))}
						</SelectContent>
					</Select>
				</Field>

				<Field data-invalid={errors.displayName ? "true" : undefined}>
					<FieldLabel htmlFor={fieldId("display-name")}>Display name</FieldLabel>
					<Input
						id={fieldId("display-name")}
						value={displayName}
						onChange={(e) => setDisplayName(e.target.value)}
						placeholder="e.g. Acme GitHub Enterprise"
						autoComplete="off"
						aria-required="true"
						aria-invalid={!!errors.displayName}
						aria-describedby={errors.displayName ? fieldId("display-name-error") : undefined}
					/>
					{errors.displayName && (
						<FieldError id={fieldId("display-name-error")}>{errors.displayName}</FieldError>
					)}
				</Field>

				<Field data-invalid={errors.issuerUrl ? "true" : undefined}>
					<FieldLabel htmlFor={fieldId("issuer-url")}>Issuer / base URL</FieldLabel>
					<Input
						id={fieldId("issuer-url")}
						type="url"
						value={issuerUrl}
						onChange={(e) => setIssuerUrl(e.target.value)}
						placeholder="https://git.example.com"
						autoComplete="off"
						aria-required="true"
						aria-invalid={!!errors.issuerUrl}
						aria-describedby={
							errors.issuerUrl ? fieldId("issuer-url-error") : fieldId("issuer-url-desc")
						}
					/>
					<FieldDescription id={fieldId("issuer-url-desc")}>
						Must be a publicly reachable HTTPS URL. We run an OIDC discovery probe against it.
					</FieldDescription>
					{errors.issuerUrl && (
						<FieldError id={fieldId("issuer-url-error")}>{errors.issuerUrl}</FieldError>
					)}
				</Field>

				<Field data-invalid={errors.clientId ? "true" : undefined}>
					<FieldLabel htmlFor={fieldId("client-id")}>Client ID</FieldLabel>
					<Input
						id={fieldId("client-id")}
						value={clientId}
						onChange={(e) => setClientId(e.target.value)}
						autoComplete="off"
						aria-required="true"
						aria-invalid={!!errors.clientId}
						aria-describedby={errors.clientId ? fieldId("client-id-error") : undefined}
					/>
					{errors.clientId && (
						<FieldError id={fieldId("client-id-error")}>{errors.clientId}</FieldError>
					)}
				</Field>

				<Field data-invalid={errors.clientSecret ? "true" : undefined}>
					<FieldLabel htmlFor={fieldId("client-secret")}>Client secret</FieldLabel>
					<div className="relative">
						<Input
							id={fieldId("client-secret")}
							type={showSecret ? "text" : "password"}
							value={clientSecret}
							onChange={(e) => setClientSecret(e.target.value)}
							autoComplete="off"
							className="pr-9"
							aria-required="true"
							aria-invalid={!!errors.clientSecret}
							aria-describedby={errors.clientSecret ? fieldId("client-secret-error") : undefined}
						/>
						<Button
							type="button"
							variant="ghost"
							size="icon-xs"
							className="absolute right-1.5 top-1/2 -translate-y-1/2"
							onClick={() => setShowSecret((v) => !v)}
							aria-label={showSecret ? "Hide client secret" : "Show client secret"}
							aria-pressed={showSecret}
						>
							{showSecret ? <EyeOffIcon className="size-3.5" /> : <EyeIcon className="size-3.5" />}
						</Button>
					</div>
					{errors.clientSecret && (
						<FieldError id={fieldId("client-secret-error")}>{errors.clientSecret}</FieldError>
					)}
				</Field>

				<Field>
					<FieldLabel htmlFor={fieldId("scopes")}>Scopes (optional)</FieldLabel>
					<Input
						id={fieldId("scopes")}
						value={scopes}
						onChange={(e) => setScopes(e.target.value)}
						placeholder="openid profile email"
						autoComplete="off"
						aria-describedby={fieldId("scopes-desc")}
					/>
					<FieldDescription id={fieldId("scopes-desc")}>
						Space-separated OAuth scopes. Leave blank to use the provider defaults.
					</FieldDescription>
				</Field>

				<div aria-live="assertive">
					{submitError && (
						<Alert variant="destructive">
							<OctagonXIcon aria-hidden="true" />
							<AlertTitle>Could not validate the provider</AlertTitle>
							<AlertDescription>{submitError}</AlertDescription>
						</Alert>
					)}
				</div>

				<DialogFooter>
					<DialogClose render={<Button type="button" variant="outline" disabled={isSubmitting} />}>
						Cancel
					</DialogClose>
					<Button type="submit" disabled={isSubmitting}>
						{isSubmitting && <Spinner className="mr-2" />}
						Validate &amp; add
					</Button>
				</DialogFooter>
			</form>
		</DialogContent>
	);
}
