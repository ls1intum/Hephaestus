import { useMutation } from "@tanstack/react-query";
import { CircleCheckIcon, EyeIcon, EyeOffIcon, OctagonXIcon } from "lucide-react";
import { useState } from "react";
import { gitLabPreflightMutation } from "@/api/@tanstack/react-query.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { type ConnectionFormData, connectionSchema } from "./schemas";
import { useWizard } from "./wizard-context";

export function ConnectGitLabStep() {
	const { state, dispatch } = useWizard();
	const [showToken, setShowToken] = useState(false);
	const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof ConnectionFormData, string>>>(
		{},
	);

	const preflight = useMutation({
		...gitLabPreflightMutation(),
		onSuccess: (data) => {
			dispatch({ type: "SET_PREFLIGHT_RESULT", result: data });
		},
		onError: (error) => {
			console.error("GitLab preflight failed:", {
				serverUrl: state.serverUrl,
				message: error instanceof Error ? error.message : "Unknown error",
			});
		},
	});

	const handleValidate = () => {
		if (preflight.isPending) return;
		const result = connectionSchema.safeParse({
			serverUrl: state.serverUrl,
			personalAccessToken: state.personalAccessToken,
		});
		if (!result.success) {
			const errors: Partial<Record<keyof ConnectionFormData, string>> = {};
			for (const issue of result.error.issues) {
				const key = issue.path[0] as keyof ConnectionFormData;
				errors[key] = issue.message;
			}
			setFieldErrors(errors);
			return;
		}
		setFieldErrors({});
		// Persist trimmed values back to state so downstream steps use normalized data
		dispatch({ type: "SET_SERVER_URL", value: result.data.serverUrl });
		dispatch({ type: "SET_PAT", value: result.data.personalAccessToken });
		// Reset any stale mutation state before firing the new request
		preflight.reset();
		preflight.mutate({
			body: {
				personalAccessToken: result.data.personalAccessToken,
				serverUrl: result.data.serverUrl || undefined,
			},
		});
	};

	const settingsBaseUrl = state.serverUrl || "https://gitlab.com";

	return (
		<div className="flex flex-col gap-4">
			<Field data-invalid={fieldErrors.serverUrl ? "true" : undefined}>
				<FieldLabel htmlFor="gitlab-server-url">GitLab Instance</FieldLabel>
				<Input
					id="gitlab-server-url"
					value={state.serverUrl || "https://gitlab.com"}
					disabled
					aria-describedby="gitlab-server-url-description"
				/>
				<FieldDescription id="gitlab-server-url-description">
					Configured by your administrator.
				</FieldDescription>
			</Field>

			<Field data-invalid={fieldErrors.personalAccessToken ? "true" : undefined}>
				<FieldLabel htmlFor="gitlab-pat">Access Token</FieldLabel>
				<div className="relative">
					<Input
						id="gitlab-pat"
						type={showToken ? "text" : "password"}
						placeholder="glpat-... or glgat-..."
						value={state.personalAccessToken}
						onChange={(e) => dispatch({ type: "SET_PAT", value: e.target.value })}
						autoComplete="off"
						className="pr-9"
						aria-required="true"
						aria-invalid={!!fieldErrors.personalAccessToken}
						aria-describedby={
							fieldErrors.personalAccessToken ? "gitlab-pat-error" : "gitlab-pat-description"
						}
						onKeyDown={(e) => {
							if (e.key === "Enter") {
								e.preventDefault();
								handleValidate();
							}
						}}
					/>
					<Button
						type="button"
						variant="ghost"
						size="icon-xs"
						className="absolute right-1.5 top-1/2 -translate-y-1/2"
						onClick={() => setShowToken(!showToken)}
						aria-label={showToken ? "Hide token" : "Show token"}
						aria-pressed={showToken}
					>
						{showToken ? <EyeOffIcon className="size-3.5" /> : <EyeIcon className="size-3.5" />}
					</Button>
				</div>
				<FieldDescription id="gitlab-pat-description">
					Use a{" "}
					<a
						href={`${settingsBaseUrl}/help/user/group/settings/group_access_tokens`}
						target="_blank"
						rel="noopener noreferrer"
					>
						Group Access Token
					</a>{" "}
					with <code className="text-xs">api</code> scope (recommended), or a{" "}
					<a
						href={`${settingsBaseUrl}/-/user_settings/personal_access_tokens`}
						target="_blank"
						rel="noopener noreferrer"
					>
						Personal Access Token
					</a>
					.
				</FieldDescription>
				{fieldErrors.personalAccessToken && (
					<FieldError id="gitlab-pat-error">{fieldErrors.personalAccessToken}</FieldError>
				)}
			</Field>

			<Button
				type="button"
				onClick={handleValidate}
				disabled={!state.personalAccessToken.trim() || preflight.isPending}
			>
				{preflight.isPending && <Spinner className="mr-2" />}
				Validate Token
			</Button>

			{state.preflightResult?.valid && (
				<Alert>
					<CircleCheckIcon aria-hidden="true" className="text-green-600 dark:text-green-400" />
					<AlertTitle>Token valid</AlertTitle>
					<AlertDescription>
						Authenticated as <strong>{state.preflightResult.username}</strong>
					</AlertDescription>
				</Alert>
			)}

			{state.preflightResult && !state.preflightResult.valid && (
				<Alert variant="destructive">
					<OctagonXIcon aria-hidden="true" />
					<AlertTitle>Validation failed</AlertTitle>
					<AlertDescription>
						{state.preflightResult.error ||
							"The token could not be validated. Check your token and try again."}
					</AlertDescription>
				</Alert>
			)}

			{preflight.isError && !state.preflightResult && (
				<Alert variant="destructive">
					<OctagonXIcon aria-hidden="true" />
					<AlertTitle>Connection error</AlertTitle>
					<AlertDescription>
						Could not reach the GitLab instance. Check the URL and try again.
					</AlertDescription>
				</Alert>
			)}
		</div>
	);
}
