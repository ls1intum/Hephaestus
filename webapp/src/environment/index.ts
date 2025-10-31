export default {
	version: "DEV",
	clientUrl: "http://localhost:4200",
	serverUrl: "http://localhost:8080",
	sentry: {
		environment: "local",
		dsn: "https://289f1f62feeb4f70a8878dc0101825cd@sentry.ase.in.tum.de/3",
	},
	keycloak: {
		url: "http://localhost:8081",
		realm: "hephaestus",
		clientId: "hephaestus",
		skipLoginPage: true, // If true, it will directly use github IDP for login
	},
	posthog: {
		enabled: false,
		projectApiKey: "",
		apiHost: "",
	},
	legal: {
		imprintHtml: "<p>This is the imprint.</p>",
		privacyHtml: "<p>This is the privacy policy.</p>",
	},
};
