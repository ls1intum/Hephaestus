import type { IAudioElement } from "@chainlit/react-client";

import { AudioElement } from "@/components/mentor/Elements/Audio";

interface InlinedAudioListProps {
	items: IAudioElement[];
}

const InlinedAudioList = ({ items }: InlinedAudioListProps) => {
	return (
		<div className="flex flex-col space-y-4">
			{items.map((audio) => (
				<div
					key={audio.id || audio.name || `audio-${audio.url}`}
					className="pt-2"
				>
					<AudioElement element={audio} />
				</div>
			))}
		</div>
	);
};

export { InlinedAudioList };
