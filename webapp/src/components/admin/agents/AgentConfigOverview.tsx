import { Bot, Clock3, Globe, KeyRound, Link2 } from "lucide-react";
import type { AgentConfig, AgentRunner } from "@/api/types.gen";
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

export interface AgentConfigOverviewProps {
	runners: AgentRunner[];
	configs: AgentConfig[];
	selectedRunnerId: number | "new" | null;
	selectedConfigId: number | "new" | null;
	isLoadingRunners: boolean;
	isLoadingConfigs: boolean;
	deletingRunnerId: number | null;
	deletingConfigId: number | null;
	onCreateRunner: () => void;
	onSelectRunner: (runnerId: number | "new") => void;
	onDeleteRunner: (runner: AgentRunner) => void;
	onCreateConfig: () => void;
	onSelectConfig: (configId: number | "new") => void;
	onDeleteConfig: (config: AgentConfig) => void;
}

export function AgentConfigOverview({
	runners,
	configs,
	selectedRunnerId,
	selectedConfigId,
	isLoadingRunners,
	isLoadingConfigs,
	deletingRunnerId,
	deletingConfigId,
	onCreateRunner,
	onSelectRunner,
	onDeleteRunner,
	onCreateConfig,
	onSelectConfig,
	onDeleteConfig,
}: AgentConfigOverviewProps) {
	return (
		<div className="grid gap-6 lg:grid-cols-2">
			<Card>
				<CardHeader>
					<CardTitle>Runners</CardTitle>
					<CardDescription>
						Executable runtimes used by agent configs. This is the GitHub-like execution layer.
					</CardDescription>
					<CardAction>
						<Button onClick={onCreateRunner}>New runner</Button>
					</CardAction>
				</CardHeader>
				<CardContent>
					{isLoadingRunners ? (
						<LoadingState />
					) : runners.length === 0 ? (
						<EmptyState
							title="No runners yet"
							description="Create a runner first so configs have an execution target."
						/>
					) : (
						<div className="space-y-3">
							{runners.map((runner) => (
								<Card
									key={runner.id}
									size="sm"
									className={
										selectedRunnerId === runner.id
											? "border-primary ring-primary/20 ring-2"
											: undefined
									}
								>
									<CardHeader>
										<CardTitle className="flex items-center justify-between gap-3">
											<span className="truncate">{runner.name}</span>
											<Badge variant="secondary">{formatAgentType(runner.agentType)}</Badge>
										</CardTitle>
										<CardDescription className="flex flex-wrap gap-2">
											<Badge variant="outline">{formatProvider(runner.llmProvider)}</Badge>
											<Badge variant="outline">{formatCredentialMode(runner.credentialMode)}</Badge>
											{runner.hasLlmApiKey && <Badge variant="outline">Stored credential</Badge>}
										</CardDescription>
									</CardHeader>
									<CardContent className="space-y-4">
										<dl className="grid gap-3 sm:grid-cols-2">
											<ConfigValue
												icon={<Bot className="size-4" />}
												label="Model"
												value={runner.modelName ?? "Workspace default"}
											/>
											<ConfigValue
												icon={<Clock3 className="size-4" />}
												label="Timeout"
												value={`${runner.timeoutSeconds}s`}
											/>
											<ConfigValue
												icon={<KeyRound className="size-4" />}
												label="Credentials"
												value={formatCredentialMode(runner.credentialMode)}
											/>
											<ConfigValue
												icon={<Globe className="size-4" />}
												label="Network"
												value={runner.allowInternet ? "Internet enabled" : "Sandbox only"}
											/>
										</dl>
										<div className="flex flex-col gap-2 sm:flex-row sm:justify-between">
											<Button variant="outline" onClick={() => onSelectRunner(runner.id)}>
												{selectedRunnerId === runner.id ? "Editing" : "Edit"}
											</Button>
											<Button
												variant="destructive"
												onClick={() => onDeleteRunner(runner)}
												disabled={deletingRunnerId === runner.id}
											>
												{deletingRunnerId === runner.id ? (
													<Spinner className="mr-2 size-4" />
												) : null}
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

			<Card>
				<CardHeader>
					<CardTitle>Agent Configs</CardTitle>
					<CardDescription>
						Logical review-agent entries that bind to a runner and decide whether new jobs are
						submitted.
					</CardDescription>
					<CardAction>
						<Button onClick={onCreateConfig} disabled={runners.length === 0}>
							New config
						</Button>
					</CardAction>
				</CardHeader>
				<CardContent>
					{isLoadingConfigs ? (
						<LoadingState />
					) : configs.length === 0 ? (
						<EmptyState
							title="No agent configs yet"
							description={
								runners.length === 0
									? "Create a runner before adding logical review-agent configs."
									: "Create configs after you have at least one runner."
							}
						/>
					) : (
						<div className="space-y-3">
							{configs.map((config) => (
								<Card
									key={config.id}
									size="sm"
									className={
										selectedConfigId === config.id
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
											<Badge variant="outline">Runner: {config.runnerName}</Badge>
											<Badge variant="outline">{formatAgentType(config.agentType)}</Badge>
										</CardDescription>
									</CardHeader>
									<CardContent className="space-y-4">
										<dl className="grid gap-3 sm:grid-cols-2">
											<ConfigValue
												icon={<Link2 className="size-4" />}
												label="Runner"
												value={config.runnerName}
											/>
											<ConfigValue
												icon={<Clock3 className="size-4" />}
												label="Timeout"
												value={`${config.timeoutSeconds}s`}
											/>
										</dl>
										<div className="flex flex-col gap-2 sm:flex-row sm:justify-between">
											<Button variant="outline" onClick={() => onSelectConfig(config.id)}>
												{selectedConfigId === config.id ? "Editing" : "Edit"}
											</Button>
											<Button
												variant="destructive"
												onClick={() => onDeleteConfig(config)}
												disabled={deletingConfigId === config.id}
											>
												{deletingConfigId === config.id ? (
													<Spinner className="mr-2 size-4" />
												) : null}
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
		</div>
	);
}

function LoadingState() {
	return (
		<div className="flex min-h-40 items-center justify-center">
			<Spinner className="size-6" />
		</div>
	);
}

function EmptyState(props: { title: string; description: string }) {
	return (
		<div className="rounded-xl border border-dashed px-6 py-12 text-center">
			<p className="text-base font-medium">{props.title}</p>
			<p className="mt-2 text-sm text-muted-foreground">{props.description}</p>
		</div>
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
