import { type MutableRefObject, useCallback, useRef, useState } from "react";
import { useRecoilState, useSetRecoilState } from "recoil";
import { v4 as uuidv4 } from "uuid";

import {
	type FileSpec,
	type IStep,
	useAuth,
	useChatData,
	useChatInteract,
} from "@chainlit/react-client";

import { Settings } from "@/components/mentor/icons/Settings";
import { Button } from "@/components/ui/button";

import {
	type IAttachment,
	attachmentsState,
	persistentCommandState,
} from "@/state/chat";
import { chatSettingsOpenState } from "@/state/project";

import { Attachments } from "./Attachments";
import CommandButtons from "./CommandButtons";
import CommandButton from "./CommandPopoverButton";
import Input, { type InputMethods } from "./Input";
import McpButton from "./Mcp";
import SubmitButton from "./SubmitButton";
import UploadButton from "./UploadButton";
import VoiceButton from "./VoiceButton";

interface Props {
	fileSpec: FileSpec;
	onFileUpload: (payload: File[]) => void;
	onFileUploadError: (error: string) => void;
	autoScrollRef: MutableRefObject<boolean>;
}

export default function MessageComposer({
	fileSpec,
	onFileUpload,
	onFileUploadError,
	autoScrollRef,
}: Props) {
	const inputRef = useRef<InputMethods>(null);
	const [value, setValue] = useState("");
	const [selectedCommand, setSelectedCommand] = useRecoilState(
		persistentCommandState,
	);
	const setChatSettingsOpen = useSetRecoilState(chatSettingsOpenState);
	const [attachments, setAttachments] = useRecoilState(attachmentsState);

	const { user } = useAuth();
	const { sendMessage, replyMessage } = useChatInteract();
	const { askUser, chatSettingsInputs, disabled: _disabled } = useChatData();

	const disabled = _disabled || !!attachments.find((a) => !a.uploaded);

	const onPaste = useCallback(
		(event: ClipboardEvent) => {
			if (event.clipboardData?.items) {
				const items = Array.from(event.clipboardData.items);

				// If no text data, check for files (e.g., images)
				for (const item of items) {
					if (item.kind === "file") {
						const file = item.getAsFile();
						if (file) {
							onFileUpload([file]);
						}
					}
				}
			}
		},
		[onFileUpload],
	);

	const onSubmit = useCallback(
		async (
			msg: string,
			attachments?: IAttachment[],
			selectedCommand?: string,
		) => {
			const message: IStep = {
				threadId: "",
				command: selectedCommand,
				id: uuidv4(),
				name: user?.identifier || "User",
				type: "user_message",
				output: msg,
				createdAt: new Date().toISOString(),
				metadata: { location: window.location.href },
			};

			const fileReferences = attachments
				?.filter((a) => !!a.serverId)
				.map((a) => {
					// Only include attachments where serverId is definitely defined
					if (typeof a.serverId === "string") {
						return { id: a.serverId };
					}
					return null;
				})
				.filter((ref): ref is { id: string } => ref !== null);

			if (autoScrollRef) {
				autoScrollRef.current = true;
			}
			sendMessage(message, fileReferences);
		},
		[user, sendMessage, autoScrollRef],
	);

	const onReply = useCallback(
		async (msg: string) => {
			const message: IStep = {
				threadId: "",
				id: uuidv4(),
				name: user?.identifier || "User",
				type: "user_message",
				output: msg,
				createdAt: new Date().toISOString(),
				metadata: { location: window.location.href },
			};

			replyMessage(message);
			if (autoScrollRef) {
				autoScrollRef.current = true;
			}
		},
		[user, replyMessage, autoScrollRef],
	);

	// biome-ignore lint/correctness/useExhaustiveDependencies: This is a callback that does not need to be exhaustive
	const submit = useCallback(() => {
		if (disabled || (value === "" && attachments.length === 0)) {
			return;
		}
		if (askUser) {
			onReply(value);
		} else {
			onSubmit(value, attachments, selectedCommand?.id);
		}
		setAttachments([]);
		inputRef.current?.reset();
	}, [
		value,
		disabled,
		askUser,
		attachments,
		onReply,
		onSubmit,
		selectedCommand,
	]);

	return (
		<div
			id="message-composer"
			className="bg-accent dark:bg-card rounded-3xl p-3 px-4 w-full min-h-24 flex flex-col"
		>
			{attachments.length > 0 ? (
				<div className="mb-1">
					<Attachments />
				</div>
			) : null}
			<Input
				ref={inputRef}
				id="chat-input"
				autoFocus
				selectedCommand={selectedCommand}
				setSelectedCommand={setSelectedCommand}
				onChange={setValue}
				onPaste={onPaste}
				onEnter={submit}
				placeholder={"Type your message here..."}
			/>
			<div className="flex items-center justify-between">
				<div className="flex items-center -ml-1.5">
					<UploadButton
						disabled={disabled}
						fileSpec={fileSpec}
						onFileUploadError={onFileUploadError}
						onFileUpload={onFileUpload}
					/>
					<CommandButton
						disabled={disabled}
						onCommandSelect={setSelectedCommand}
					/>
					{chatSettingsInputs.length > 0 && (
						<Button
							id="chat-settings-open-modal"
							disabled={disabled}
							onClick={() => setChatSettingsOpen(true)}
							className="hover:bg-muted"
							variant="ghost"
							size="icon"
						>
							<Settings className="!size-6" />
						</Button>
					)}
					<McpButton disabled={disabled} />
					<VoiceButton disabled={disabled} />
					<CommandButtons
						disabled={disabled}
						selectedCommandId={selectedCommand?.id}
						onCommandSelect={setSelectedCommand}
					/>
				</div>
				<div className="flex items-center gap-1">
					<SubmitButton
						onSubmit={submit}
						disabled={disabled || !value.trim()}
					/>
				</div>
			</div>
		</div>
	);
}
