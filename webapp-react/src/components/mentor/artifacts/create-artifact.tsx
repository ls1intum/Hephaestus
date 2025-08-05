import type { ChatMessage, CustomUIDataTypes } from "@/lib/types";
import type { UseChatHelpers } from "@ai-sdk/react";
import type { DataUIPart } from "ai";
import type { ComponentType, Dispatch, ReactNode, SetStateAction } from "react";
import type { UIArtifact } from "../Artifact";

// biome-ignore lint/suspicious/noExplicitAny: Ignore for now
export type ArtifactActionContext<M = any> = {
	content: string;
	handleVersionChange: (type: "next" | "prev" | "toggle" | "latest") => void;
	currentVersionIndex: number;
	isCurrentVersion: boolean;
	mode: "edit" | "diff";
	metadata: M;
	setMetadata: Dispatch<SetStateAction<M>>;
};

// biome-ignore lint/suspicious/noExplicitAny: Ignore for now
type ArtifactAction<M = any> = {
	icon: ReactNode;
	label?: string;
	description: string;
	onClick: (context: ArtifactActionContext<M>) => Promise<void> | void;
	isDisabled?: (context: ArtifactActionContext<M>) => boolean;
};

export type ArtifactToolbarContext = {
	sendMessage: UseChatHelpers<ChatMessage>["sendMessage"];
};

export type ArtifactToolbarItem = {
	description: string;
	icon: ReactNode;
	onClick: (context: ArtifactToolbarContext) => void;
};

// biome-ignore lint/suspicious/noExplicitAny: Ignore for now
interface ArtifactContent<M = any> {
	title: string;
	content: string;
	mode: "edit" | "diff";
	isCurrentVersion: boolean;
	currentVersionIndex: number;
	status: "streaming" | "idle";
	onSaveContent: (updatedContent: string, debounce: boolean) => void;
	isInline: boolean;
	getDocumentContentById: (index: number) => string;
	isLoading: boolean;
	metadata: M;
	setMetadata: Dispatch<SetStateAction<M>>;
}

// biome-ignore lint/suspicious/noExplicitAny: Ignore for now
interface InitializeParameters<M = any> {
	documentId: string;
	setMetadata: Dispatch<SetStateAction<M>>;
}

// biome-ignore lint/suspicious/noExplicitAny: Ignore for now
type ArtifactConfig<T extends string, M = any> = {
	kind: T;
	description: string;
	content: ComponentType<ArtifactContent<M>>;
	actions: Array<ArtifactAction<M>>;
	toolbar: ArtifactToolbarItem[];
	initialize?: (parameters: InitializeParameters<M>) => void;
	onStreamPart: (args: {
		setMetadata: Dispatch<SetStateAction<M>>;
		setArtifact: Dispatch<SetStateAction<UIArtifact>>;
		streamPart: DataUIPart<CustomUIDataTypes>;
	}) => void;
};

// biome-ignore lint/suspicious/noExplicitAny: Ignore for now
export class Artifact<T extends string, M = any> {
	readonly kind: T;
	readonly description: string;
	readonly content: ComponentType<ArtifactContent<M>>;
	readonly actions: Array<ArtifactAction<M>>;
	readonly toolbar: ArtifactToolbarItem[];
	readonly initialize?: (parameters: InitializeParameters) => void;
	readonly onStreamPart: (args: {
		setMetadata: Dispatch<SetStateAction<M>>;
		setArtifact: Dispatch<SetStateAction<UIArtifact>>;
		streamPart: DataUIPart<CustomUIDataTypes>;
	}) => void;

	constructor(config: ArtifactConfig<T, M>) {
		this.kind = config.kind;
		this.description = config.description;
		this.content = config.content;
		this.actions = config.actions || [];
		this.toolbar = config.toolbar || [];
		this.initialize = config.initialize || (async () => ({}));
		this.onStreamPart = config.onStreamPart;
	}
}
