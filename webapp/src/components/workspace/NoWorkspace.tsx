import { Folders } from "lucide-react";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";

export function NoWorkspace() {
	return (
		<Empty>
			<EmptyHeader>
				<EmptyMedia variant="icon">
					<Folders />
				</EmptyMedia>
				<EmptyTitle>No workspace</EmptyTitle>
				<EmptyDescription>
					You&apos;re not a member of any workspace yet.
				</EmptyDescription>
			</EmptyHeader>
		</Empty>
	);
}
