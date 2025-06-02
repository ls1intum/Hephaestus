import { cn } from "@/lib/utils";
import { useContext } from "react";

import { useTheme } from "@/integrations/theme";
import { ChainlitContext, useConfig } from "@chainlit/react-client";

interface Props {
	className?: string;
}

export const Logo = ({ className }: Props) => {
	const { theme } = useTheme();
	const { config } = useConfig();
	const apiClient = useContext(ChainlitContext);

	return (
		<img
			src={apiClient.getLogoEndpoint(theme, config?.ui?.logo_file_url)}
			alt="logo"
			className={cn("logo", className)}
		/>
	);
};
