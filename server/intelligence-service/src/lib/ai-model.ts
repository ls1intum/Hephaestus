import { azure } from "@ai-sdk/azure";
import { openai } from "@ai-sdk/openai";

export const getModel = (providerAndModel: string) => {
	const [provider, model] = providerAndModel.split(":");
	if (!provider || !model) {
		throw new Error(
			`Invalid model format: ${providerAndModel}. Expected format: <provider>:<model>`,
		);
	}

	switch (provider) {
		case "openai":
			return openai(model);
		case "azure":
			return azure(model);
		default:
			throw new Error(`Unsupported provider: ${provider}`);
	}
};
