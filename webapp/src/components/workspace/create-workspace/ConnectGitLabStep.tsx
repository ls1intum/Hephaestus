import { useMutation } from "@tanstack/react-query";
import { CircleCheckIcon, EyeIcon, EyeOffIcon, OctagonXIcon } from "lucide-react";
import { useState } from "react";
import { gitLabPreflightMutation } from "@/api/@tanstack/react-query.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { connectionSchema } from "./schemas";
import { useWizard } from "./wizard-context";

export function ConnectGitLabStep() {
	const { state, dispatch } = useWizard();
	const [showToken, setShowToken] = useState(false);
	const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

	const preflight = useMutation({
		...gitLabPreflightMutation(),
		onSuccess: (data) => {
			dispatch({ type: "SET_PREFLIGHT_RESULT", result: data });
		},
	});

	const handleValidate = () => {
		const result = connectionSchema.safeParse({
			serverUrl: state.serverUrl,
			personalAccessToken: state.personalAccessToken,
		});
		if (!result.success) {
			const errors: Record<string, string> = {};
			for (const issue of result.error.issues) {
				const key = issue.path[0] as string;
				errors[key] = issue.message;
			}
			setFieldErrors(errors);
			return;
		}
		setFieldErrors({});
		dispatch({ type: "CLEAR_PREFLIGHT" });
		preflight.mutate({
			body: {
				personalAccessToken: result.data.personalAccessToken,
				serverUrl: result.data.serverUrl || undefined,
			},
		});
	};

	// Only use validated server URL for the settings link
	const settingsBaseUrl = state.preflightResult?.valid
		? state.serverUrl || "https://gitlab.com"
		: "https://gitlab.com";

	return (
		<div className="flex flex-col gap-4">
			<Field>
				<FieldLabel htmlFor="gitlab-server-url">GitLab Instance URL</FieldLabel>
				<Input
					id="gitlab-server-url"
					placeholder="https://gitlab.com"
					value={state.serverUrl}
					onChange={(e) => dispatch({ type: "SET_SERVER_URL", value: e.target.value })}
					autoComplete="url"
					autoFocus
				/>
				<FieldDescription>
					Leave empty for gitlab.com. Enter a custom URL for self-hosted instances.
				</FieldDescription>
				{fieldErrors.serverUrl && <FieldError>{fieldErrors.serverUrl}</FieldError>}
			</Field>

			<Field>
				<FieldLabel htmlFor="gitlab-pat">Personal Access Token</FieldLabel>
				<div className="relative">
					<Input
						id="gitlab-pat"
						type={showToken ? "text" : "password"}
						placeholder="glpat-xxxxxxxxxxxxxxxxxxxx"
						value={state.personalAccessToken}
						onChange={(e) => dispatch({ type: "SET_PAT", value: e.target.value })}
						autoComplete="off"
						className="pr-9"
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
					>
						{showToken ? <EyeOffIcon className="size-3.5" /> : <EyeIcon className="size-3.5" />}
					</Button>
				</div>
				<FieldDescription>
					Create a token with <code className="text-xs">api</code> scope at your{" "}
					<a
						href={`${settingsBaseUrl}/-/user_settings/personal_access_tokens`}
						target="_blank"
						rel="noopener noreferrer"
					>
						GitLab settings
					</a>
					.
				</FieldDescription>
				{fieldErrors.personalAccessToken && (
					<FieldError>{fieldErrors.personalAccessToken}</FieldError>
				)}
			</Field>

			<Button
				type="button"
				onClick={handleValidate}
				disabled={!state.personalAccessToken || preflight.isPending}
			>
				{preflight.isPending && <Spinner className="mr-2" />}
				Validate Token
			</Button>

			{state.preflightResult?.valid && (
				<Alert>
					<CircleCheckIcon className="text-green-600 dark:text-green-400" />
					<AlertTitle>Token valid</AlertTitle>
					<AlertDescription>
						Authenticated as <strong>{state.preflightResult.username}</strong>
					</AlertDescription>
				</Alert>
			)}

			{state.preflightResult && !state.preflightResult.valid && (
				<Alert variant="destructive">
					<OctagonXIcon />
					<AlertTitle>Validation failed</AlertTitle>
					<AlertDescription>
						{state.preflightResult.error ||
							"The token could not be validated. Check your token and try again."}
					</AlertDescription>
				</Alert>
			)}

			{preflight.isError && !state.preflightResult && (
				<Alert variant="destructive">
					<OctagonXIcon />
					<AlertTitle>Connection error</AlertTitle>
					<AlertDescription>
						Could not reach the GitLab instance. Check the URL and try again.
					</AlertDescription>
				</Alert>
			)}
		</div>
	);
}
