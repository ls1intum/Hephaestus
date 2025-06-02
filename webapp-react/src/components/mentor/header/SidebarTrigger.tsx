import { Button } from "@/components/ui/button";
import {
	Tooltip,
	TooltipContent,
	TooltipProvider,
	TooltipTrigger,
} from "@/components/ui/tooltip";

import { useSidebar } from "@/components/ui/sidebar";
import { Sidebar } from "../icons/Sidebar";

export default function SidebarTrigger() {
	const { setOpen, open, openMobile, setOpenMobile, isMobile } = useSidebar();

	return (
		<TooltipProvider>
			<Tooltip>
				<TooltipTrigger asChild>
					<Button
						id="sidebar-trigger-button"
						onClick={() =>
							isMobile ? setOpenMobile(!openMobile) : setOpen(!open)
						}
						size="icon"
						variant="ghost"
						className="text-muted-foreground hover:text-muted-foreground"
					>
						<Sidebar className="!size-6" />
					</Button>
				</TooltipTrigger>
				<TooltipContent>
					<p>{open ? "Close sidebar" : "Open sidebar"}</p>
				</TooltipContent>
			</Tooltip>
		</TooltipProvider>
	);
}
