import { useEffect, useState } from "react";
import type { InstanceLlmSettings, UpdateInstanceLlmSettingsRequest } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldContent, FieldDescription, FieldLabel } from "@/components/ui/field";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";

type UnpricedPolicy = NonNullable<UpdateInstanceLlmSettingsRequest["defaultUnpricedPolicy"]>;

function unpricedPolicyOf(value: string): UnpricedPolicy {
	return value === "BLOCK" ? "BLOCK" : "WARN";
}

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
	const [defaultUnpricedPolicy, setDefaultUnpricedPolicy] = useState<UnpricedPolicy>("WARN");
	const [dirty, setDirty] = useState(false);

	useEffect(() => {
		if (!settings) return;
		setAllowedHosts(settings.allowedEgressHosts ?? "");
		setAllowWorkspaceConnections(settings.allowWorkspaceConnections);
		setDefaultUnpricedPolicy(unpricedPolicyOf(settings.defaultUnpricedPolicy));
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
						placeholder="api.openai.com&#10;llm.example.com"
						rows={4}
					/>
					<FieldDescription>
						One host per line (or comma-separated). Blank allows any public host.
					</FieldDescription>
				</Field>

				<Field orientation="horizontal">
					<FieldContent>
						<FieldLabel htmlFor="llm-settings-allow-byo">
							Let workspaces add providers and models
						</FieldLabel>
						<FieldDescription>
							Controls new provider connections and new models. Existing providers and models remain
							manageable and are billed to the account that owns their credential.
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

				<Field>
					<FieldLabel>Usage without a known price</FieldLabel>
					<FieldDescription>
						Choose the instance-wide safety default when an OpenAI-compatible provider reports usage
						that Hephaestus cannot price.
					</FieldDescription>
					<RadioGroup
						value={defaultUnpricedPolicy}
						onValueChange={(value) => {
							if (!value) return;
							setDefaultUnpricedPolicy(value as UnpricedPolicy);
							setDirty(true);
						}}
						aria-label="Usage without a known price"
					>
						<div className="flex items-start gap-2 rounded-lg border p-3">
							<RadioGroupItem value="WARN" id="llm-settings-unpriced-warn" className="mt-0.5" />
							<label htmlFor="llm-settings-unpriced-warn" className="space-y-1 text-sm">
								<span className="block font-medium">Warn and continue</span>
								<span className="block text-muted-foreground">
									Keep AI work running and mark reported spend as not fully verifiable.
								</span>
							</label>
						</div>
						<div className="flex items-start gap-2 rounded-lg border p-3">
							<RadioGroupItem value="BLOCK" id="llm-settings-unpriced-block" className="mt-0.5" />
							<label htmlFor="llm-settings-unpriced-block" className="space-y-1 text-sm">
								<span className="block font-medium">Block new AI work</span>
								<span className="block text-muted-foreground">
									Pauses new AI work across the instance after unknown-price usage is recorded,
									until pricing is configured.
								</span>
							</label>
						</div>
					</RadioGroup>
				</Field>

				<div className="flex justify-end">
					<Button
						size="sm"
						disabled={!dirty || isSubmitting}
						onClick={() =>
							onSave({
								allowedEgressHosts: allowedHosts.trim(),
								allowWorkspaceConnections,
								defaultUnpricedPolicy,
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
