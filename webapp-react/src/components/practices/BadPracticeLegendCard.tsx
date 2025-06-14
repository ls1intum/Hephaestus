import {
	Card,
	CardContent,
	CardDescription,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";
import { InfoIcon } from "@primer/octicons-react";
import { stateConfig } from "./utils";

export function BadPracticeLegendCard() {
	const stateList = Object.values(stateConfig);

	return (
		<Card>
			<CardHeader>
				<CardTitle>
					<InfoIcon className="inline mr-2 h-4 w-4" /> Practice Legend
				</CardTitle>
				<CardDescription>
					Understanding the pull request practice indicators
				</CardDescription>
			</CardHeader>
			<CardContent>
				<div className="space-y-3">
					<div className="grid grid-cols-1 gap-2">
						{stateList.map((state) => {
							const Icon = state.icon;
							return (
								<div key={state.text} className="flex items-center gap-2">
									<Icon className={`${state.color} h-4 w-4`} />
									<span className="text-muted-foreground">{state.text}</span>
								</div>
							);
						})}
					</div>

					<div className="pt-2 border-t">
						<p className="text-sm text-muted-foreground">
							We analyze your pull requests to celebrate what you're doing well
							and suggest improvements, helping you grow as a developer and
							level up your development workflow.
						</p>
					</div>
				</div>
			</CardContent>
		</Card>
	);
}
