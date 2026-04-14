import { Bot, Clock3, Globe, KeyRound, Plus, Trash2 } from "lucide-react";
import type { AgentConfig } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Card,
	CardAction,
	CardContent,
	CardDescription,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";
import { Spinner } from "@/components/ui/spinner";
import { formatAgentType, formatCredentialMode, formatProvider } from "./utils";

export interface AgentConfigListProps {
	configs: AgentConfig[];
	isLoading: boolean;
	editingConfigId: number | "new";
	deletingConfigId: number | null;
	onCreateNew: () => void;
	onEdit: (config: AgentConfig) => void;
	onDelete: (config: AgentConfig) => void;
}

export function AgentConfigList({
	configs,
	isLoading,
	editingConfigId,
	deletingConfigId,
	onCreateNew,
	onEdit,
	onDelete,
}: AgentConfigListProps) {
	return (
		<Card>
			<CardHeader>
				<CardTitle>Agent configurations</CardTitle>
				<CardDescription>
					Each enabled configuration receives its own practice-review job for this workspace.
				</CardDescription>
				<CardAction>
					<Button onClick={onCreateNew}>
						<Plus className="mr-2 size-4" />
						New config
					</Button>
				</CardAction>
			</CardHeader>
			<CardContent>
				{isLoading ? (
					<div className="flex min-h-40 items-center justify-center">
						<Spinner className="size-6" />
					</div>
				) : configs.length === 0 ? (
					<div className="rounded-xl border border-dashed px-6 py-12 text-center">
						<p className="text-base font-medium">No agent configs yet</p>
						<p className="mt-2 text-sm text-muted-foreground">
							Create a Claude Code or Pi runtime to start collecting practice-review jobs.
						</p>
					</div>
				) : (
					<div className="space-y-3">
						{configs.map((config) => (
							<Card
								key={config.id}
								size="sm"
								className={
									editingConfigId === config.id
										? "border-primary ring-primary/20 ring-2"
										: undefined
								}
							>
								<CardHeader>
									<CardTitle className="flex items-center justify-between gap-3">
										<span className="truncate">{config.name}</span>
										<Badge variant={config.enabled ? "default" : "outline"}>
											{config.enabled ? "Enabled" : "Disabled"}
										</Badge>
									</CardTitle>
									<CardDescription className="flex flex-wrap gap-2">
										<Badge variant="secondary">{formatAgentType(config.agentType)}</Badge>
										<Badge variant="outline">{formatProvider(config.llmProvider)}</Badge>
										<Badge variant="outline">{formatCredentialMode(config.credentialMode)}</Badge>
										{config.hasLlmApiKey && <Badge variant="outline">Stored credential</Badge>}
									</CardDescription>
								</CardHeader>
								<CardContent className="space-y-4">
									<dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
										<ConfigValue
											icon={<Bot className="size-4" />}
											label="Model"
											value={config.modelName ?? "Workspace default"}
										/>
										<ConfigValue
											icon={<Clock3 className="size-4" />}
											label="Timeout"
											value={`${config.timeoutSeconds}s`}
										/>
										<ConfigValue
											icon={<KeyRound className="size-4" />}
											label="Credentials"
											value={formatCredentialMode(config.credentialMode)}
										/>
										<ConfigValue
											icon={<Globe className="size-4" />}
											label="Network"
											value={config.allowInternet ? "Internet enabled" : "Sandbox only"}
										/>
									</dl>

									{config.modelVersion && (
										<p className="text-sm text-muted-foreground">
											Version snapshot:{" "}
											<span className="font-medium text-foreground">{config.modelVersion}</span>
										</p>
									)}

									<div className="flex flex-col gap-2 sm:flex-row sm:justify-between">
										<Button variant="outline" onClick={() => onEdit(config)}>
											{editingConfigId === config.id ? "Editing" : "Edit"}
										</Button>
										<Button
											variant="destructive"
											onClick={() => onDelete(config)}
											disabled={deletingConfigId === config.id}
										>
											{deletingConfigId === config.id ? (
												<Spinner className="mr-2 size-4" />
											) : (
												<Trash2 className="mr-2 size-4" />
											)}
											Delete
										</Button>
									</div>
								</CardContent>
							</Card>
						))}
					</div>
				)}
			</CardContent>
		</Card>
	);
}

function ConfigValue(props: { icon: React.ReactNode; label: string; value: string }) {
	return (
		<div className="space-y-1 rounded-lg border px-3 py-2">
			<dt className="flex items-center gap-2 text-xs uppercase tracking-wide text-muted-foreground">
				{props.icon}
				{props.label}
			</dt>
			<dd className="text-sm font-medium text-foreground">{props.value}</dd>
		</div>
	);
}
