import { Sidebar, SidebarHeader, SidebarRail } from "@/components/ui/sidebar";
import SidebarTrigger from "../header/SidebarTrigger";

import { useNavigate } from "@tanstack/react-router";
import NewChatButton from "../header/NewChat";
import SearchChats from "./Search";
import { ThreadHistory } from "./ThreadHistory";

export default function LeftSidebar({
	...props
}: React.ComponentProps<typeof Sidebar>) {
	const navigate = useNavigate();
	return (
		<Sidebar {...props} className="border-none">
			<SidebarHeader className="py-3">
				<div className="flex items-center justify-between">
					<SidebarTrigger />
					<div className="flex items-center">
						<SearchChats />
						<NewChatButton navigate={navigate} />
					</div>
				</div>
			</SidebarHeader>
			<ThreadHistory />
			<SidebarRail />
		</Sidebar>
	);
}
