import { useEffect, useState } from "react";
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
 */
export function InstanceLlmSettingsCard({
	settings,
	isLoading,
	isSubmitting,
	onSave,
}: InstanceLlmSettingsCardProps) {
	const [allowedHosts, setAllowedHosts] = useState("");
	const [allowWorkspaceConnections, setAllowWorkspaceConnections] = useState(false);
	const [dirty, setDirty] = useState(false);

	useEffect(() => {
		if (!settings) return;
		setAllowedHosts(settings.allowedEgressHosts ?? "");
		setAllowWorkspaceConnections(settings.allowWorkspaceConnections);
		setDirty(false);
	}, [settings]);

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
						onChange={(e) => {
							setAllowedHosts(e.target.value);
							setDirty(true);
						}}
						placeholder="api.openai.com&#10;api.anthropic.com"
						rows={4}
					/>
					<FieldDescription>
						One host per line (or comma-separated). Blank allows any public host.
					</FieldDescription>
				</Field>

				<Field orientation="horizontal">
					<FieldContent>
						<FieldLabel htmlFor="llm-settings-allow-byo">
							Let workspaces connect their own AI provider
						</FieldLabel>
						<FieldDescription>
							Workspaces can add their own provider connection, billed to them directly.
						</FieldDescription>
					</FieldContent>
					<Switch
						id="llm-settings-allow-byo"
						checked={allowWorkspaceConnections}
						onCheckedChange={(checked) => {
							setAllowWorkspaceConnections(checked);
							setDirty(true);
						}}
					/>
				</Field>

				<div className="flex justify-end">
					<Button
						size="sm"
						disabled={!dirty || isSubmitting}
						onClick={() =>
							onSave({
								allowedEgressHosts: allowedHosts.trim() || undefined,
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
