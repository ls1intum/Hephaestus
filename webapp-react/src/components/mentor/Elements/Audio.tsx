import { cn } from "@/lib/utils";

import type { IAudioElement } from "@chainlit/react-client";

const AudioElement = ({ element }: { element: IAudioElement }) => {
	if (!element.url) {
		return null;
	}

	return (
		<div className={cn("space-y-2", `${element.display}-audio`)}>
			<p className="text-sm leading-7 text-muted-foreground">{element.name}</p>
			<audio controls src={element.url} autoPlay={element.autoPlay}>
				<track kind="captions" label="No captions available" />
				Your browser does not support the audio element.
			</audio>
		</div>
	);
};

export { AudioElement };
