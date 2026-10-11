import { QueryClient } from "@tanstack/react-query";
import { createMemoryHistory, createRouter } from "@tanstack/react-router";
import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http, type PathParams } from "msw";
import { describe, expect, it, vi } from "vitest";

import type { PublicActivity, PublicActivityOnboardingRequest } from "@/api/types.gen";
import type { Wire } from "@/lib/dates";
import { ROUTER_SEARCH } from "@/lib/router-search";
import { workspaceListItem } from "@/mocks/fixtures/workspaces";
import { unauthenticatedUser } from "@/mocks/handlers";
import { server } from "@/mocks/server";
import { routeTree } from "@/routeTree.gen";
import { workspaceRewrite } from "@/runtime/workspace-address";
import { deferred } from "@/test/async";
import { ObserverStub } from "@/test/observers";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";

// A case mounts the whole app chrome, whose route modules are imported lazily.
vi.setConfig({ testTimeout: 30_000 });

const counts = {
	contributions: 5,
	pullRequestsOpened: 2,
	pullRequestsMerged: 1,
	pullRequestsReviewed: 3,
	peopleHelped: 2,
	issuesOpened: 0,
	activeWeeks: 1,
};

function person(login: string, name: string, overrides: Partial<typeof counts> = {}) {
	return {
		login,
		name,
		avatarUrl: "",
		profileUrl: `https://github.com/${login}`,
		counts: { ...counts, ...overrides },
		weeks: [{ start: "2026-09-21T00:00:00Z", contributions: counts.contributions }],
	};
}

function publicActivity(overrides: Partial<Wire<PublicActivity>> = {}): Wire<PublicActivity> {
	return {
		workspaceName: "Acme",
		providerType: "GITHUB",
		allowSearchEngines: false,
		from: "2026-09-01T00:00:00Z",
		to: "2026-10-01T00:00:00Z",
		coverage: { completeRepositories: 1, totalRepositories: 1 },
		highlights: { firstContributors: [], mostPeopleHelped: [] },
		repositories: [
			{ key: "acme/api", name: "api" },
			{ key: "acme/web", name: "web" },
		],
		people: [
			person("ada", "Ada Lovelace"),
			person("bob", "Bob Brenner", { contributions: 8, pullRequestsReviewed: 9 }),
		],
		...overrides,
	};
}

function publish(page: Wire<PublicActivity> = publicActivity(), reads: URL[] = []) {
	server.use(
		http.get("*/public/workspaces/:slug/activity", ({ request }) => {
			reads.push(new URL(request.url));
			return HttpResponse.json(page);
		}),
	);
}

/** The people table's names in the order it shows them. */
function names(): string[] {
	return within(screen.getByRole("table", { name: "People" }))
		.getAllByRole("link")
		.map((link) => link.textContent);
}

describe("the address of a workspace", () => {
	it("shows a signed-out visitor the public activity page of a workspace that publishes", async () => {
		server.use(unauthenticatedUser);
		publish();
		const { router } = renderRouteAtWithRouter("/w/acme");

		await screen.findByRole("heading", { name: "Acme activity" }, ROUTE_RENDER_WAIT);
		expect(names()).toStrictEqual(["Bob Brenner", "Ada Lovelace"]);
		within(screen.getByRole("main")).getByRole("button", { name: "Sign in" });
		expect(router.state.location.pathname).toBe("/w/acme");
	});

	// The server answers an unknown, a private and a switched-off workspace alike, so the page
	// has one way to learn that nothing is published: a not-found.
	it("sends a signed-out visitor of a workspace that publishes nothing to sign in, keeping the search", async () => {
		server.use(unauthenticatedUser);
		const { router } = renderRouteAtWithRouter("/w/private?range=1y");

		await waitFor(() => expect(router.state.location.pathname).toBe("/login"), ROUTE_RENDER_WAIT);
		expect(router.state.location.search.returnTo).toBe("/w/private?range=1y");
	});

	it("shows the page to a visitor when the identity cannot be read", async () => {
		server.use(http.get("*/user", () => new HttpResponse(null, { status: 503 })));
		publish();
		renderRouteAtWithRouter("/w/acme");

		await screen.findByRole("heading", { name: "Acme activity" }, ROUTE_RENDER_WAIT);
	});

	it("drops a repository the workspace no longer lists, rather than treating the page as private", async () => {
		server.use(unauthenticatedUser);
		publish();
		// Added last, so it answers first: the read with the repository fails once.
		server.use(
			http.get(
				"*/public/workspaces/:slug/activity",
				() => HttpResponse.json({ status: 404, title: "Not Found" }, { status: 404 }),
				{ once: true },
			),
		);
		const { router } = renderRouteAtWithRouter("/w/acme?repo=acme/gone&sort=reviews");

		await screen.findByRole("heading", { name: "Acme activity" }, ROUTE_RENDER_WAIT);
		expect(router.state.location.href).toBe("/w/acme?sort=reviews");
	});

	it("reads the period, repositories and order from the address, and writes them back readable", async () => {
		const user = userEvent.setup();
		const reads: URL[] = [];
		server.use(unauthenticatedUser);
		publish(publicActivity(), reads);
		const { router } = renderRouteAtWithRouter("/w/acme?range=1y&repo=acme/api&sort=name");

		await screen.findByRole("heading", { name: "Acme activity" }, ROUTE_RENDER_WAIT);
		expect(names()).toStrictEqual(["Ada Lovelace", "Bob Brenner"]);
		expect(reads.at(-1)?.searchParams.get("range")).toBe("1y");
		expect(reads.at(-1)?.searchParams.getAll("repo")).toStrictEqual(["acme/api"]);

		await user.click(screen.getByRole("button", { name: /contributions/iu }));
		await waitFor(() => expect(router.state.location.search.sort).toBeUndefined());
		await user.click(screen.getByRole("button", { name: "30 days" }));
		await waitFor(() => expect(router.state.location.search.range).toBe("30d"));
		expect(router.state.location.href).toBe("/w/acme?range=30d&repo=acme/api");
		await waitFor(() => expect(reads.at(-1)?.searchParams.get("range")).toBe("30d"));
	});

	it("offers a retry when the page could not be read", async () => {
		const user = userEvent.setup();
		server.use(unauthenticatedUser);
		publish();
		// Added last, so it answers first: the page fails once and then reads.
		server.use(
			http.get("*/public/workspaces/:slug/activity", () => HttpResponse.error(), { once: true }),
		);
		renderRouteAtWithRouter("/w/acme");

		await screen.findByText("We could not load people", undefined, ROUTE_RENDER_WAIT);
		screen.getByRole("heading", { name: "Public activity" });
		expect(screen.queryByRole("table", { name: "People" })).toBeNull();
		await user.click(screen.getByRole("button", { name: /Retry/u }));
		await screen.findByRole("heading", { name: "Acme activity" });
		expect(names()).toHaveLength(2);
	});

	// The router's own delays decide this: nothing for a read under a second, then the page's shape.
	it("shows the page's shape while a slow first read is out, then the page", async () => {
		const response = deferred<Response>();
		server.use(
			unauthenticatedUser,
			http.get("*/public/workspaces/:slug/activity", async () => response.promise),
		);
		renderRouteAtWithRouter("/w/acme");

		await screen.findByText("Loading public activity", undefined, ROUTE_RENDER_WAIT);
		expect(screen.queryByRole("heading", { name: "Acme activity" })).toBeNull();
		expect(screen.queryByRole("table", { name: "People" })).toBeNull();
		response.resolve(HttpResponse.json(publicActivity()));

		await screen.findByRole("heading", { name: "Acme activity" }, ROUTE_RENDER_WAIT);
		expect(screen.queryByText("Loading public activity")).toBeNull();
		expect(names()).toStrictEqual(["Bob Brenner", "Ada Lovelace"]);
	});

	it("shows a page that reads quickly without the loading shape in between", async () => {
		const loadingShown: boolean[] = [];
		const observer = new MutationObserver(() => {
			loadingShown.push(document.body.textContent.includes("Loading public activity"));
		});
		observer.observe(document.body, { childList: true, subtree: true });
		server.use(unauthenticatedUser);
		publish();
		renderRouteAtWithRouter("/w/acme");

		await screen.findByRole("heading", { name: "Acme activity" }, ROUTE_RENDER_WAIT);
		observer.disconnect();
		expect(loadingShown).not.toContain(true);
	});

	it("opens published person figures without another read, and returns to the same filters", async () => {
		const user = userEvent.setup();
		const reads: URL[] = [];
		server.use(unauthenticatedUser);
		publish(publicActivity(), reads);
		const address = "/w/acme?range=30d&repo=acme/api&sort=name";
		const { router } = renderRouteAtWithRouter(address);
		await screen.findByRole("heading", { name: "Acme activity" }, ROUTE_RENDER_WAIT);

		await user.click(screen.getByRole("link", { name: "Ada Lovelace" }));
		const panel = await screen.findByRole("dialog", { name: "Ada Lovelace" });
		await waitFor(() => expect(router.state.location.search.detail).toBe("person:ada"));
		const totals = within(panel).getByRole("heading", { name: "In this range" }).closest("section");
		expect(totals?.querySelector("dd")?.textContent).toBe("5");
		const weeks = within(panel).getByRole("table", { name: "Contributions by week" });
		expect(within(weeks).getByRole("cell", { name: "5" }).textContent).toBe("5");
		expect(
			within(panel)
				.getByRole("link", { name: /ada on GitHub/u })
				.getAttribute("href"),
		).toBe("https://github.com/ada");
		expect(reads).toHaveLength(1);

		await act(async () => router.history.back());
		await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
		expect(router.state.location.href).toBe(address);
		expect(reads).toHaveLength(1);
	});

	it("removes a person's figures and profile when the published response no longer includes them", async () => {
		let page = publicActivity();
		server.use(
			unauthenticatedUser,
			http.get("*/public/workspaces/:slug/activity", () => HttpResponse.json(page)),
		);
		const { router } = renderRouteAtWithRouter("/w/acme?detail=person%3Aada");
		const panel = await screen.findByRole("dialog", { name: "Ada Lovelace" }, ROUTE_RENDER_WAIT);
		within(panel).getByRole("link", { name: /ada on GitHub/u });
		server.use(
			http.get("*/public/workspaces/:slug/activity", () => HttpResponse.error(), { once: true }),
		);
		await act(async () => router.invalidate());
		within(screen.getByRole("dialog", { name: "Ada Lovelace" })).getByRole("link", {
			name: /ada on GitHub/u,
		});
		page = publicActivity({ people: [person("bob", "Bob Brenner")] });
		await act(async () => router.invalidate());
		await waitFor(() => {
			const current = screen.getByRole("dialog", { name: "ada" });
			within(current).getByText("No activity in this range");
			expect(within(current).queryByRole("link", { name: /on GitHub/u })).toBeNull();
			expect(within(current).queryByRole("table")).toBeNull();
		});
	});
});

/** The robots tags the page asks for once its route has loaded. */
async function robotsAt(url: string) {
	const router = createRouter({
		...ROUTER_SEARCH,
		routeTree,
		history: createMemoryHistory({ initialEntries: [url] }),
		context: { queryClient: new QueryClient(), auth: undefined },
	});
	await router.load();
	return router.state.matches
		.flatMap(({ meta }) => meta ?? [])
		.filter((tag) => tag?.name === "robots");
}

describe("search engines", () => {
	it("are asked to stay away unless the workspace allows them", async () => {
		server.use(unauthenticatedUser);
		publish();

		await expect(robotsAt("/w/acme")).resolves.toStrictEqual([
			{ name: "robots", content: "noindex" },
		]);
	});

	it("stay away from a page that did not load", async () => {
		server.use(unauthenticatedUser);
		server.use(http.get("*/public/workspaces/:slug/activity", () => HttpResponse.error()));

		await expect(robotsAt("/w/acme")).resolves.toStrictEqual([
			{ name: "robots", content: "noindex" },
		]);
	});

	it("may list the page when the workspace allows them", async () => {
		server.use(unauthenticatedUser);
		publish(publicActivity({ allowSearchEngines: true }));

		await expect(robotsAt("/w/acme")).resolves.toStrictEqual([]);
	});
});

describe("refreshing public activity", () => {
	it("keeps the active range through a failed refresh and disables Retry while it reloads", async () => {
		server.use(unauthenticatedUser);
		publish();
		const { router } = renderRouteAtWithRouter("/w/acme?sort=name");
		await screen.findByRole("heading", { name: "Acme activity" }, ROUTE_RENDER_WAIT);
		server.use(http.get("*/public/workspaces/:slug/activity", () => HttpResponse.error()));
		await act(async () => {
			await router.invalidate();
		});
		expect(names()).toStrictEqual(["Ada Lovelace", "Bob Brenner"]);
		screen.getByText("We could not refresh activity");

		const next = deferred<Wire<PublicActivity>>();
		server.use(
			http.get("*/public/workspaces/:slug/activity", async () =>
				HttpResponse.json(await next.promise),
			),
		);
		await userEvent.setup().click(screen.getByRole("button", { name: "Retry" }));
		await waitFor(() =>
			expect(screen.getByRole<HTMLButtonElement>("button", { name: "Retrying…" }).disabled).toBe(
				true,
			),
		);
		expect(names()).toStrictEqual(["Ada Lovelace", "Bob Brenner"]);
		await act(async () => {
			next.resolve(publicActivity({ people: [person("bob", "Bob Brenner")] }));
		});
		await waitFor(() => expect(screen.queryByText("We could not refresh activity")).toBeNull());
		expect(names()).toStrictEqual(["Bob Brenner"]);
	});

	it.each([429, 503])("retains the active figures on a retryable %s refresh", async (status) => {
		server.use(unauthenticatedUser);
		publish();
		const { router } = renderRouteAtWithRouter("/w/acme?sort=name");
		await screen.findByRole("heading", { name: "Acme activity" }, ROUTE_RENDER_WAIT);
		server.use(
			http.get("*/public/workspaces/:slug/activity", () =>
				HttpResponse.json({ status }, { status }),
			),
		);
		await act(async () => {
			await router.invalidate();
		});
		expect(names()).toStrictEqual(["Ada Lovelace", "Bob Brenner"]);
		screen.getByText("We could not refresh activity");
	});

	it.each([401, 403])(
		"does not restore pre-refusal figures after %s and a later network failure",
		async (status) => {
			server.use(unauthenticatedUser);
			publish();
			const { router } = renderRouteAtWithRouter("/w/acme");
			await screen.findByRole("heading", { name: "Acme activity" }, ROUTE_RENDER_WAIT);
			server.use(
				http.get("*/public/workspaces/:slug/activity", () =>
					HttpResponse.json({ status }, { status }),
				),
			);
			await act(async () => {
				await router.invalidate();
			});
			expect(screen.queryByRole("heading", { name: "Acme activity" })).toBeNull();
			server.use(http.get("*/public/workspaces/:slug/activity", () => HttpResponse.error()));
			await act(async () => {
				await router.invalidate();
			});
			expect(screen.queryByRole("heading", { name: "Acme activity" })).toBeNull();
			expect(screen.queryByText("Ada Lovelace")).toBeNull();
		},
	);

	it("redirects on publication removal instead of retaining its earlier figures", async () => {
		server.use(unauthenticatedUser);
		publish();
		const { router } = renderRouteAtWithRouter("/w/acme");
		await screen.findByRole("heading", { name: "Acme activity" }, ROUTE_RENDER_WAIT);
		server.use(
			http.get("*/public/workspaces/:slug/activity", () =>
				HttpResponse.json({ status: 404 }, { status: 404 }),
			),
		);
		await act(async () => {
			await router.invalidate();
		});
		await waitFor(() => expect(router.state.location.pathname).toBe("/login"));
		expect(screen.queryByText("Ada Lovelace")).toBeNull();
	});

	it.each([{ range: "30d" as const }, { repo: ["acme/api"] }])(
		"does not borrow a displayed range for another failed scope %j",
		async (search) => {
			server.use(unauthenticatedUser);
			publish();
			const { router } = renderRouteAtWithRouter("/w/acme");
			await screen.findByRole("heading", { name: "Acme activity" }, ROUTE_RENDER_WAIT);
			server.use(http.get("*/public/workspaces/:slug/activity", () => HttpResponse.error()));
			await act(async () => {
				await router.navigate({
					to: "/w/$workspaceSlug",
					params: { workspaceSlug: "acme" },
					search,
				});
			});
			expect(screen.queryByRole("heading", { name: "Acme activity" })).toBeNull();
			expect(screen.queryByText("Ada Lovelace")).toBeNull();
		},
	);

	it("does not reuse a cached workspace on a failed return from another workspace", async () => {
		server.use(unauthenticatedUser);
		publish();
		const { router } = renderRouteAtWithRouter("/w/acme");
		await screen.findByRole("heading", { name: "Acme activity" }, ROUTE_RENDER_WAIT);
		publish(
			publicActivity({ workspaceName: "Other", people: [person("other", "Other Developer")] }),
		);
		await act(async () => {
			await router.navigate({ to: "/w/$workspaceSlug", params: { workspaceSlug: "other" } });
		});
		await screen.findByRole("heading", { name: "Other activity" });
		server.use(http.get("*/public/workspaces/:slug/activity", () => HttpResponse.error()));
		await act(async () => {
			await router.navigate({ to: "/w/$workspaceSlug", params: { workspaceSlug: "acme" } });
		});
		expect(screen.queryByRole("heading", { name: "Acme activity" })).toBeNull();
		expect(screen.queryByText("Ada Lovelace")).toBeNull();
		expect(screen.queryByText("Other Developer")).toBeNull();
	});
});

describe("a signed-in person who is not a member", () => {
	/** The page, the person's own workspace, and the answers the first-visit step records. */
	function visit() {
		const answers: unknown[] = [];
		const requests: URL[] = [];
		let hidden = false;
		server.use(
			http.get("*/public/workspaces/:slug/activity", ({ request }) => {
				requests.push(new URL(request.url));
				return HttpResponse.json(
					publicActivity({
						people: hidden
							? [person("bob", "Bob Brenner")]
							: [person("ada", "Ada Lovelace"), person("bob", "Bob Brenner")],
					}),
				);
			}),
			http.get("*/workspaces", () => HttpResponse.json([workspaceListItem("other")])),
			http.get("*/workspaces/other/members/me", () =>
				HttpResponse.json({ role: "MEMBER", userId: 42, userLogin: "ada", userName: "Ada" }),
			),
			http.get("*/user/public-activity/workspaces/acme/onboarding", () =>
				HttpResponse.json({ seen: answers.length > 0, visible: !hidden }),
			),
			http.put<PathParams, PublicActivityOnboardingRequest>(
				"*/user/public-activity/workspaces/acme/onboarding",
				async ({ request }) => {
					const body = await request.json();
					answers.push(body);
					hidden = !body.visible;
					return HttpResponse.json({ seen: true, visible: body.visible });
				},
			),
		);
		return { answers, requests };
	}

	it("sees the page, and the step to show or hide themselves", async () => {
		const user = userEvent.setup();
		const { answers } = visit();
		const { router } = renderRouteAtWithRouter("/w/acme");

		const step = await screen.findByRole("alertdialog", {}, ROUTE_RENDER_WAIT);
		expect(router.state.location.pathname).toBe("/w/acme");
		// The step is modal, so the page behind it is out of the accessibility tree.
		const settings = within(screen.getByRole("main", { hidden: true })).getByRole("link", {
			name: "User settings",
			hidden: true,
		});
		expect(settings.getAttribute("href")).toBe("/settings");
		await user.click(within(step).getByRole("button", { name: "Hide me" }));

		await waitFor(() => expect(answers).toStrictEqual([{ visible: false }]));
		await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
	});

	it("no longer sees their own row once they hide, without reloading", async () => {
		const user = userEvent.setup();
		const { requests } = visit();
		renderRouteAtWithRouter("/w/acme");

		const step = await screen.findByRole("alertdialog", {}, ROUTE_RENDER_WAIT);
		expect(requests).toHaveLength(1);
		await user.click(within(step).getByRole("button", { name: "Hide me" }));

		await waitFor(() => expect(requests.length).toBeGreaterThan(1));
		await waitFor(() => expect(names()).toStrictEqual(["Bob Brenner"]));
	});

	it("can leave the step for this visit with Escape, and go on while a save never answers", async () => {
		const user = userEvent.setup();
		// Never answered: a save that settled later would read the page again, into the next case.
		const hangs = deferred<Response>();
		visit();
		server.use(
			http.put("*/user/public-activity/workspaces/acme/onboarding", async () => hangs.promise),
		);
		renderRouteAtWithRouter("/w/acme");

		const step = await screen.findByRole("alertdialog", {}, ROUTE_RENDER_WAIT);
		await user.click(within(step).getByRole("button", { name: "Hide me" }));
		await user.click(within(step).getByRole("button", { name: "Decide later" }));

		await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
	});

	it("has no active workspace here, so the chrome reads nothing the server would refuse", async () => {
		const { requests } = visit();
		const paths: string[] = [];
		server.events.on("request:start", ({ request }) => {
			paths.push(new URL(request.url).pathname);
		});
		renderRouteAtWithRouter("/w/acme");

		await screen.findByRole("alertdialog", {}, ROUTE_RENDER_WAIT);
		server.events.removeAllListeners();
		expect(requests).toHaveLength(1);
		// Membership, survey invitations and the Heph setup are members-only reads of a workspace.
		expect(paths.filter((path) => /^\/workspaces\/[^/]+\//u.test(path))).toStrictEqual([]);
	});

	it("sees the colours of the workspace's provider, not their own", async () => {
		visit();
		server.use(
			http.get("*/public/workspaces/:slug/activity", () =>
				HttpResponse.json(publicActivity({ providerType: "GITLAB" })),
			),
		);
		renderRouteAtWithRouter("/w/acme");

		await screen.findByRole("alertdialog", {}, ROUTE_RENDER_WAIT);
		await waitFor(() => expect(document.documentElement.dataset.provider).toBe("gitlab"));
		screen.getByRole("columnheader", { name: /Merge requests/u, hidden: true });
	});
});

describe("a signed-in person whose consent is pending", () => {
	it("gives it before the page", async () => {
		server.use(
			http.get("*/user/consent", () => HttpResponse.json({ completed: false })),
			http.get("*/workspaces", () => HttpResponse.json([])),
		);
		publish();
		const { router } = renderRouteAtWithRouter("/w/acme");

		await waitFor(() => expect(router.state.location.pathname).toBe("/consent"), ROUTE_RENDER_WAIT);
	});
});

describe("a member", () => {
	it("is asked once, on the first page of a workspace that publishes", async () => {
		const user = userEvent.setup();
		const answers: unknown[] = [];
		let seen = false;
		server.use(
			http.get("*/workspaces", () =>
				HttpResponse.json([workspaceListItem("acme", { publishesPublicActivity: true })]),
			),
			http.get("*/workspaces/:workspaceSlug/members/me", () =>
				HttpResponse.json({ role: "MEMBER", userId: 42, userLogin: "ada", userName: "Ada" }),
			),
			http.get("*/user/public-activity/workspaces/acme/onboarding", () =>
				HttpResponse.json({ seen, visible: true }),
			),
			http.put("*/user/public-activity/workspaces/acme/onboarding", async ({ request }) => {
				answers.push(await request.json());
				seen = true;
				return HttpResponse.json({ seen, visible: true });
			}),
		);
		vi.stubGlobal("IntersectionObserver", ObserverStub);
		renderRouteAtWithRouter("/w/acme");

		const step = await screen.findByRole("alertdialog", {}, ROUTE_RENDER_WAIT);
		await user.click(within(step).getByRole("button", { name: "Show me" }));

		await waitFor(() => expect(answers).toStrictEqual([{ visible: true }]));
		await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
	});

	it("is not asked where the workspace does not publish", async () => {
		const asked = vi.fn();
		server.use(
			http.get("*/workspaces", () => HttpResponse.json([workspaceListItem("acme")])),
			http.get("*/workspaces/:workspaceSlug/members/me", () =>
				HttpResponse.json({ role: "MEMBER", userId: 42, userLogin: "ada", userName: "Ada" }),
			),
			http.get("*/user/public-activity/workspaces/acme/onboarding", () => {
				asked();
				return HttpResponse.json({ seen: false, visible: true });
			}),
		);
		vi.stubGlobal("IntersectionObserver", ObserverStub);
		renderRouteAtWithRouter("/w/acme");

		await screen.findByRole("heading", { name: /Activity/u }, ROUTE_RENDER_WAIT);
		expect(asked).not.toHaveBeenCalled();
		expect(screen.queryByRole("alertdialog")).toBeNull();
	});
});

describe("tenant workspace root", () => {
	it("loads the same public activity page and query as the apex workspace path", async () => {
		server.use(unauthenticatedUser);
		const reads: URL[] = [];
		const page = publicActivity();
		publish(page, reads);
		const router = createRouter({
			...ROUTER_SEARCH,
			routeTree,
			origin: "https://acme.hephaestus.build",
			rewrite: workspaceRewrite(
				{ enabled: true, baseDomain: "hephaestus.build", apexOrigin: "https://hephaestus.build" },
				"https://acme.hephaestus.build",
			),
			history: createMemoryHistory({ initialEntries: ["/?range=30d"] }),
			context: { queryClient: new QueryClient(), auth: undefined },
		});
		await router.load();
		expect(router.state.location.pathname).toBe("/w/acme");
		expect(router.history.location.href).toBe("/?range=30d");
		expect(reads).toHaveLength(1);
		expect(reads[0]?.pathname).toBe("/public/workspaces/acme/activity");
		expect(router.state.matches.at(-1)?.loaderData).toMatchObject({
			status: "ready",
			page: { workspaceName: "Acme" },
		});
	});
});
