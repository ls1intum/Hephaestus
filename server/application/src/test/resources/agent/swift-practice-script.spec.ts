import assert from "node:assert/strict";
import { mkdirSync, rmSync, writeFileSync } from "node:fs";
import { mkdir, mkdtemp } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";

import type { DiffFile } from "../../../../../../docker/agents/practice-scripts/lib/types.ts";
import { runBundled } from "../../../../../../docker/agents/practice-scripts/run-bundled.ts";

/**
 * A folder with an empty checkout at `repo/App`, and a run of the script on a pull request with the
 * checkout, the change and the record that a test gives.
 */
async function stage(slug: string) {
	const root = await mkdtemp(path.join(tmpdir(), "swift-practice-script-"));
	await mkdir(path.join(root, "repo/App"), { recursive: true });
	return {
		root,
		script: async (
			repo: string,
			change: ReadonlyMap<string, DiffFile>,
			work: Record<string, unknown>,
			contextDir?: string,
		) => runBundled(slug, { repo, change, work, contextDir }),
	};
}

const metadata = {
	pr_number: 1,
	pr_url: "https://example.org/team/project/pull/1",
	repository_full_name: "team/project",
	repository_id: 1,
	provider_id: 1,
	source_branch: "f",
	target_branch: "main",
	commit_sha: "abc123",
};

const view = `import SwiftUI

struct EventList: View {
    @State private var events: [Event] = []
    var body: some View {
        List(events) { Text($0.name) }
            .task {
                let (data, _) = try! await URLSession.shared.data(from: url)
                events = try! JSONDecoder().decode([Event].self, from: data)
            }
    }
}

final class EventStore {
    func load() async {
        let (data, _) = try await URLSession.shared.data(from: url)
    }
}
`;

/** The whole file as added lines, numbered as in the file. */
function whole(file: string, source: string): [string, DiffFile] {
	const lines = source.split("\n");
	return [
		file,
		{
			path: file,
			addedLines: new Map(lines.map((line, i) => [i + 1, line])),
			removedLines: new Map(),
			hunks: [],
		},
	];
}

void test("an I/O call is placed in the type that encloses it: the view's counts, the store's does not", async () => {
	const { root, script } = await stage("keeps-views-free-of-networking-and-persistence");
	try {
		writeFileSync(path.join(root, "repo/App/EventList.swift"), view);
		const result = await script(
			path.join(root, "repo"),
			new Map([whole("App/EventList.swift", view)]),
			metadata,
		);
		assert.deepEqual(
			result.leads.map((lead) => [lead.at, lead.kind, lead.facts?.enclosing]),
			[
				[{ change: "App/EventList.swift", line: 8 }, "urlsession-request", "struct EventList"],
				[{ change: "App/EventList.swift", line: 9 }, "json-coding", "struct EventList"],
			],
		);
		assert.equal(result.facts?.ioCallsInViews, 2);
		assert.equal(result.facts.filesWithoutCheckout, 0);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("without a checkout the placing is unknown, and the lead is still reported", async () => {
	const { root, script } = await stage("keeps-views-free-of-networking-and-persistence");
	try {
		const result = await script(
			path.join(root, "repo"),
			new Map([whole("App/EventList.swift", view)]),
			metadata,
		);
		assert.equal(result.facts?.filesWithoutCheckout, 1);
		assert.ok(result.leads.every((lead) => lead.facts?.enclosing === "unknown"));
		// The store's call is a candidate too when nothing says which type it lies in.
		assert.equal(result.leads.length, 3);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("a new view without a preview is counted per file, a preview elsewhere in the change is a lead", async () => {
	const { root, script } = await stage("ships-a-preview-with-each-new-view");
	try {
		const result = await script(
			path.join(root, "repo"),
			new Map([
				whole("App/EventList.swift", view),
				whole(
					"App/EventRow.swift",
					'struct EventRow: View {\n    var body: some View { Text("") }\n}\n\n#Preview {\n    EventRow()\n}\n',
				),
			]),
			metadata,
		);
		assert.deepEqual(result.facts, {
			newViews: 2,
			previewsAdded: 1,
			filesWithNewViewAndNoPreview: 1,
		});
		assert.deepEqual(
			result.leads.map((lead) => [lead.at, lead.kind]),
			[
				[{ change: "App/EventList.swift", line: 3 }, "new-view-type"],
				[{ change: "App/EventRow.swift", line: 1 }, "new-view-type"],
				[{ change: "App/EventRow.swift", line: 5 }, "preview"],
			],
		);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("an API use is shown with the usage keys located for it and where, never a verdict on the target", async () => {
	const { root, script } = await stage("declares-permissions-truthfully-at-point-of-use");
	try {
		writeFileSync(
			path.join(root, "repo/project.yml"),
			"targets:\n  App:\n    info:\n      properties:\n        NSCameraUsageDescription: Scans the ticket's QR code\n",
		);
		const source =
			"import CoreLocation\nimport AVFoundation\nlet manager = CLLocationManager()\nmanager.requestWhenInUseAuthorization()\nlet session = AVCaptureSession()\n";
		const result = await script(
			path.join(root, "repo"),
			new Map([whole("App/Scanner.swift", source)]),
			metadata,
		);
		assert.equal(result.facts?.permissionReferenceKinds, 2);
		assert.equal(result.facts.authorizationRequests, 1);
		const said = (result.directions ?? []).join("\n");
		assert.match(said, /Location — keys located: none/u);
		assert.match(said, /Camera — keys located: NSCameraUsageDescription \(project\.yml\)/u);
		assert.match(said, /does not show the target declares it/u);
		assert.doesNotMatch(said, /No usage key found/u);
		assert.match(said, /A root view can be the feature that needs authorization/u);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("a system picker, an import or an image picker on the library is not permission-bearing access", async () => {
	const { root, script } = await stage("declares-permissions-truthfully-at-point-of-use");
	try {
		const source = [
			"import CoreLocation",
			"import PhotosUI",
			"let picker = PHPickerViewController(configuration: PHPickerConfiguration())",
			"let library = UIImagePickerController()",
			"library.sourceType = .photoLibrary",
			"let place = CLLocationCoordinate2D(latitude: 48.1, longitude: 11.6)",
		].join("\n");
		const result = await script(
			path.join(root, "repo"),
			new Map([whole("App/Picker.swift", source)]),
			metadata,
		);
		assert.equal(result.facts?.permissionReferenceKinds, 0);
		assert.deepEqual(result.leads, []);
		assert.doesNotMatch((result.directions ?? []).join("\n"), /Permission-related|authorization/u);
		// Choosing the camera as the picker's source is the access it is.
		const camera = await script(
			path.join(root, "repo"),
			new Map([whole("App/Picker.swift", `${source}\nlibrary.sourceType = .camera\n`)]),
			metadata,
		);
		assert.equal(camera.facts?.permissionReferenceKinds, 1);
		assert.match((camera.directions ?? []).join("\n"), /Camera — keys located: none/u);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("a location type named in a delegate signature is a reference to inspect, and only a request call is a request", async () => {
	const { root, script } = await stage("declares-permissions-truthfully-at-point-of-use");
	try {
		const delegateOnly = [
			"import CoreLocation",
			"final class Preview: NSObject, CLLocationManagerDelegate {",
			"    func locationManager(_ manager: CLLocationManager, didFailWithError error: any Error) {}",
			"}",
		].join("\n");
		const typeOnly = await script(
			path.join(root, "repo"),
			new Map([whole("App/Preview.swift", delegateOnly)]),
			metadata,
		);
		assert.deepEqual(
			typeOnly.leads.map((lead) => [lead.at, lead.kind]),
			[[{ change: "App/Preview.swift", line: 3 }, "permission-reference-location"]],
		);
		assert.equal(typeOnly.facts?.permissionReferenceKinds, 1);
		assert.equal(typeOnly.facts.authorizationRequests, 0);

		const requesting = [
			"import CoreLocation",
			"final class Preview: NSObject, CLLocationManagerDelegate {",
			"    let manager = CLLocationManager()",
			"    func begin() { manager.requestWhenInUseAuthorization() }",
			"}",
		].join("\n");
		const request = await script(
			path.join(root, "repo"),
			new Map([whole("App/Preview.swift", requesting)]),
			metadata,
		);
		assert.deepEqual(
			request.leads.map((lead) => [lead.at, lead.kind]),
			[
				[{ change: "App/Preview.swift", line: 3 }, "permission-reference-location"],
				[{ change: "App/Preview.swift", line: 4 }, "authorization-request"],
			],
		);
		assert.equal(request.facts?.permissionReferenceKinds, 1);
		assert.equal(request.facts.authorizationRequests, 1);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("a custom font given a size scales, one given a fixed size does not, and a system point size is read", async () => {
	const { root, script } = await stage("makes-ui-accessible-by-default");
	try {
		const source = [
			"struct Course: View {",
			"    var body: some View {",
			'        Text("A").font(.custom("CourseFont", size: 18))',
			'        Text("B").font(.custom("CourseFont", fixedSize: 18))',
			'        Text("C").font(.system(size: 14))',
			"    }",
			"}",
		].join("\n");
		const result = await script(
			path.join(root, "repo"),
			new Map([whole("App/Course.swift", source)]),
			metadata,
		);
		assert.deepEqual(
			result.leads.map((lead) => [lead.at.line, lead.kind]),
			[
				[3, "scaled-custom-font"],
				[4, "fixed-size-custom-font"],
				[5, "system-font-with-a-point-size"],
			],
		);
		const said = result.directions?.join("\n") ?? "";
		assert.match(said, /^1 custom font\(s\) with fixedSize: added/mu);
		assert.match(said, /^1 system font\(s\) with a point size added/mu);
		assert.doesNotMatch(said, /scaled only with relativeTo/u);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("rows beyond the cap are counted by kind and said to be unlisted", async () => {
	const { root, script } = await stage("makes-ui-accessible-by-default");
	try {
		const icons = Array.from({ length: 65 }, () => '        Image(systemName: "star")');
		const source = [
			"struct Stars: View {",
			"    var body: some View {",
			...icons,
			"    }",
			"}",
		].join("\n");
		const result = await script(
			path.join(root, "repo"),
			new Map([whole("App/Stars.swift", source)]),
			metadata,
		);
		assert.equal(result.leads.length, 60);
		assert.equal(result.facts?.symbolImages, 65);
		assert.equal(result.facts.interfaceLinesAdded, 65);
		assert.match(
			result.directions?.[0] ?? "",
			/^65 matching added line\(s\); the first 60 are listed and 5 are not\./u,
		);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("detached work needs no written reason, and default isolation text is a lead per target, never the project's", async () => {
	const { root, script } = await stage("uses-structured-concurrency-safely");
	try {
		const source = [
			"struct Feed: View {",
			"    var body: some View {",
			'        Button("Load") { Task { await model.load() } }',
			"        Text(model.title).onAppear { Task.detached { await model.prefetch() } }",
			"    }",
			"}",
		].join("\n");
		writeFileSync(path.join(root, "repo/App/Feed.swift"), source);
		const diff = new Map([whole("App/Feed.swift", source)]);
		const plain = await script(path.join(root, "repo"), diff, metadata);
		const said = (plain.directions ?? []).join("\n");
		assert.match(said, /no written reason is required/u);
		assert.doesNotMatch(said, /stated reason|whether \.task was available/u);
		assert.match(said, /missing @MainActor on an added line does not by itself/u);
		assert.match(said, /leaves the module's default unknown rather than nonisolated/u);
		assert.match(said, /Check the SDK before relying on inherited SwiftUI View isolation/u);
		assert.match(said, /Swallowed failures belong to handles-errors-instead-of-swallowing-them/u);
		// Two targets that disagree, and a SwiftPM setting that is commented out.
		writeFileSync(
			path.join(root, "repo/project.yml"),
			[
				"targets:",
				"  App:",
				"    settings:",
				"      SWIFT_DEFAULT_ACTOR_ISOLATION: MainActor",
				"  Widget:",
				"    settings:",
				"      SWIFT_DEFAULT_ACTOR_ISOLATION: nonisolated",
			].join("\n"),
		);
		writeFileSync(
			path.join(root, "repo/Package.swift"),
			'// .target(name: "Core", swiftSettings: [.defaultIsolation(MainActor.self)])\n',
		);
		const withSettings = await script(path.join(root, "repo"), diff, metadata);
		assert.deepEqual(
			withSettings.leads
				.filter((lead) => lead.kind === "default-isolation-setting")
				.map((lead) => lead.at),
			[
				{ file: "project.yml", line: 4 },
				{ file: "project.yml", line: 7 },
				{ file: "Package.swift", line: 1 },
			],
		);
		const directions = (withSettings.directions ?? []).join("\n");
		assert.match(
			directions,
			/applies only to its own target or SwiftPM module and build configuration/u,
		);
		assert.doesNotMatch(directions, /sets MainActor as the default|is on the main actor unless/u);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("each state declaration is a lead with its enclosing type, and a plain var in a view is a place to read, not a lapse", async () => {
	const { root, script } = await stage("owns-state-at-the-right-level");
	try {
		const source = [
			"struct Parent: View {",
			"    @State private var store = Store()",
			"    @State private var count = 0",
			"    var title: String",
			"    var body: some View {",
			"        Child(count: $count).environment(store)",
			"    }",
			"}",
			"",
			"struct Child: View {",
			"    @Binding var count: Int",
			'    var body: some View { Text("") }',
			"}",
		].join("\n");
		writeFileSync(path.join(root, "repo/App/Parent.swift"), source);
		const result = await script(
			path.join(root, "repo"),
			new Map([whole("App/Parent.swift", source)]),
			metadata,
		);
		assert.deepEqual(
			result.leads.map((lead) => [lead.at.line, lead.kind, lead.facts?.enclosing]),
			[
				[2, "state-creating-an-object", "struct Parent"],
				[3, "state-property", "struct Parent"],
				[4, "mutable-stored-property", "struct Parent"],
				[6, "environment-injection", "struct Parent"],
				[11, "binding", "struct Child"],
			],
		);
		assert.equal(result.facts?.objectsCreatedInViews, 1);
		assert.equal(result.facts.storedVarsInViews, 1);
		const said = (result.directions ?? []).join("\n");
		assert.match(said, /Read whether the view mutates each one/u);
		assert.doesNotMatch(said, /lapse|rule-1/u);

		const none = await script(
			path.join(root, "repo"),
			new Map([whole("App/Plain.swift", "let answer = 42")]),
			metadata,
		);
		assert.deepEqual(none.leads, []);
		assert.match(none.directions?.[0] ?? "", /^Scanned 1 added lines in 1 files .*none matched\./u);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("a change with no reference anywhere says what it scanned and what the inventory holds", async () => {
	const { root, script } = await stage("links-the-change-to-its-issue");
	try {
		mkdirSync(path.join(root, "context"));
		// Each row carries the ids of its repository; the reviewed one is the admitted repository id.
		writeFileSync(
			path.join(root, "context/project_inventory.json"),
			JSON.stringify({
				issues: [
					{
						repository: "team/project",
						repository_id: 1,
						provider_id: 1,
						number: 4,
						title: "Add the event map",
						state: "opened",
					},
				],
			}),
		);
		const result = await script(
			path.join(root, "repo"),
			new Map(),
			{
				...metadata,
				title: "Add the map",
				body: "Shows the events on a map.",
				source_branch: "map",
			},
			path.join(root, "context"),
		);
		assert.equal(result.facts?.referencesFound, 0);
		assert.equal(result.facts.inventoryOpenIssues, 1);
		assert.equal(result.facts.linkedWorkItems, -1);
		const referenced = await script(
			path.join(root, "repo"),
			new Map(),
			{
				...metadata,
				title: "Add the map",
				// The template's commented example is not the author's reference.
				body: "<!-- Example: Closes #12 -->\nCloses #4",
				source_branch: "4-add-the-map",
			},
			path.join(root, "context"),
		);
		assert.equal(referenced.facts?.referencesFound, 1);
		assert.equal(referenced.facts.closingReferences, 1);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("native platform logging is a logger and standard output a print, a print under a scripts path is marked as tool output", async () => {
	const { root, script } = await stage("logs-through-the-platform-logger");
	try {
		writeFileSync(
			path.join(root, "repo/App/Log.swift"),
			'import OSLog\nlet logger = Logger(subsystem: "app", category: "app")\n',
		);
		const result = await script(
			path.join(root, "repo"),
			new Map([
				whole(
					"App/Store.swift",
					'final class Store {\n    func load() {\n        print("loading")\n        logger.info("loaded")\n        NSLog("loaded %d", count)\n    }\n}\n',
				),
				whole(
					"App/Sync.m",
					'- (void)sync {\n    NSLog(@"synced %@", name);\n    os_log_error(OS_LOG_DEFAULT, "sync failed");\n    printf("synced");\n}\n',
				),
				whole(
					"app/src/main/java/com/example/Feed.kt",
					'class Feed {\n    fun load() {\n        Log.d("Feed", "loaded")\n        println("loaded")\n    }\n}\n',
				),
				whole(
					"app/src/main/java/com/example/Cache.java",
					'class Cache {\n    void evict() {\n        Log.w(TAG, "evicted");\n        System.out.println("evicted");\n    }\n}\n',
				),
				whole("scripts/report.py", 'print("done")\n'),
			]),
			metadata,
		);
		assert.deepEqual(result.facts, {
			printsAdded: 5,
			loggerCallsAdded: 6,
			printsInToolPaths: 1,
			checkoutHasLogger: 1,
			filesScanned: 5,
			linesAdded: 30,
		});
		assert.deepEqual(
			result.leads.map((lead) => [
				lead.kind,
				lead.facts?.language,
				lead.facts?.call,
				lead.facts?.toolPath,
			]),
			[
				["print-call", "swift", "print(", false],
				["logger-call", "swift", "Logger", false],
				["logger-call", "swift", "NSLog", false],
				["logger-call", "objective-c", "NSLog", false],
				["logger-call", "objective-c", "os_log", false],
				["print-call", "objective-c", "printf(", false],
				["logger-call", "kotlin", "android.util.Log", false],
				["print-call", "kotlin", "println(", false],
				["logger-call", "java", "android.util.Log", false],
				["print-call", "java", "System.out/err", false],
				["print-call", "python", "print(", true],
			],
		);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("a print in another language beyond the shared row cap is counted but not reported as a listed row", async () => {
	const { root, script } = await stage("logs-through-the-platform-logger");
	try {
		const prints = Array.from({ length: 40 }, (_, i) => `        print("step ${String(i)}")`);
		const swift = ["final class Store {", "    func load() {", ...prints, "    }", "}"].join("\n");
		const result = await script(
			path.join(root, "repo"),
			new Map([whole("App/Store.swift", swift), whole("scripts/report.py", 'print("done")\n')]),
			metadata,
		);
		assert.equal(result.leads.length, 40);
		assert.ok(
			result.leads.every((lead) => lead.kind === "print-call" && lead.facts?.language === "swift"),
		);
		assert.equal(result.facts?.printsAdded, 41);
		// The script's own print is past the 40 leads returned, so no listed lead is under a tool path.
		assert.equal(result.facts.printsInToolPaths, 0);
		assert.match(result.directions?.[0] ?? "", /^41 diagnostic line\(s\) added; 40 are listed\./u);
		assert.match(result.directions?.[1] ?? "", /\(0 listed under a tool or script path\)/u);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("a literal and a named asset color are told apart, and the asset's dark appearance is read from the catalog", async () => {
	const { root, script } = await stage("uses-adaptive-colors-for-every-appearance");
	try {
		mkdirSync(path.join(root, "repo/App/Assets.xcassets/Card.colorset"), { recursive: true });
		writeFileSync(
			path.join(root, "repo/App/Assets.xcassets/Card.colorset/Contents.json"),
			'{"colors":[{"idiom":"universal","color":{}},{"appearances":[{"appearance":"luminosity","value":"dark"}],"idiom":"universal","color":{}}]}',
		);
		const source =
			'struct Card: View {\n    var body: some View {\n        Text("x")\n            .foregroundStyle(.white)\n            .background(Color("Card"))\n            .padding()\n            .background(Color(red: 1, green: 1, blue: 1))\n    }\n}\n';
		writeFileSync(path.join(root, "repo/App/Card.swift"), source);
		const result = await script(
			path.join(root, "repo"),
			new Map([whole("App/Card.swift", source)]),
			metadata,
		);
		assert.equal(result.facts?.literalColors, 2);
		assert.match(
			(result.directions ?? []).join(" "),
			/A translucent scrim over an image is content/u,
		);
		assert.equal(result.facts.systemAdaptiveColors, 0);
		assert.equal(result.facts.namedAssetColors, 1);
		assert.equal(result.facts.assetsWithoutDarkAppearance, 0);
		assert.deepEqual(
			result.leads.map((lead) => [lead.at.line, lead.kind, lead.facts?.hasDarkAppearance ?? null]),
			[
				[4, "literal-white-or-black", null],
				[5, "named-asset", true],
				[7, "fixed-components", null],
			],
		);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("a colorset's appearances are read as JSON whatever the key order, and an unreadable one stays unknown", async () => {
	const { root, script } = await stage("uses-adaptive-colors-for-every-appearance");
	try {
		const colorsets: Record<string, string> = {
			// The dark appearance with its keys in the other order.
			Reordered:
				'{"colors":[{"color":{},"idiom":"universal"},{"color":{},"idiom":"universal","appearances":[{"value":"dark","appearance":"luminosity"}]}]}',
			Single: '{"colors":[{"color":{},"idiom":"universal"}],"info":{"version":1}}',
			Broken: '{"colors":[',
			NoColors: '{"info":{"version":1}}',
			Empty: '{"colors":[]}',
			GamutOnly:
				'{"colors":[{"idiom":"universal","display-gamut":"sRGB","color":{}},{"idiom":"universal","display-gamut":"display-P3","color":{}}]}',
			// Valid JSON whose inspected entries are not the shape a colorset has.
			NullEntry: '{"colors":[{"color":{},"idiom":"universal"},null]}',
			TextAppearances: '{"colors":[{"color":{},"idiom":"universal","appearances":"dark"}]}',
		};
		for (const [name, contents] of Object.entries(colorsets)) {
			mkdirSync(path.join(root, `repo/App/Assets.xcassets/${name}.colorset`), { recursive: true });
			writeFileSync(
				path.join(root, `repo/App/Assets.xcassets/${name}.colorset/Contents.json`),
				contents,
			);
		}
		const source = `struct Card: View {\n    var body: some View {\n${Object.keys(colorsets)
			.map((name) => `        Text("x").foregroundStyle(Color("${name}"))`)
			.join("\n")}\n    }\n}\n`;
		writeFileSync(path.join(root, "repo/App/Card.swift"), source);
		const result = await script(
			path.join(root, "repo"),
			new Map([whole("App/Card.swift", source)]),
			metadata,
		);
		assert.deepEqual(
			result.leads.map((lead) => [lead.facts?.asset, lead.facts?.hasDarkAppearance]),
			[
				["Reordered", true],
				["Single", false],
				["Broken", "unknown"],
				["NoColors", "unknown"],
				["Empty", "unknown"],
				["GamutOnly", false],
				["NullEntry", "unknown"],
				["TextAppearances", "unknown"],
			],
		);
		assert.equal(result.facts?.assetsWithoutDarkAppearance, 2);
		const said = (result.directions ?? []).join("\n");
		assert.match(said, /no dark luminosity entry/u);
		assert.doesNotMatch(said, /literal in disguise/u);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("a named color resolves only to one colorset, exact name first; two candidates stay unknown", async () => {
	const { root, script } = await stage("uses-adaptive-colors-for-every-appearance");
	try {
		const dark =
			'{"colors":[{"color":{},"idiom":"universal"},{"color":{},"idiom":"universal","appearances":[{"appearance":"luminosity","value":"dark"}]}]}';
		const colorsets: [string, string][] = [
			["App/Assets.xcassets/Brand.colorset", dark],
			["Widget/Assets.xcassets/BRAND.colorset", '{"colors":[{"color":{},"idiom":"universal"}]}'],
			["App/Assets.xcassets/Accent.colorset", dark],
			["Widget/Assets.xcassets/Accent.colorset", dark],
		];
		for (const [folder, contents] of colorsets) {
			mkdirSync(path.join(root, "repo", folder), { recursive: true });
			writeFileSync(path.join(root, "repo", folder, "Contents.json"), contents);
		}
		const source =
			'struct Card: View {\n    var body: some View {\n        Text("x").foregroundStyle(Color("Brand"))\n        Text("y").foregroundStyle(Color("brand"))\n        Text("z").foregroundStyle(Color("Accent"))\n    }\n}\n';
		writeFileSync(path.join(root, "repo/App/Card.swift"), source);
		const result = await script(
			path.join(root, "repo"),
			new Map([whole("App/Card.swift", source)]),
			metadata,
		);
		assert.deepEqual(
			result.leads.map((lead) => [lead.facts?.asset, lead.facts?.hasDarkAppearance]),
			[
				["Brand", true],
				["brand", "unknown"],
				// The same exact name in two catalogs: which target's the view uses is not here.
				["Accent", "unknown"],
			],
		);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("a named color given a bundle is a named asset; the main bundle resolves, another bundle stays unknown", async () => {
	const { root, script } = await stage("uses-adaptive-colors-for-every-appearance");
	try {
		mkdirSync(path.join(root, "repo/App/Assets.xcassets/Brand.colorset"), { recursive: true });
		writeFileSync(
			path.join(root, "repo/App/Assets.xcassets/Brand.colorset/Contents.json"),
			'{"colors":[{"color":{},"idiom":"universal"},{"color":{},"idiom":"universal","appearances":[{"appearance":"luminosity","value":"dark"}]}]}',
		);
		const source = [
			"struct Card: View {",
			"    var body: some View {",
			'        Text("a").foregroundStyle(Color("Brand", bundle: .main))',
			'        Text("b").foregroundStyle(Color("Brand", bundle: nil))',
			// A package's own catalog: the checkout's Brand.colorset may not be the one it holds.
			'        Text("c").foregroundStyle(Color("Brand", bundle: .module))',
			// The same argument shape names an image, not a color.
			'        Image("Brand", bundle: .module)',
			"    }",
			"}",
			"",
		].join("\n");
		writeFileSync(path.join(root, "repo/App/Card.swift"), source);
		const result = await script(
			path.join(root, "repo"),
			new Map([whole("App/Card.swift", source)]),
			metadata,
		);
		assert.deepEqual(
			result.leads.map((lead) => [
				lead.at.line,
				lead.kind,
				lead.facts?.bundle ?? null,
				lead.facts?.hasDarkAppearance,
			]),
			[
				[3, "named-asset", null, true],
				[4, "named-asset", null, true],
				[5, "named-asset", ".module", "unknown"],
			],
		);
		assert.equal(result.facts?.namedAssetColors, 3);
		assert.ok((result.directions ?? []).some((d) => /bundle other than the main one/u.test(d)));
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("no gray is literal or adaptive by its spelling: each is left to the review to classify", async () => {
	const { root, script } = await stage("uses-adaptive-colors-for-every-appearance");
	try {
		const source =
			'struct Card: View {\n    var body: some View {\n        Text("x").foregroundStyle(.gray)\n        Text("x").background(Color(.systemGray2))\n        Text("x").background(Color(uiColor: .systemGray6))\n        Text("Start").padding().background(Color.gray)\n    }\n}\n';
		writeFileSync(path.join(root, "repo/App/Card.swift"), source);
		const result = await script(
			path.join(root, "repo"),
			new Map([whole("App/Card.swift", source)]),
			metadata,
		);
		assert.equal(result.facts?.literalColors, 0);
		assert.equal(result.facts.systemAdaptiveColors, 0);
		assert.equal(result.facts.colorsToClassify, 4);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("only spellings that fix the kind are counted literal or adaptive; standard, UIKit, converted and app colors are left to classify", async () => {
	const { root, script } = await stage("uses-adaptive-colors-for-every-appearance");
	try {
		const source = `struct Card: View {
    var body: some View {
        Image(systemName: "house.fill").foregroundStyle(.pink)
        Text("Plant helper").background(Color.green, in: RoundedRectangle(cornerRadius: 8))
        Text("Card").background(SwiftUI.Color.purple.opacity(0.10))
        Text("Brand").background(Color.brandGreen)
        Text("Tile").background(Color(uiColor: .systemGreen))
        Text("Fixed").background(Color(uiColor: .green))
        Text("Fixed").background(Color(UIColor.green))
        Text("Converted").background(Color(.red))
        Text("Tinted").tint(.pink)
        Text("Beige").background(Color(red: 0.96, green: 0.94, blue: 0.90))
        Text("Paper").background(Color(white: 0.95))
        Text("Ink").foregroundStyle(.black)
        Text("Quiet").foregroundStyle(.secondary)
        Text("Glass").background(.regularMaterial)
        Text("Accent").foregroundStyle(.tint)
    }
}
`;
		writeFileSync(path.join(root, "repo/App/Card.swift"), source);
		const result = await script(
			path.join(root, "repo"),
			new Map([whole("App/Card.swift", source)]),
			metadata,
		);
		assert.deepEqual(
			result.leads.map((lead) => [lead.at.line, lead.kind]),
			[
				[3, "color-modifier-member"],
				[4, "color-member"],
				[5, "color-member"],
				[6, "color-member"],
				[7, "uikit-color"],
				[8, "uikit-color"],
				[9, "uikit-color"],
				[10, "color-from-a-value"],
				[11, "color-modifier-member"],
				[12, "fixed-components"],
				[13, "fixed-components"],
				[14, "literal-white-or-black"],
				[15, "semantic-style"],
				[16, "material"],
				[17, "accent"],
			],
		);
		assert.equal(result.facts?.literalColors, 3);
		assert.equal(result.facts.systemAdaptiveColors, 3);
		assert.equal(result.facts.colorsToClassify, 9);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("framework storage is not direct view I/O", async () => {
	const { root, script } = await stage("keeps-views-free-of-networking-and-persistence");
	try {
		const source = `import SwiftUI
import CoreData
struct Settings: View {
    @AppStorage("compact") private var compact = false
    @SceneStorage("selection") private var selection = ""
    @FetchRequest(sortDescriptors: []) private var items: FetchedResults<Item>
    var body: some View { Text(selection) }
}
`;
		writeFileSync(path.join(root, "repo/App/Settings.swift"), source);
		const result = await script(
			path.join(root, "repo"),
			new Map([whole("App/Settings.swift", source)]),
			metadata,
		);
		assert.equal(result.facts?.ioCallsInViews, 0);
		assert.deepEqual(result.leads, []);
		assert.match(result.directions?.join("\n") ?? "", /^Scanned 9 added lines in 1 files/mu);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});
