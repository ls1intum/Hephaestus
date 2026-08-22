/**
 * A random identifier, falling back to a timestamp where `crypto.randomUUID` is missing.
 *
 * `randomUUID` is exposed only in secure contexts, so the unconditional declaration in the DOM
 * typings overstates it — over plain `http://` on anything but localhost the method is genuinely
 * absent, and calling it throws. The fallback is not unique across clients; use this only to
 * correlate events that already carry other identifying context.
 */
export function randomId(): string {
	const cryptoApi: { randomUUID?: () => string } = globalThis.crypto;
	return cryptoApi.randomUUID?.() ?? `${Date.now()}`;
}
