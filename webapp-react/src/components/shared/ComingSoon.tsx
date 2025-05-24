import { Construction, Hammer } from "lucide-react";
import type * as React from "react";

import { cn } from "@/lib/utils";

interface ComingSoonProps extends React.ComponentProps<"div"> {
	title?: string;
	description?: string;
	attribution?: string;
}

// Animation keyframes using CSS custom properties for better performance
const HAMMER_ANIMATION_STYLES = `
  @keyframes hammerSwing {
    0% { transform: rotate(0deg); }
    20% { transform: rotate(-45deg); }
    30% { transform: rotate(-45deg); }
    40% { transform: rotate(0deg); }
    100% { transform: rotate(0deg); }
  }
  
  @keyframes buildingImpact {
    0%, 37% { transform: scale(1) rotate(0deg); }
    40% { transform: scale(1.05) rotate(2deg); }
    42% { transform: scale(1.0) rotate(-1deg); }
    44% { transform: scale(1.02) rotate(1deg); }
    46%, 100% { transform: scale(1) rotate(0deg); }
  }
  
  .hammer-swing {
    transform-origin: bottom left;
    animation: hammerSwing 1s infinite ease-in-out;
  }
  
  .building-impact {
    animation: buildingImpact 1s infinite ease-in-out;
  }
` as const;

export function ComingSoon({
	title = "Coming Soon!",
	description = "We're hard at work building something amazing for you. Stay tuned!",
	attribution = "— The Hephaestus Team",
}: ComingSoonProps) {
	return (
		<>
			{/* Scoped styles for animations */}
			<style>{HAMMER_ANIMATION_STYLES}</style>

			<div className="w-full max-w-2xl px-6 py-12 text-center">
				<div className="relative mb-8 flex justify-center">
					<div className="relative">
            {/* Animated Construction Icon */}
						<div className="flex items-center justify-center building-impact size-16 rounded-full bg-primary/10">
              <Construction
                className="size-8 text-primary"
                aria-label="Building under construction"
              />
            </div>
						{/* Animated Hammer Icon */}
						<div
							className="hammer-swing absolute -top-5 -left-8 text-primary"
							role="img"
							aria-label="Hammer working on construction"
						>
							<Hammer className="size-12" />
						</div>
					</div>
				</div>
				<h1 className="mb-6 text-4xl font-bold tracking-tighter sm:text-5xl md:text-6xl">
					{title}
				</h1>

				<div className="space-y-4">
					<p className="mx-auto max-w-[600px] text-lg text-muted-foreground">
						{description}
					</p>
					<p className="text-sm font-medium text-primary">{attribution}</p>
				</div>
			</div>
		</>
	);
}
