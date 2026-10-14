import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { mkdtemp, mkdir, readdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import process from "node:process";
import { test } from "node:test";
import { pathToFileURL } from "node:url";

import { appendInclude, parseCommand, promote, promoteDraft, validateDraft } from "./db-utils.ts";
import { environmentForGitFixture } from "./lib/git-environment.ts";

const generated = `<?xml version="1.1" encoding="UTF-8" standalone="no"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog">
    <changeSet author="root (generated)" id="1788329906048-6">
        <dropForeignKeyConstraint baseTableName="consent_decision" constraintName="fk_consent_decision_notice"/>
    </changeSet>
    <changeSet author="root (generated)" id="1788329906048-3">
        <addNotNullConstraint columnDataType="timestamp(6) with timezone" columnName="updated_at" tableName="outline_collection" validate="true"/>
    </changeSet>
</databaseChangeLog>
`;

void test("a draft becomes a new changelog with sequential ids and one author", () => {
	const changelog = promoteDraft(generated, 1_700_000_000_000);
	assert.match(changelog, /^<\?xml version="1\.0" encoding="UTF-8"\?>\n<databaseChangeLog /u);
	assert.match(changelog, /dbchangelog-latest\.xsd/u);
	assert.deepEqual(
		[...changelog.matchAll(/<changeSet id="(?<id>[^"]+)" author="hephaestus">/gu)].map(
			(m) => m.groups?.id,
		),
		["1700000000000-1", "1700000000000-2"],
	);
	assert.doesNotMatch(changelog, /root \(generated\)|version="1\.1"/u);
	assert.match(changelog, /dropForeignKeyConstraint[\s\S]*addNotNullConstraint/u);
	assert.ok(changelog.endsWith("</databaseChangeLog>\n"));
});

void test("a draft appends to the changelog this branch already added, continuing its numbering", () => {
	const existing = promoteDraft(generated, 1_700_000_000_000);
	const appended = promoteDraft(generated, 1_700_000_000_000, existing);
	assert.deepEqual(
		[...appended.matchAll(/<changeSet id="(?<id>[^"]+)"/gu)].map((m) => m.groups?.id),
		["1700000000000-1", "1700000000000-2", "1700000000000-3", "1700000000000-4"],
	);
	assert.equal(appended.split("</databaseChangeLog>").length, 2);
});

void test("an empty draft is rejected rather than promoted to an empty changelog", () => {
	assert.throws(() => promoteDraft("<databaseChangeLog/>", 1_700_000_000_000), /no change sets/u);
});

void test("master.xml gains the include at the end and never twice", () => {
	const master = `<databaseChangeLog>
    <include file="./changelog/1_changelog.xml" relativeToChangelogFile="true"/>
</databaseChangeLog>
`;
	const once = appendInclude(master, "2_changelog.xml");
	assert.equal(
		once,
		`<databaseChangeLog>
    <include file="./changelog/1_changelog.xml" relativeToChangelogFile="true"/>
    <include file="./changelog/2_changelog.xml" relativeToChangelogFile="true"/>
</databaseChangeLog>
`,
	);
	assert.equal(appendInclude(once, "2_changelog.xml"), once);
});

const nativeSet = `<changeSet id="draft-1" author="draft">
        <preConditions onFail="HALT" onError="HALT">
            <sqlCheck expectedResult="1">SELECT count(*) FROM pg_constraint WHERE conname = 'ck_example'</sqlCheck>
        </preConditions>
        <comment>Replace a native constraint the JPA diff cannot see.</comment>
        <sql><![CDATA[
ALTER TABLE example DROP CONSTRAINT ck_example, ADD CONSTRAINT ck_example CHECK (value > 0 AND value < 10);
        ]]></sql>
        <rollback>
            <stop>Restore a verified backup.</stop>
        </rollback>
    </changeSet>`;
const native = `<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog">
    ${nativeSet}
</databaseChangeLog>
`;

void test("an authored draft keeps its preconditions, SQL and rollback while promotion assigns id and author", () => {
	validateDraft(native);
	const changelog = promoteDraft(native, 1_700_000_000_000);
	const body = nativeSet.slice(nativeSet.indexOf(">") + 1);
	assert.ok(changelog.includes(`<changeSet id="1700000000000-1" author="hephaestus">${body}`));
	assert.doesNotMatch(changelog, /draft-1|author="draft"/u);
});

const wrap = (inner: string): string => `<databaseChangeLog>${inner}</databaseChangeLog>`;

void test("an authored draft that promotion would silently change is refused", () => {
	for (const [draft, reason] of [
		[wrap(nativeSet.replace("</comment>", "")), /comment/u],
		[`<!DOCTYPE databaseChangeLog [<!ENTITY x SYSTEM "file:///etc/hosts">]>${native}`, /DOCTYPE/u],
		[
			native.replace(
				"<databaseChangeLog ",
				'<databaseChangeLog objectQuotingStrategy="QUOTE_ALL_OBJECTS" ',
			),
			/objectQuotingStrategy/u,
		],
		[
			native.replace(
				"<databaseChangeLog ",
				'<databaseChangeLog xmlns:ext="http://www.liquibase.org/xml/ns/dbchangelog-ext" ',
			),
			/xmlns:ext/u,
		],
		["<databaseChangeLog/>", /no change sets/u],
		[wrap(`<include file="other.xml"/>${nativeSet}`), /Only change sets/u],
		[wrap(`<!-- kept nowhere -->${nativeSet}`), /Only change sets/u],
		[
			wrap(nativeSet.replace('author="draft"', 'author="draft" runInTransaction="false"')),
			/runInTransaction/u,
		],
		[wrap('<changeSet id="a" author="b"></changeSet>'), /makes no change/u],
		[wrap(`<changeSet id="a" author="b"/>${nativeSet}`), /closing tag/u],
	] as const) {
		assert.throws(() => validateDraft(draft), reason, draft);
	}
});

void test("--from-draft belongs to draft-changelog and leaves the generated mode unchanged", () => {
	assert.deepEqual(parseCommand(["draft-changelog"]), {
		command: "draft-changelog",
		help: false,
		fromDraft: undefined,
		externalDatabase: false,
	});
	assert.equal(parseCommand(["draft-changelog", "--from-draft", "d.xml"]).fromDraft, "d.xml");
	assert.throws(
		() => parseCommand(["check-drift", "--from-draft", "d.xml"]),
		/only to draft-changelog/u,
	);
	assert.throws(() => parseCommand(["draft-changelog", "d.xml"]), /Unexpected arguments: d\.xml/u);
	assert.equal(parseCommand(["--help"]).help, true);
});

for (const second of ["untracked", "staged"] as const) {
	void test(`a branch with a committed and a second, ${second} changelog is refused without a third or a master.xml change`, async () => {
		const repository = await mkdtemp(path.join(tmpdir(), "db-promote-"));
		const env = environmentForGitFixture();
		const git = (...args: string[]) =>
			execFileSync("git", args, { cwd: repository, env, stdio: ["ignore", "pipe", "pipe"] });
		const changelogs = path.join(repository, "server/application/src/main/resources/db/changelog");
		const master = path.join(repository, "server/application/src/main/resources/db/master.xml");
		const masterXml = "<databaseChangeLog>\n</databaseChangeLog>\n";
		try {
			git("init", "--quiet", "--initial-branch=main");
			git("config", "user.name", "Test");
			git("config", "user.email", "test@example.invalid");
			await mkdir(changelogs, { recursive: true });
			await writeFile(master, masterXml);
			await writeFile(path.join(changelogs, "1_changelog.xml"), native);
			git("add", ".");
			git("commit", "--quiet", "-m", "released");
			git("checkout", "--quiet", "-b", "feature");
			await writeFile(path.join(changelogs, "2_changelog.xml"), native);
			git("add", ".");
			git("commit", "--quiet", "-m", "branch changelog");
			await writeFile(path.join(changelogs, "3_changelog.xml"), native);
			if (second === "staged") {
				git("add", ".");
			}

			await assert.rejects(promote(native, repository), /several changelogs/u);
			const listing = await readdir(changelogs);
			assert.deepEqual(listing.toSorted(), [
				"1_changelog.xml",
				"2_changelog.xml",
				"3_changelog.xml",
			]);
			assert.equal(await readFile(master, "utf8"), masterXml);
		} finally {
			await rm(repository, { recursive: true, force: true });
		}
	});
}

void test("appending authored SQL preserves PostgreSQL dollar quotes and replacement tokens", () => {
	const sql = "DO $$ BEGIN RAISE EXCEPTION 'Restore a verified backup'; END $$; -- $& $` $'";
	const draft = `<databaseChangeLog><changeSet id="draft" author="draft"><sql splitStatements="false">${sql}</sql></changeSet></databaseChangeLog>`;
	const existing = promoteDraft(generated, 1_700_000_000_000);
	const appended = promoteDraft(draft, 1_700_000_000_000, existing);
	assert.ok(appended.includes(`<sql splitStatements="false">${sql}</sql>`));
	assert.equal(appended.split("</databaseChangeLog>").length, 2);
});

void test("--external-database applies to all database commands, including an authored draft", () => {
	for (const command of ["check-drift", "draft-changelog", "generate-erd"]) {
		assert.equal(parseCommand([command, "--external-database"]).externalDatabase, true);
	}
	assert.deepEqual(
		parseCommand(["draft-changelog", "--external-database", "--from-draft", "d.xml"]),
		{ command: "draft-changelog", help: false, fromDraft: "d.xml", externalDatabase: true },
	);
	for (const flag of ["--external", "--external-database=false", "--packaged-server"]) {
		assert.throws(() => parseCommand(["check-drift", flag]));
	}
});

void test("CLI help requires a disposable external database", () => {
	const result = spawnSync(process.execPath, ["scripts/db-utils.ts", "--help"], {
		encoding: "utf8",
	});
	assert.equal(result.status, 0, result.stderr);
	assert.match(result.stdout, /--external-database.*disposable database/u);
});

for (const ci of [false, true]) {
	for (const operation of ["check-drift", "draft-changelog", "from-draft", "generate-erd"]) {
		void test(`${operation}: external database leaves Compose and data directories untouched with CI=${ci}`, async () => {
			const directory = await mkdtemp(path.join(tmpdir(), "db-external-"));
			try {
				const preload = path.join(directory, "mock.mjs");
				const command = operation === "from-draft" ? "draft-changelog" : operation;
				await writeFile(
					preload,
					`import assert from "node:assert/strict";
import * as fs from "node:fs/promises";
import { mock } from "node:test";
const calls = [];
const writes = [];
const ci = ${ci};
const operation = ${JSON.stringify(operation)};
const draftXml = ${JSON.stringify(generated)};
const nativeXml = ${JSON.stringify(native)};
const changelog = "server/application/src/main/resources/db/changelog/1700000000000_changelog.xml";
mock.module("node:fs/promises", { exports: {
  ...fs,
  async access(file) {
    assert.doesNotMatch(String(file), /postgres-data/u);
  },
  async readFile(file, ...args) {
    const name = String(file);
    if (name.endsWith("server/.env")) return "CI=false\\nPOSTGRES_HOST=file-host\\nPOSTGRES_PORT=5432\\nPOSTGRES_DB=file-db\\nPOSTGRES_USER=file-user\\nPOSTGRES_PASSWORD=file-password\\n";
    if (name.endsWith("changelog_new.xml")) {
      if (operation === "check-drift") throw new Error("No drift");
      return draftXml;
    }
    if (name === "d.xml") return nativeXml;
    if (name.endsWith(changelog)) return "<databaseChangeLog>\\n</databaseChangeLog>\\n";
    return fs.readFile(file, ...args);
  },
  async rename() { throw new Error("External mode must not swap data directories"); },
  async rm(file, options) {
    assert.ok(String(file).endsWith("changelog_new.xml"));
    assert.deepEqual(options, { force: true });
  },
  async writeFile(file) {
    assert.ok(String(file).endsWith(changelog));
    writes.push(String(file));
  }
}});
mock.module(${JSON.stringify(new URL("lib/process.ts", import.meta.url).href)}, { exports: {
  async run(executable, args, options) {
    calls.push({ executable, args });
    assert.notEqual(executable, "docker");
    if (args[0].endsWith("run-gradlew.ts")) {
      assert.equal(args.includes("-PpackagedServer=true"), ci);
      assert.ok(args.includes("-PpostgresPort=55432"));
      assert.equal(options.env.POSTGRES_HOST, "127.0.0.1");
      assert.equal(options.env.POSTGRES_DB, "external-test");
    } else {
      assert.equal(args[0], "generate-mermaid-erd.ts");
      assert.ok(args.includes("jdbc:postgresql://127.0.0.1:55432/external-test"));
      assert.ok(args.includes("external-user"));
      assert.equal(options.env.POSTGRES_PASSWORD, "external-password");
    }
  },
  async succeeds(executable, args) {
    calls.push({ executable, args });
    assert.equal(executable, "pg_isready");
    assert.ok(args.includes("--version") || (args.includes("127.0.0.1") && args.includes("55432")));
    return true;
  },
  async output(executable, args) {
    assert.equal(executable, "git");
    if (args[0] === "merge-base") return "main-base";
    return args.includes("HEAD") ? changelog : "";
  }
}});
process.on("beforeExit", () => {
  assert.ok(calls.some(call => call.executable === "pg_isready" && call.args.includes("-h")));
  const migrations = calls.filter(call => call.args[0].endsWith("run-gradlew.ts"));
  assert.equal(migrations.length, operation === "draft-changelog" ? 2 : 1);
  assert.equal(migrations.some(call => call.args.includes(":application:liquibaseDiff")), operation === "check-drift" || operation === "draft-changelog");
  assert.equal(calls.some(call => call.args[0] === "generate-mermaid-erd.ts"), operation !== "check-drift");
  assert.equal(writes.length, operation === "draft-changelog" || operation === "from-draft" ? 1 : 0);
});
`,
				);
				const result = spawnSync(
					process.execPath,
					[
						"--experimental-test-module-mocks",
						"--import",
						pathToFileURL(preload).href,
						"scripts/db-utils.ts",
						command,
						...(ci ? [] : ["--external-database"]),
						...(operation === "from-draft" ? ["--from-draft", "d.xml"] : []),
					],
					{
						encoding: "utf8",
						env: environmentForGitFixture({
							CI: String(ci),
							POSTGRES_HOST: "127.0.0.1",
							POSTGRES_PORT: "55432",
							POSTGRES_DB: "external-test",
							POSTGRES_USER: "external-user",
							POSTGRES_PASSWORD: "external-password",
						}),
					},
				);
				assert.equal(result.status, 0, result.stdout + result.stderr);
			} finally {
				await rm(directory, { recursive: true, force: true });
			}
		});
	}
}
