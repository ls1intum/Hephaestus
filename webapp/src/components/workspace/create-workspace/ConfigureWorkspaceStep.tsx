import { useEffect, useState } from "react";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { workspaceDetailsSchema } from "./schemas";
import { generateSlug } from "./slug-utils";
import { useWizard } from "./wizard-context";

export function ConfigureWorkspaceStep() {
	const { state, dispatch } = useWizard();
	const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

	// Auto-populate display name from group name on first entry.
	// Component remounts via key={stepKey} in parent, so deps are safe.
	useEffect(() => {
		if (!state.displayName && state.selectedGroup) {
			const name = state.selectedGroup.name;
			dispatch({ type: "SET_DISPLAY_NAME", value: name });
			dispatch({ type: "SET_SLUG", value: generateSlug(name), manual: false });
		}
	}, [state.displayName, state.selectedGroup, dispatch]);

	const validate = (overrides: Partial<{ displayName: string; workspaceSlug: string }> = {}) => {
		const result = workspaceDetailsSchema.safeParse({
			displayName: overrides.displayName ?? state.displayName,
			workspaceSlug: overrides.workspaceSlug ?? state.workspaceSlug,
		});
		if (!result.success) {
			const errors: Record<string, string> = {};
			for (const issue of result.error.issues) {
				const key = issue.path[0] as string;
				errors[key] = issue.message;
			}
			setFieldErrors(errors);
		} else {
			setFieldErrors({});
		}
	};

	const handleDisplayNameChange = (value: string) => {
		dispatch({ type: "SET_DISPLAY_NAME", value });
		const slug = !state.slugManuallyEdited ? generateSlug(value) : state.workspaceSlug;
		if (!state.slugManuallyEdited) {
			dispatch({ type: "SET_SLUG", value: slug, manual: false });
		}
		validate({ displayName: value, workspaceSlug: slug });
	};

	const handleSlugChange = (value: string) => {
		dispatch({ type: "SET_SLUG", value, manual: true });
		validate({ workspaceSlug: value });
	};

	return (
		<div className="flex flex-col gap-4">
			<Field data-invalid={fieldErrors.displayName ? "true" : undefined}>
				<FieldLabel htmlFor="workspace-display-name">Display Name</FieldLabel>
				<Input
					id="workspace-display-name"
					placeholder="My Workspace"
					value={state.displayName}
					onChange={(e) => handleDisplayNameChange(e.target.value)}
					aria-invalid={!!fieldErrors.displayName}
					aria-describedby={fieldErrors.displayName ? "workspace-display-name-error" : undefined}
					autoFocus
				/>
				{fieldErrors.displayName && (
					<FieldError id="workspace-display-name-error">{fieldErrors.displayName}</FieldError>
				)}
			</Field>

			<Field data-invalid={fieldErrors.workspaceSlug ? "true" : undefined}>
				<FieldLabel htmlFor="workspace-slug">URL Slug</FieldLabel>
				<Input
					id="workspace-slug"
					placeholder="my-workspace"
					value={state.workspaceSlug}
					onChange={(e) => handleSlugChange(e.target.value)}
					aria-invalid={!!fieldErrors.workspaceSlug}
					aria-describedby={
						fieldErrors.workspaceSlug ? "workspace-slug-error" : "workspace-slug-description"
					}
				/>
				<FieldDescription id="workspace-slug-description">
					Used in URLs: /w/<strong>{state.workspaceSlug || "my-workspace"}</strong>
				</FieldDescription>
				{fieldErrors.workspaceSlug && (
					<FieldError id="workspace-slug-error">{fieldErrors.workspaceSlug}</FieldError>
				)}
			</Field>

			{/* Summary */}
			<div className="rounded-lg border bg-muted/30 p-3 text-sm space-y-1.5">
				<h4 className="font-medium text-xs text-muted-foreground uppercase tracking-wider">
					Summary
				</h4>
				<SummaryRow label="Provider" value="GitLab" />
				<SummaryRow label="Instance" value={state.serverUrl || "https://gitlab.com"} />
				<SummaryRow label="Group" value={state.selectedGroup?.fullPath ?? "—"} />
				<SummaryRow label="Token owner" value={state.preflightResult?.username ?? "—"} />
			</div>
		</div>
	);
}

function SummaryRow({ label, value }: { label: string; value: string }) {
	return (
		<div className="flex justify-between">
			<span className="text-muted-foreground">{label}</span>
			<span className="font-medium truncate ml-4">{value}</span>
		</div>
	);
}
