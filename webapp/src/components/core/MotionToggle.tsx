import { Accessibility, EyeOff, Zap } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useAccessibilityStore } from "@/stores/accessibility-store";

export function MotionToggle() {
	const { motion, setMotion } = useAccessibilityStore();

	return (
		<DropdownMenu>
			<DropdownMenuTrigger render={<Button variant="outline" size="icon" />}>
				{motion === "reduced" ? (
					<EyeOff className="h-[1.2rem] w-[1.2rem]" />
				) : motion === "full" ? (
					<Zap className="h-[1.2rem] w-[1.2rem]" />
				) : (
					<Accessibility className="h-[1.2rem] w-[1.2rem]" />
				)}
				<span className="sr-only">Toggle motion settings</span>
			</DropdownMenuTrigger>
			<DropdownMenuContent align="end">
				<DropdownMenuItem onClick={() => setMotion("system")}>
					<Accessibility className="mr-2 h-4 w-4" />
					<span>System Default</span>
				</DropdownMenuItem>
				<DropdownMenuItem onClick={() => setMotion("reduced")}>
					<EyeOff className="mr-2 h-4 w-4" />
					<span>Reduced Motion</span>
				</DropdownMenuItem>
				<DropdownMenuItem onClick={() => setMotion("full")}>
					<Zap className="mr-2 h-4 w-4" />
					<span>Full Motion</span>
				</DropdownMenuItem>
			</DropdownMenuContent>
		</DropdownMenu>
	);
}
