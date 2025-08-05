import type { UIArtifact } from "@/components/mentor/Artifact";
import { useCallback, useMemo, useState } from "react";

export const initialArtifactData: UIArtifact = {
	documentId: "init",
	content: "",
	kind: "text",
	title: "",
	status: "idle",
	isVisible: false,
	boundingBox: {
		top: 0,
		left: 0,
		width: 0,
		height: 0,
	},
};

type Selector<T> = (state: UIArtifact) => T;

export function useArtifactSelector<Selected>(selector: Selector<Selected>) {
	const [localArtifact] = useState<UIArtifact>(initialArtifactData);

	const selectedValue = useMemo(() => {
		return selector(localArtifact);
	}, [localArtifact, selector]);

	return selectedValue;
}

export function useArtifact() {
	const [localArtifact, setLocalArtifact] =
		useState<UIArtifact>(initialArtifactData);
	// biome-ignore lint/suspicious/noExplicitAny: Metadata can be any shape depending on artifact type
	const [localArtifactMetadata, setLocalArtifactMetadata] = useState<any>(null);

	const artifact = useMemo(() => {
		return localArtifact;
	}, [localArtifact]);

	const setArtifact = useCallback(
		(updaterFn: UIArtifact | ((currentArtifact: UIArtifact) => UIArtifact)) => {
			setLocalArtifact((currentArtifact) => {
				if (typeof updaterFn === "function") {
					return updaterFn(currentArtifact);
				}
				return updaterFn;
			});
		},
		[],
	);

	// biome-ignore lint/suspicious/noExplicitAny: Metadata can be any shape depending on artifact type
	const setMetadata = useCallback((metadata: any) => {
		setLocalArtifactMetadata(metadata);
	}, []);

	return useMemo(
		() => ({
			artifact,
			setArtifact,
			metadata: localArtifactMetadata,
			setMetadata,
		}),
		[artifact, setArtifact, localArtifactMetadata, setMetadata],
	);
}
