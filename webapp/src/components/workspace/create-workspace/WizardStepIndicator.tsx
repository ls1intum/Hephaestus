import { CheckIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import type { WizardStep } from "./wizard-context";

const STEPS = [{ label: "Connect" }, { label: "Select Group" }, { label: "Configure" }] as const;

export function WizardStepIndicator({ currentStep }: { currentStep: WizardStep }) {
	return (
		<ol aria-label="Wizard progress" className="flex items-center gap-2">
			{STEPS.map((step, index) => {
				const stepNumber = (index + 1) as WizardStep;
				const isCompleted = stepNumber < currentStep;
				const isCurrent = stepNumber === currentStep;
				return (
					<li
						key={step.label}
						className="flex items-center gap-2"
						aria-current={isCurrent ? "step" : undefined}
					>
						<span
							className={cn(
								"flex size-6 shrink-0 items-center justify-center rounded-full text-xs font-medium",
								isCompleted && "bg-primary text-primary-foreground",
								isCurrent && "border-2 border-primary text-primary",
								!isCompleted &&
									!isCurrent &&
									"border border-muted-foreground/30 text-muted-foreground",
							)}
						>
							{isCompleted ? (
								<>
									<CheckIcon className="size-3.5" aria-hidden="true" />
									<span className="sr-only">Step {stepNumber} completed</span>
								</>
							) : (
								stepNumber
							)}
						</span>
						<span
							className={cn(
								"text-sm hidden sm:inline",
								isCurrent ? "font-medium text-foreground" : "text-muted-foreground",
							)}
						>
							{step.label}
						</span>
						{index < STEPS.length - 1 && (
							<span
								className={cn(
									"h-px w-6 sm:w-8",
									isCompleted ? "bg-primary" : "bg-muted-foreground/30",
								)}
							/>
						)}
					</li>
				);
			})}
		</ol>
	);
}
