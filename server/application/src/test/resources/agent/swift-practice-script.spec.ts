import assert from "node:assert/strict";
import test from "node:test";

import type { DiffFile } from "../../../../../../docker/agents/practice-scripts/kits/scm/types.ts";
import {
	leadKeys,
	runBundled,
	runBundledProgram,
} from "../../../../../../docker/agents/practice-scripts/run-bundled.ts";

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
	const result = await runBundled("keeps-views-free-of-networking-and-persistence", {
		repo: { "App/EventList.swift": view },
		change: new Map([whole("App/EventList.swift", view)]),
	});
	const keyOf = await leadKeys("keeps-views-free-of-networking-and-persistence");
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [lead.at, keyOf(lead.label), lead.facts?.enclosing]),
		[
			[
				{ file: "App/EventList.swift", line: 8, endLine: 8 },
				"urlsession-request",
				"struct EventList",
			],
			[{ file: "App/EventList.swift", line: 9, endLine: 9 }, "json-coding", "struct EventList"],
		],
	);
	assert.equal(result.facts?.ioCallsInViews, 2);
	assert.equal(result.facts.filesWithUnknownLocation, 0);
});

void test("an unavailable pinned file withholds the scan input instead of claiming unknown item membership", async () => {
	const result = await runBundledProgram("keeps-views-free-of-networking-and-persistence", {
		repo: {},
		change: new Map([whole("App/EventList.swift", view)]),
	});
	assert.ok("error" in result.outcome);
});

void test("a new view without a preview is counted per file, a preview elsewhere in the change is a lead", async () => {
	const result = await runBundled("ships-a-preview-with-each-new-view", {
		change: new Map([
			whole("App/EventList.swift", view),
			whole(
				"App/EventRow.swift",
				'struct EventRow: View {\n    var body: some View { Text("") }\n}\n\n#Preview {\n    EventRow()\n}\n',
			),
		]),
	});
	assert.deepEqual(result.facts, {
		newViews: 2,
		previewsAdded: 1,
		filesWithNewViewAndNoPreview: 1,
	});
	const keyOf = await leadKeys("ships-a-preview-with-each-new-view");
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [lead.at, keyOf(lead.label)]),
		[
			[{ change: "App/EventList.swift", line: 3 }, "new-view-type"],
			[{ change: "App/EventRow.swift", line: 1 }, "new-view-type"],
			[{ change: "App/EventRow.swift", line: 5 }, "preview"],
		],
	);
});

void test("an API use is shown with the usage keys located for it and where, never a verdict on the target", async () => {
	const source =
		"import CoreLocation\nimport AVFoundation\nlet manager = CLLocationManager()\nmanager.requestWhenInUseAuthorization()\nlet session = AVCaptureSession()\n";
	const result = await runBundled("declares-permissions-truthfully-at-point-of-use", {
		repo: {
			"App/Scanner.swift": source,
			"project.yml":
				"targets:\n  App:\n    info:\n      properties:\n        NSCameraUsageDescription: Scans the ticket's QR code\n",
		},
		change: new Map([whole("App/Scanner.swift", source)]),
	});
	assert.equal(result.facts?.permissionReferenceKinds, 2);
	assert.equal(result.facts.authorizationRequests, 1);
	const said = (result.directions ?? []).join("\n");
	assert.match(said, /Location — keys located: none/u);
	assert.match(said, /Camera — keys located: NSCameraUsageDescription \(project\.yml\)/u);
	assert.match(said, /does not show the target declares it/u);
	assert.doesNotMatch(said, /No usage key found/u);
	assert.match(said, /A root view can be the feature that needs authorization/u);
});

void test("a system picker, an import or an image picker on the library is not permission-bearing access", async () => {
	const slug = "declares-permissions-truthfully-at-point-of-use";
	const source = [
		"import CoreLocation",
		"import PhotosUI",
		"let picker = PHPickerViewController(configuration: PHPickerConfiguration())",
		"let library = UIImagePickerController()",
		"library.sourceType = .photoLibrary",
		"let place = CLLocationCoordinate2D(latitude: 48.1, longitude: 11.6)",
	].join("\n");
	const result = await runBundled(slug, {
		change: new Map([whole("App/Picker.swift", source)]),
	});
	assert.equal(result.facts?.permissionReferenceKinds, 0);
	assert.deepEqual(result.leads ?? [], []);
	assert.doesNotMatch((result.directions ?? []).join("\n"), /Permission-related|authorization/u);
	const camera = await runBundled(slug, {
		change: new Map([whole("App/Picker.swift", `${source}\nlibrary.sourceType = .camera\n`)]),
	});
	assert.equal(camera.facts?.permissionReferenceKinds, 1);
	assert.match((camera.directions ?? []).join("\n"), /Camera — keys located: none/u);
});

void test("a location type named in a delegate signature is a reference to inspect, and only a request call is a request", async () => {
	const slug = "declares-permissions-truthfully-at-point-of-use";
	const keyOf = await leadKeys(slug);
	const delegateOnly = [
		"import CoreLocation",
		"final class Preview: NSObject, CLLocationManagerDelegate {",
		"    func locationManager(_ manager: CLLocationManager, didFailWithError error: any Error) {}",
		"}",
	].join("\n");
	const typeOnly = await runBundled(slug, {
		change: new Map([whole("App/Preview.swift", delegateOnly)]),
	});
	assert.deepEqual(
		(typeOnly.leads ?? []).map((lead) => [lead.at, keyOf(lead.label)]),
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
	const request = await runBundled(slug, {
		change: new Map([whole("App/Preview.swift", requesting)]),
	});
	assert.deepEqual(
		(request.leads ?? []).map((lead) => [lead.at, keyOf(lead.label)]),
		[
			[{ change: "App/Preview.swift", line: 3 }, "permission-reference-location"],
			[{ change: "App/Preview.swift", line: 4 }, "authorization-request"],
		],
	);
	assert.equal(request.facts?.permissionReferenceKinds, 1);
	assert.equal(request.facts.authorizationRequests, 1);
});

void test("a custom font given a size scales, one given a fixed size does not, and a system point size is read", async () => {
	const source = [
		"struct Course: View {",
		"    var body: some View {",
		'        Text("A").font(.custom("CourseFont", size: 18))',
		'        Text("B").font(.custom("CourseFont", fixedSize: 18))',
		'        Text("C").font(.system(size: 14))',
		"    }",
		"}",
	].join("\n");
	const result = await runBundled("makes-ui-accessible-by-default", {
		change: new Map([whole("App/Course.swift", source)]),
	});
	const keyOf = await leadKeys("makes-ui-accessible-by-default");
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [lead.at.line, keyOf(lead.label)]),
		[
			[3, "scaled-custom-font"],
			[4, "fixed-size-custom-font"],
			[5, "system-font-with-a-point-size"],
		],
	);
	const said = result.directions?.join("\n") ?? "";
	assert.match(said, /^1 custom font\(s\) with fixedSize: selected/mu);
	assert.match(said, /^1 system font\(s\) with a point size selected/mu);
	assert.doesNotMatch(said, /scaled only with relativeTo/u);
});

void test("rows beyond the cap are counted by kind and said to be unlisted", async () => {
	const icons = Array.from({ length: 65 }, () => '        Image(systemName: "star")');
	const source = ["struct Stars: View {", "    var body: some View {", ...icons, "    }", "}"].join(
		"\n",
	);
	const result = await runBundled("makes-ui-accessible-by-default", {
		change: new Map([whole("App/Stars.swift", source)]),
	});
	assert.equal(result.leads?.length, 60);
	assert.equal(result.facts?.symbolImages, 65);
	assert.equal(result.facts.interfaceLinesSelected, 65);
	assert.match(
		result.directions?.[0] ?? "",
		/^65 matching occurrence\(s\); the first 60 are listed and 5 are not\./u,
	);
});

void test("detached item needs no written reason, and default isolation text is a lead per target, never the project's", async () => {
	const slug = "uses-structured-concurrency-safely";
	const keyOf = await leadKeys(slug);
	const source = [
		"struct Feed: View {",
		"    var body: some View {",
		'        Button("Load") { Task { await model.load() } }',
		"        Text(model.title).onAppear { Task.detached { await model.prefetch() } }",
		"    }",
		"}",
	].join("\n");
	const change = new Map([whole("App/Feed.swift", source)]);
	const plain = await runBundled(slug, { repo: { "App/Feed.swift": source }, change });
	const said = (plain.directions ?? []).join("\n");
	assert.match(said, /no written reason is required/u);
	assert.doesNotMatch(said, /stated reason|whether \.task was available/u);
	assert.match(said, /missing @MainActor on a selected line does not by itself/u);
	assert.match(said, /leaves the module's default unknown rather than nonisolated/u);
	assert.match(said, /Check the SDK before relying on inherited SwiftUI View isolation/u);
	assert.match(said, /Swallowed failures belong to handles-errors-instead-of-swallowing-them/u);
	const withSettings = await runBundled(slug, {
		repo: {
			"App/Feed.swift": source,
			"project.yml": [
				"targets:",
				"  App:",
				"    settings:",
				"      SWIFT_DEFAULT_ACTOR_ISOLATION: MainActor",
				"  Widget:",
				"    settings:",
				"      SWIFT_DEFAULT_ACTOR_ISOLATION: nonisolated",
			].join("\n"),
			"Package.swift":
				'// .target(name: "Core", swiftSettings: [.defaultIsolation(MainActor.self)])\n',
		},
		change,
	});
	assert.deepEqual(
		(withSettings.leads ?? [])
			.filter((lead) => keyOf(lead.label) === "default-isolation-setting")
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
});

void test("each state declaration is a lead with its enclosing type, and a plain var in a view is a place to read, not a lapse", async () => {
	const slug = "owns-state-at-the-right-level";
	const keyOf = await leadKeys(slug);
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
	const result = await runBundled(slug, {
		repo: { "App/Parent.swift": source },
		change: new Map([whole("App/Parent.swift", source)]),
	});
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [lead.at.line, keyOf(lead.label), lead.facts?.enclosing]),
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

	const none = await runBundled(slug, {
		change: new Map([whole("App/Plain.swift", "let answer = 42")]),
	});
	assert.deepEqual(none.leads ?? [], []);
	assert.match(none.directions?.[0] ?? "", /^Scanned 1 added lines in 1 files .*none matched\./u);
});

void test("a change with no reference anywhere says what it scanned and what the inventory holds", async () => {
	const slug = "links-the-change-to-its-issue";
	const context = {
		"project_inventory.json": {
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
		},
	};
	const reviewed = { repository_id: 1, provider_id: 1 };
	const run = async (item: { title: string; body: string; source_branch: string }) =>
		runBundled(slug, {
			item: { ...reviewed, ...item },
			context: {
				...context,
				"metadata.json": { ...reviewed, ...item },
				"description.md": item.body,
			},
		});
	const result = await run({
		title: "Add the map",
		body: "Shows the events on a map.",
		source_branch: "map",
	});
	assert.equal(result.facts?.referencesFound, 0);
	assert.equal(result.facts.inventoryOpenIssues, 1);
	assert.equal(result.facts.linkedWorkItems, -1);
	const referenced = await run({
		title: "Add the map",
		body: "<!-- Example: Closes #12 -->\nCloses #4",
		source_branch: "4-add-the-map",
	});
	assert.equal(referenced.facts?.referencesFound, 1);
	assert.equal(referenced.facts.closingReferences, 1);
});

void test("native platform logging is a logger and standard output a print, a print under a scripts path is marked as tool output", async () => {
	const result = await runBundled("logs-through-the-platform-logger", {
		repo: {
			"App/Store.swift":
				'final class Store {\n    func load() {\n        print("loading")\n        logger.info("loaded")\n        NSLog("loaded %d", count)\n    }\n}\n',
			"App/Sync.m":
				'- (void)sync {\n    NSLog(@"synced %@", name);\n    os_log_error(OS_LOG_DEFAULT, "sync failed");\n    printf("synced");\n}\n',
			"app/src/main/java/com/example/Feed.kt":
				'class Feed {\n    fun load() {\n        Log.d("Feed", "loaded")\n        println("loaded")\n    }\n}\n',
			"app/src/main/java/com/example/Cache.java":
				'class Cache {\n    void evict() {\n        Log.w(TAG, "evicted");\n        System.out.println("evicted");\n    }\n}\n',
			"scripts/report.py": 'print("done")\n',
			"App/Log.swift": 'import OSLog\nlet logger = Logger(subsystem: "app", category: "app")\n',
		},
		change: new Map([
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
	});
	const keyOf = await leadKeys("logs-through-the-platform-logger");
	assert.deepEqual(result.facts, {
		printsSelected: 5,
		loggerCallsSelected: 6,
		printsInToolPaths: 1,
		checkoutHasLogger: 1,
		filesScanned: 5,
		linesSelected: 30,
	});
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [
			keyOf(lead.label),
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
});

void test("a print in another language beyond the shared row cap is counted but not reported as a listed row", async () => {
	const prints = Array.from({ length: 40 }, (_, i) => `        print("step ${String(i)}")`);
	const swift = ["final class Store {", "    func load() {", ...prints, "    }", "}"].join("\n");
	const result = await runBundled("logs-through-the-platform-logger", {
		change: new Map([
			whole("App/Store.swift", swift),
			whole("scripts/report.py", 'print("done")\n'),
		]),
	});
	const keyOf = await leadKeys("logs-through-the-platform-logger");
	assert.equal(result.leads?.length, 40);
	assert.ok(
		(result.leads ?? []).every(
			(lead) => keyOf(lead.label) === "print-call" && lead.facts?.language === "swift",
		),
	);
	assert.equal(result.facts?.printsSelected, 41);
	assert.equal(result.facts.printsInToolPaths, 0);
	assert.match(result.directions?.[0] ?? "", /^41 diagnostic line\(s\) selected; 40 are listed\./u);
	assert.match(result.directions?.[1] ?? "", /\(0 listed under a tool or script path\)/u);
});

void test("a literal and a named asset color are told apart, and the asset's dark appearance is read from the catalog", async () => {
	const source =
		'struct Card: View {\n    var body: some View {\n        Text("x")\n            .foregroundStyle(.white)\n            .background(Color("Card"))\n            .padding()\n            .background(Color(red: 1, green: 1, blue: 1))\n    }\n}\n';
	const result = await runBundled("uses-adaptive-colors-for-every-appearance", {
		repo: {
			"App/Assets.xcassets/Card.colorset/Contents.json":
				'{"colors":[{"idiom":"universal","color":{}},{"appearances":[{"appearance":"luminosity","value":"dark"}],"idiom":"universal","color":{}}]}',
			"App/Card.swift": source,
		},
		change: new Map([whole("App/Card.swift", source)]),
	});
	assert.equal(result.facts?.literalColors, 2);
	assert.match(
		(result.directions ?? []).join(" "),
		/A translucent scrim over an image is content/u,
	);
	assert.equal(result.facts.systemAdaptiveColors, 0);
	assert.equal(result.facts.namedAssetColors, 1);
	assert.equal(result.facts.assetsWithoutDarkAppearance, 0);
	const keyOf = await leadKeys("uses-adaptive-colors-for-every-appearance");
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [
			lead.at.line,
			keyOf(lead.label),
			lead.facts?.hasDarkAppearance ?? null,
		]),
		[
			[4, "literal-white-or-black", null],
			[5, "named-asset", true],
			[7, "fixed-components", null],
		],
	);
});

void test("a colorset's appearances are read as JSON whatever the key order, and an unreadable one stays unknown", async () => {
	const colorsets: Record<string, string> = {
		Reordered:
			'{"colors":[{"color":{},"idiom":"universal"},{"color":{},"idiom":"universal","appearances":[{"value":"dark","appearance":"luminosity"}]}]}',
		Single: '{"colors":[{"color":{},"idiom":"universal"}],"info":{"version":1}}',
		Broken: '{"colors":[',
		NoColors: '{"info":{"version":1}}',
		Empty: '{"colors":[]}',
		GamutOnly:
			'{"colors":[{"idiom":"universal","display-gamut":"sRGB","color":{}},{"idiom":"universal","display-gamut":"display-P3","color":{}}]}',
		NullEntry: '{"colors":[{"color":{},"idiom":"universal"},null]}',
		TextAppearances: '{"colors":[{"color":{},"idiom":"universal","appearances":"dark"}]}',
	};
	const source = `struct Card: View {\n    var body: some View {\n${Object.keys(colorsets)
		.map((name) => `        Text("x").foregroundStyle(Color("${name}"))`)
		.join("\n")}\n    }\n}\n`;
	const result = await runBundled("uses-adaptive-colors-for-every-appearance", {
		repo: {
			...Object.fromEntries(
				Object.entries(colorsets).map(([name, contents]) => [
					`App/Assets.xcassets/${name}.colorset/Contents.json`,
					contents,
				]),
			),
			"App/Card.swift": source,
		},
		change: new Map([whole("App/Card.swift", source)]),
	});
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [lead.facts?.asset, lead.facts?.hasDarkAppearance]),
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
});

void test("a named color resolves only to one colorset, exact name first; two candidates stay unknown", async () => {
	const dark =
		'{"colors":[{"color":{},"idiom":"universal"},{"color":{},"idiom":"universal","appearances":[{"appearance":"luminosity","value":"dark"}]}]}';
	const colorsets: [string, string][] = [
		["App/Assets.xcassets/Brand.colorset", dark],
		["Widget/Assets.xcassets/BRAND.colorset", '{"colors":[{"color":{},"idiom":"universal"}]}'],
		["App/Assets.xcassets/Accent.colorset", dark],
		["Widget/Assets.xcassets/Accent.colorset", dark],
	];
	const source =
		'struct Card: View {\n    var body: some View {\n        Text("x").foregroundStyle(Color("Brand"))\n        Text("y").foregroundStyle(Color("brand"))\n        Text("z").foregroundStyle(Color("Accent"))\n    }\n}\n';
	const result = await runBundled("uses-adaptive-colors-for-every-appearance", {
		repo: {
			...Object.fromEntries(
				colorsets.map(([folder, contents]) => [`${folder}/Contents.json`, contents]),
			),
			"App/Card.swift": source,
		},
		change: new Map([whole("App/Card.swift", source)]),
	});
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [lead.facts?.asset, lead.facts?.hasDarkAppearance]),
		[
			["Brand", true],
			["brand", "unknown"],
			["Accent", "unknown"],
		],
	);
});

void test("a cut colorset listing cannot establish a unique named asset", async () => {
	const single = '{"colors":[{"color":{},"idiom":"universal"}]}';
	const source =
		'struct Card: View {\n    var body: some View {\n        Text("x").foregroundStyle(Color("Brand"))\n    }\n}\n';
	const result = await runBundled("uses-adaptive-colors-for-every-appearance", {
		repo: {
			"A/Assets.xcassets/Brand.colorset/Contents.json": single,
			...Object.fromEntries(
				Array.from({ length: 5000 }, (_, i) => [
					`B/Assets.xcassets/Noise${String(i).padStart(4, "0")}.colorset/Contents.json`,
					single,
				]),
			),
			"Z/Assets.xcassets/Brand.colorset/Contents.json":
				'{"colors":[{"color":{},"appearances":[{"appearance":"luminosity","value":"dark"}]}]}',
			"App/Card.swift": source,
		},
		change: new Map([whole("App/Card.swift", source)]),
	});
	assert.equal(result.leads?.[0]?.facts?.hasDarkAppearance, "unknown");
	assert.equal(result.facts?.assetsWithoutDarkAppearance, 0);
	assert.equal(result.facts.assetColorsInCheckout, -1);
	assert.deepEqual(result.limitations, [
		{ what: "colorset inventory", count: 1, reason: "truncated" },
	]);
});

void test("a named color given a bundle is a named asset; the main bundle resolves, another bundle stays unknown", async () => {
	const source = [
		"struct Card: View {",
		"    var body: some View {",
		'        Text("a").foregroundStyle(Color("Brand", bundle: .main))',
		'        Text("b").foregroundStyle(Color("Brand", bundle: nil))',
		'        Text("c").foregroundStyle(Color("Brand", bundle: .module))',
		'        Image("Brand", bundle: .module)',
		"    }",
		"}",
		"",
	].join("\n");
	const result = await runBundled("uses-adaptive-colors-for-every-appearance", {
		repo: {
			"App/Assets.xcassets/Brand.colorset/Contents.json":
				'{"colors":[{"color":{},"idiom":"universal"},{"color":{},"idiom":"universal","appearances":[{"appearance":"luminosity","value":"dark"}]}]}',
			"App/Card.swift": source,
		},
		change: new Map([whole("App/Card.swift", source)]),
	});
	const keyOf = await leadKeys("uses-adaptive-colors-for-every-appearance");
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [
			lead.at.line,
			keyOf(lead.label),
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
});

void test("no gray is literal or adaptive by its spelling: each is left to the review to classify", async () => {
	const source =
		'struct Card: View {\n    var body: some View {\n        Text("x").foregroundStyle(.gray)\n        Text("x").background(Color(.systemGray2))\n        Text("x").background(Color(uiColor: .systemGray6))\n        Text("Start").padding().background(Color.gray)\n    }\n}\n';
	const result = await runBundled("uses-adaptive-colors-for-every-appearance", {
		repo: { "App/Card.swift": source },
		change: new Map([whole("App/Card.swift", source)]),
	});
	assert.equal(result.facts?.literalColors, 0);
	assert.equal(result.facts.systemAdaptiveColors, 0);
	assert.equal(result.facts.colorsToClassify, 4);
});

void test("only spellings that fix the kind are counted literal or adaptive; standard, UIKit, converted and app colors are left to classify", async () => {
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
	const result = await runBundled("uses-adaptive-colors-for-every-appearance", {
		repo: { "App/Card.swift": source },
		change: new Map([whole("App/Card.swift", source)]),
	});
	const keyOf = await leadKeys("uses-adaptive-colors-for-every-appearance");
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [lead.at.line, keyOf(lead.label)]),
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
});

void test("framework storage is not direct view I/O", async () => {
	const source = `import SwiftUI
import CoreData
struct Settings: View {
    @AppStorage("compact") private var compact = false
    @SceneStorage("selection") private var selection = ""
    @FetchRequest(sortDescriptors: []) private var items: FetchedResults<Item>
    var body: some View { Text(selection) }
}
`;
	const result = await runBundled("keeps-views-free-of-networking-and-persistence", {
		repo: { "App/Settings.swift": source },
		change: new Map([whole("App/Settings.swift", source)]),
	});
	assert.equal(result.facts?.ioCallsInViews, 0);
	assert.deepEqual(result.leads ?? [], []);
	assert.match(result.directions?.join("\n") ?? "", /^Scanned 9 added lines in 1 files/mu);
});
