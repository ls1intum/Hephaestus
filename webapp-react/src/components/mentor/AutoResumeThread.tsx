import { useEffect } from "react";
import { useRecoilState } from "recoil";
import { toast } from "sonner";

import {
	resumeThreadErrorState,
	useChatInteract,
	useChatSession,
	useConfig,
} from "@chainlit/react-client";
import { useNavigate } from "@tanstack/react-router";

interface Props {
	id: string;
}

export default function AutoResumeThread({ id }: Props) {
	const navigate = useNavigate();
	const { config } = useConfig();
	const { clear, setIdToResume } = useChatInteract();
	const { session, idToResume } = useChatSession();
	const [resumeThreadError, setResumeThreadError] = useRecoilState(
		resumeThreadErrorState,
	);

	useEffect(() => {
		if (!config?.threadResumable) return;
		clear();
		setIdToResume(id);
		if (!config?.dataPersistence) {
			navigate({ to: "/mentor", replace: true });
		}
	}, [
		config?.threadResumable,
		config?.dataPersistence,
		id,
		clear,
		setIdToResume,
		navigate,
	]);

	useEffect(() => {
		if (id !== idToResume) {
			return;
		}
		if (session?.error) {
			toast.error("Couldn't resume chat");
			navigate({ to: "/", replace: true });
		}
	}, [session, idToResume, id, navigate]);

	useEffect(() => {
		if (resumeThreadError) {
			toast.error(`Couldn't resume chat: ${resumeThreadError}`);
			navigate({ to: "/", replace: true });
			setResumeThreadError(undefined);
		}
	}, [resumeThreadError, navigate, setResumeThreadError]);

	return null;
}
