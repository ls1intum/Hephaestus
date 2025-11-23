import { Link } from "@tanstack/react-router";
import { ShieldAlert } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Empty } from "@/components/ui/empty";

export function NoWorkspace() {
	return (
		<div className="max-w-xl mx-auto py-10">
			<Empty
				title="You're not in a workspace yet"
				description="Workspaces keep data scoped and permissions enforced. Ask an admin to add you or create one if you have permissions."
				icon={<ShieldAlert className="size-5" />}
				action={
					<Button asChild variant="default">
						<Link to="/about">Learn more</Link>
					</Button>
				}
			/>
		</div>
	);
}
