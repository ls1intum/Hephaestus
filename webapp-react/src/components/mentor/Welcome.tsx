import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Bot } from "lucide-react";

/**
 * Welcome component for the AI Mentor chat interface.
 * Displays a welcoming message and suggested topics when no messages are present.
 */
function Welcome() {
	return (
		<div className="flex flex-1 items-center justify-center p-8">
			<div className="text-center max-w-md mx-auto">
				<div className="mb-6">
					<Avatar className="h-16 w-16 mx-auto">
						<AvatarFallback className="bg-mentor/10 text-mentor">
							<Bot className="h-8 w-8" />
						</AvatarFallback>
					</Avatar>
				</div>

				<div className="space-y-3">
					<h3 className="text-lg font-semibold text-foreground">
						Hey there! 👋 I'm your AI Mentor
					</h3>
					<p className="text-sm text-muted-foreground leading-relaxed">
						I'm here to help you navigate the teamwork side of software
						development. Think of me as your friendly guide for everything that
						happens around your code - from planning to collaboration to getting
						your work shipped.
					</p>
				</div>

				{/* Suggested topics */}
				<div className="mt-6 space-y-2">
					<p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
						I can help you with:
					</p>
					<div className="flex flex-wrap gap-2 justify-center">
						{[
							"Writing good issues",
							"Code review basics",
							"Planning features",
							"Working with teams",
						].map((topic) => (
							<span
								key={topic}
								className="px-3 py-1 bg-muted/50 text-muted-foreground text-xs rounded-full"
							>
								{topic}
							</span>
						))}
					</div>
				</div>
			</div>
		</div>
	);
}

export { Welcome };
