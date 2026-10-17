/** Model restrictions also apply when a gateway exposes the model through an OpenAI protocol. */
export function supportsForcedToolChoice(modelId: string | undefined): boolean {
	const version = /claude[-_.](?:opus|sonnet)[-_.](?<major>\d+)[-_.](?<minor>\d+)/iu.exec(
		modelId ?? "",
	);
	if (version === null) {
		return true;
	}
	const major = Number(version.groups?.major);
	const minor = Number(version.groups?.minor);
	return major < 5 || (major === 5 && minor < 5);
}
