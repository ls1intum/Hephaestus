import { check } from "k6";
import crypto from "k6/crypto";
import http from "k6/http";

import { baseUrl, integer, required, sharedThresholds } from "./lib/config.js";

const rate = integer("WEBHOOK_RATE", 100);
const duration = __ENV.DURATION ?? "2m";
const preAllocatedVUs = integer("PRE_ALLOCATED_VUS", 40);
const event = "capacity_test";
const target = `${baseUrl()}/webhooks/github`;
const webhookSecret = required("WEBHOOK_SECRET");

export const options = {
	discardResponseBodies: true,
	scenarios: {
		webhook_burst: {
			executor: "constant-arrival-rate",
			rate,
			timeUnit: "1s",
			duration,
			preAllocatedVUs,
			maxVUs: integer("MAX_VUS", preAllocatedVUs * 2),
		},
	},
	thresholds: {
		...sharedThresholds,
		"checks{scenario:webhook_burst}": ["rate>0.99"],
		"http_req_duration{scenario:webhook_burst}": ["p(95)<250", "p(99)<750"],
		dropped_iterations: ["count==0"],
	},
};

const payload = JSON.stringify({
	repository: { name: "capacity-test", owner: { login: "hephaestus-load" } },
	padding: "x".repeat(integer("WEBHOOK_PADDING_BYTES", 4096)),
});

export default function webhookBurst() {
	const delivery = crypto.randomUUID();
	const signature = crypto.hmac("sha256", webhookSecret, payload, "hex");
	const response = http.post(target, payload, {
		headers: {
			"Content-Type": "application/json",
			"X-GitHub-Delivery": delivery,
			"X-GitHub-Event": event,
			"X-Hub-Signature-256": `sha256=${signature}`,
		},
		tags: { operation: "webhook_ingest" },
	});
	check(response, { "webhook accepted": (result) => result.status >= 200 && result.status < 300 });
}
