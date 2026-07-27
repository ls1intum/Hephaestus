import { useState } from "react";
import type { InstanceLlmSettings, UpdateInstanceLlmSettingsRequest } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldContent, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";

export interface InstanceLlmSettingsCardProps {
	settings?: InstanceLlmSettings;
	isLoading: boolean;
	isSubmitting: boolean;
	onSave: (body: UpdateInstanceLlmSettingsRequest) => void;
}

/**
 * Instance-wide LLM governance (#1368): the provider-host allowlist and whether workspaces may
 * connect their own AI provider at all. Never surfaces egress/routing — the key always stays
 * server-side and traffic always goes through the in-app proxy, with no toggle for that.
 *
 * The form is mounted only once its settings exist, so its fields seed from them directly instead of
 * being copied in by an effect. An effect would fire again on every background refetch — a second
 * admin's change, or just this tab regaining focus past the query's `staleTime` — and silently
 * overwrite whatever the admin had typed but not yet saved.
 */
export function InstanceLlmSettingsCard({
	settings,
	isLoading,
	isSubmitting,
	onSave,
}: InstanceLlmSettingsCardProps) {
	if (isLoading || !settings) {
		return (
			<Card>
				<CardHeader>
					<CardTitle>Settings</CardTitle>
				</CardHeader>
			</Card>
		);
	}

	return (
		<InstanceLlmSettingsForm settings={settings} isSubmitting={isSubmitting} onSave={onSave} />
	);
}

interface InstanceLlmSettingsFormProps {
	settings: InstanceLlmSettings;
	isSubmitting: boolean;
	onSave: (body: UpdateInstanceLlmSettingsRequest) => void;
}

function InstanceLlmSettingsForm({ settings, isSubmitting, onSave }: InstanceLlmSettingsFormProps) {
	const [allowedHosts, setAllowedHosts] = useState(settings.allowedEgressHosts ?? "");
	const [allowWorkspaceConnections, setAllowWorkspaceConnections] = useState(
		settings.allowWorkspaceConnections,
	);

	// Derived, never stored: "is there anything to save" is a comparison against what the server holds
	// right now, and the server's answer moves — a stored flag would have to be invalidated by whatever
	// notices that, which is the same edge the seeding rule above exists to avoid.
	// Trimmed on the host list because that is the form the payload below is sent in, so a lone
	// trailing newline is not offered as a change.
	const savedHosts = (settings.allowedEgressHosts ?? "").trim();
	const dirty =
		allowedHosts.trim() !== savedHosts ||
		allowWorkspaceConnections !== settings.allowWorkspaceConnections;

	return (
		<Card>
			<CardHeader>
				<CardTitle>Settings</CardTitle>
				<CardDescription>Instance-wide rules that apply to every workspace.</CardDescription>
			</CardHeader>
			<CardContent className="space-y-4">
				<Field>
					<FieldLabel htmlFor="llm-settings-allowed-hosts">Allowed provider hosts</FieldLabel>
					<Textarea
						id="llm-settings-allowed-hosts"
						value={allowedHosts}
						onChange={(e) => setAllowedHosts(e.target.value)}
						placeholder="api.openai.com&#10;llm.example.com"
						rows={4}
					/>
					<FieldDescription>
						One host per line (or comma-separated). Blank allows any public host.
					</FieldDescription>
				</Field>

				<Field orientation="horizontal">
					<FieldContent>
						<FieldLabel htmlFor="llm-settings-allow-own-provider">
							Let workspaces add providers and models
						</FieldLabel>
						<FieldDescription>
							Controls new provider connections and new models. Existing providers and models remain
							manageable and are billed to the account that owns their credential.
						</FieldDescription>
					</FieldContent>
					<Switch
						id="llm-settings-allow-own-provider"
						checked={allowWorkspaceConnections}
						onCheckedChange={setAllowWorkspaceConnections}
					/>
				</Field>

				<div className="flex justify-end">
					<Button
						size="sm"
						disabled={!dirty || isSubmitting}
						onClick={() =>
							onSave({
								allowedEgressHosts: allowedHosts.trim(),
								allowWorkspaceConnections,
							})
						}
					>
						Save settings
					</Button>
				</div>
			</CardContent>
		</Card>
	);
}
