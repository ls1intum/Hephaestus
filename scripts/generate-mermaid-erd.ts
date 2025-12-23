#!/usr/bin/env node
import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { Command, InvalidArgumentError, Option } from "commander";
import { Client } from "pg";

type LogLevel = "debug" | "info" | "silent";

type Logger = {
	debug: (message: string) => void;
	info: (message: string) => void;
	error: (message: string) => void;
};

function createLogger(level: LogLevel): Logger {
	return {
		debug: (msg) => level === "debug" && console.log(msg),
		info: (msg) => level !== "silent" && console.log(msg),
		error: (msg) => console.error(msg),
	};
}

class DatabaseConnectionError extends Error {
	constructor(message: string) {
		super(message);
		this.name = "DatabaseConnectionError";
	}
}

type ColumnInfo = {
	name: string;
	type: string;
	constraints: string[];
	comment: string;
	isPrimaryKey: boolean;
	isForeignKey: boolean;
};

type RelationshipCardinality = "||--||" | "||--o{" | "}o--o{";

type RelationshipInfo = {
	childTable: string;
	childColumn: string;
	parentTable: string;
	parentColumn: string;
	constraintName: string;
	label: string;
	cardinality: RelationshipCardinality;
};

type DatabaseConfig = {
	host: string;
	port: number;
	database: string;
	username: string;
	password: string;
};

type JdbcInfo = {
	host: string;
	port: number;
	database: string;
};

type GeneratorOptions = {
	schema: string;
	includeLiquibase: boolean;
	logger: Logger;
};

const DEFAULT_OUTPUT_PATH = "docs/contributor/erd/schema.mmd";

class MermaidErdGenerator {
	private readonly client: Client;
	private readonly schema: string;
	private readonly includeLiquibase: boolean;
	private readonly logger: Logger;

	constructor(config: DatabaseConfig, options: GeneratorOptions) {
		this.client = new Client({
			host: config.host,
			port: config.port,
			user: config.username,
			password: config.password,
			database: config.database,
		});
		this.schema = options.schema;
		this.includeLiquibase = options.includeLiquibase;
		this.logger = options.logger;
	}

	async connect() {
		try {
			await this.client.connect();
			this.logger.info(`Connected to database: ${this.client.database}`);
		} catch (error) {
			const message = `Failed to connect to database: ${(error as Error).message}`;
			throw new DatabaseConnectionError(message);
		}
	}

	async disconnect() {
		try {
			await this.client.end();
			this.logger.debug("Database connection closed.");
		} catch (error) {
			this.logger.error(
				`Failed to close database connection: ${(error as Error).message}`,
			);
		}
	}

	async generate(outputFile: string) {
		const tables = await this.getTables();
		const tableData = new Map<string, ColumnInfo[]>();

		for (const tableName of tables) {
			const columns = await this.getTableColumns(tableName);
			tableData.set(tableName, columns);
		}

		const relationships = await this.getForeignKeyRelationships();
		const content = this.buildMermaidContent(tableData, relationships);

		const outputPath = path.resolve(outputFile);
		await fs.mkdir(path.dirname(outputPath), { recursive: true });
		await fs.writeFile(outputPath, content, "utf8");
		this.logger.info(`ERD written to ${outputFile}`);
	}

	private async getTables(): Promise<string[]> {
		const excludedTables = this.includeLiquibase
			? []
			: ["databasechangelog", "databasechangeloglock"];

		const exclusionsClause = excludedTables.length
			? `AND table_name NOT IN (${excludedTables.map((_, idx) => `$${idx + 2}`).join(", ")})`
			: "";
		const query = `
			SELECT table_name
			FROM information_schema.tables
			WHERE table_schema = $1
			  AND table_type = 'BASE TABLE'
			  ${exclusionsClause}
			ORDER BY table_name
		`;
		const params = [this.schema, ...excludedTables];
		const result = await this.client.query<{ table_name: string }>(
			query,
			params,
		);
		return result.rows.map((row) => row.table_name);
	}

	private async getTableColumns(tableName: string): Promise<ColumnInfo[]> {
		const query = `
			SELECT
				c.column_name,
				c.data_type,
				c.character_maximum_length,
				c.numeric_precision,
				c.numeric_scale,
				c.is_nullable,
				CASE WHEN pk.column_name IS NOT NULL THEN 'YES' ELSE 'NO' END as is_primary_key,
				CASE WHEN fk.column_name IS NOT NULL THEN 'YES' ELSE 'NO' END as is_foreign_key,
				CASE WHEN uk.column_name IS NOT NULL THEN 'YES' ELSE 'NO' END as is_unique_key
			FROM information_schema.columns c
			LEFT JOIN (
				SELECT ku.column_name
				FROM information_schema.table_constraints tc
				JOIN information_schema.key_column_usage ku
				  ON tc.constraint_name = ku.constraint_name
				 AND tc.table_schema = ku.table_schema
				WHERE tc.table_name = $1
				  AND tc.table_schema = $2
				  AND tc.constraint_type = 'PRIMARY KEY'
			) pk ON c.column_name = pk.column_name
			LEFT JOIN (
				SELECT ku.column_name
				FROM information_schema.table_constraints tc
				JOIN information_schema.key_column_usage ku
				  ON tc.constraint_name = ku.constraint_name
				 AND tc.table_schema = ku.table_schema
				WHERE tc.table_name = $1
				  AND tc.table_schema = $2
				  AND tc.constraint_type = 'FOREIGN KEY'
			) fk ON c.column_name = fk.column_name
			LEFT JOIN (
				SELECT ku.column_name
				FROM information_schema.table_constraints tc
				JOIN information_schema.key_column_usage ku
				  ON tc.constraint_name = ku.constraint_name
				 AND tc.table_schema = ku.table_schema
				WHERE tc.table_name = $1
				  AND tc.table_schema = $2
				  AND tc.constraint_type = 'UNIQUE'
			) uk ON c.column_name = uk.column_name
			WHERE c.table_name = $1
			  AND c.table_schema = $2
			ORDER BY c.ordinal_position
		`;
		const result = await this.client.query<{
			column_name: string;
			data_type: string;
			character_maximum_length: number | null;
			numeric_precision: number | null;
			numeric_scale: number | null;
			is_nullable: "YES" | "NO";
			is_primary_key: "YES" | "NO";
			is_foreign_key: "YES" | "NO";
			is_unique_key: "YES" | "NO";
		}>(query, [tableName, this.schema]);

		return result.rows.map((row) => {
			const formattedType = this.formatDataType(
				row.data_type,
				row.character_maximum_length,
				row.numeric_precision,
				row.numeric_scale,
			);

			const constraints: string[] = [];
			if (row.is_primary_key === "YES") {
				constraints.push("PK");
			}
			if (row.is_foreign_key === "YES") {
				constraints.push("FK");
			}
			if (row.is_unique_key === "YES") {
				constraints.push("UK");
			}

			const comment =
				row.is_nullable === "NO" && row.is_primary_key !== "YES"
					? "NOT NULL"
					: "";

			return {
				name: row.column_name,
				type: formattedType,
				constraints,
				comment,
				isPrimaryKey: row.is_primary_key === "YES",
				isForeignKey: row.is_foreign_key === "YES",
			};
		});
	}

	private async getForeignKeyRelationships(): Promise<RelationshipInfo[]> {
		const excludedTables = this.includeLiquibase
			? []
			: ["databasechangelog", "databasechangeloglock"];
		const exclusionsClause = excludedTables.length
			? `AND tc.table_name NOT IN (${excludedTables
					.map((_, idx) => `$${idx + 2}`)
					.join(", ")})
			   AND ccu.table_name NOT IN (${excludedTables
						.map((_, idx) => `$${idx + 2 + excludedTables.length}`)
						.join(", ")})`
			: "";
		const params = [this.schema, ...excludedTables, ...excludedTables];
		const query = `
			SELECT DISTINCT
				tc.table_name as child_table,
				kcu.column_name as child_column,
				ccu.table_name as parent_table,
				ccu.column_name as parent_column,
				tc.constraint_name
			FROM information_schema.table_constraints tc
			JOIN information_schema.key_column_usage kcu
			  ON tc.constraint_name = kcu.constraint_name
			 AND tc.table_schema = kcu.table_schema
			JOIN information_schema.constraint_column_usage ccu
			  ON ccu.constraint_name = tc.constraint_name
			 AND ccu.table_schema = tc.table_schema
			WHERE tc.constraint_type = 'FOREIGN KEY'
			  AND tc.table_schema = $1
			  ${exclusionsClause}
			ORDER BY tc.table_name, kcu.column_name
		`;
		const result = await this.client.query<{
			child_table: string;
			child_column: string;
			parent_table: string;
			parent_column: string;
			constraint_name: string;
		}>(query, params);

		const relationships: RelationshipInfo[] = [];
		for (const row of result.rows) {
			const label = this.generateRelationshipLabel(
				row.child_table,
				row.parent_table,
				row.child_column,
			);
			const cardinality = await this.detectRelationshipCardinality(
				row.child_table,
				row.child_column,
				row.parent_table,
				row.parent_column,
			);
			relationships.push({
				childTable: row.child_table,
				childColumn: row.child_column,
				parentTable: row.parent_table,
				parentColumn: row.parent_column,
				constraintName: row.constraint_name,
				label,
				cardinality,
			});
		}

		return relationships;
	}

	private generateRelationshipLabel(
		childTable: string,
		parentTable: string,
		childColumn: string,
	): string {
		const cleanColumn = childColumn.replace("_id", "").replace("id", "");

		if (childTable.includes("assignee")) {
			return "assigned_to";
		}
		if (childTable.includes("comment")) {
			return "commented_on";
		}
		if (childTable.includes("review")) {
			return "reviewed";
		}
		if (childTable.includes("label")) {
			return "labeled";
		}
		if (childTable.includes("member")) {
			return "belongs_to";
		}
		if (childTable.includes("repository") && childTable.includes("monitor")) {
			return "monitors";
		}
		if (parentTable === "user" && childColumn.includes("author")) {
			return "authored_by";
		}
		if (parentTable === "user" && childColumn.includes("creator")) {
			return "created_by";
		}
		if (parentTable === "user" && childColumn.includes("merged_by")) {
			return "merged_by";
		}
		if (
			childColumn.includes(parentTable) ||
			parentTable.includes(cleanColumn)
		) {
			return "has";
		}
		return "references";
	}

	private async detectRelationshipCardinality(
		childTable: string,
		childColumn: string,
		_parentTable: string,
		_parentColumn: string,
	): Promise<RelationshipCardinality> {
		const uniqueQuery = `
			SELECT COUNT(*) FROM (
				SELECT ku.column_name
				FROM information_schema.table_constraints tc
				JOIN information_schema.key_column_usage ku
				  ON tc.constraint_name = ku.constraint_name
				 AND tc.table_schema = ku.table_schema
				WHERE tc.table_name = $1
				  AND tc.table_schema = $2
				  AND ku.column_name = $3
				  AND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE')
			) AS unique_constraints
		`;
		const uniqueResult = await this.client.query<{ count: string }>(
			uniqueQuery,
			[childTable, this.schema, childColumn],
		);
		const isUnique = Number(uniqueResult.rows[0]?.count ?? 0) > 0;

		const fkCountResult = await this.client.query<{ count: string }>(
			`
				SELECT COUNT(*)
				FROM information_schema.table_constraints tc
				WHERE tc.table_name = $1
				  AND tc.table_schema = $2
				  AND tc.constraint_type = 'FOREIGN KEY'
			`,
			[childTable, this.schema],
		);
		const fkCount = Number(fkCountResult.rows[0]?.count ?? 0);

		const totalColumnsResult = await this.client.query<{ count: string }>(
			`
				SELECT COUNT(*)
				FROM information_schema.columns
				WHERE table_name = $1
				  AND table_schema = $2
			`,
			[childTable, this.schema],
		);
		const totalColumns = Number(totalColumnsResult.rows[0]?.count ?? 0);

		if (fkCount >= 2 && totalColumns <= fkCount + 2) {
			return "}o--o{";
		}
		if (isUnique) {
			return "||--||";
		}
		return "||--o{";
	}

	private formatDataType(
		dataType: string,
		charMaxLen: number | null,
		numPrecision: number | null,
		numScale: number | null,
	): string {
		const typeMapping: Record<string, string> = {
			"character varying": "VARCHAR",
			character: "CHAR",
			text: "TEXT",
			integer: "INTEGER",
			bigint: "BIGINT",
			smallint: "SMALLINT",
			boolean: "BOOLEAN",
			"timestamp without time zone": "TIMESTAMP",
			"timestamp with time zone": "TIMESTAMPTZ",
			date: "DATE",
			time: "TIME",
			numeric: "NUMERIC",
			decimal: "DECIMAL",
			real: "REAL",
			"double precision": "DOUBLE",
			oid: "OID",
			uuid: "UUID",
			json: "JSON",
			jsonb: "JSONB",
		};

		let formattedType = typeMapping[dataType] ?? dataType.toUpperCase();

		if (
			charMaxLen &&
			(formattedType === "VARCHAR" || formattedType === "CHAR")
		) {
			formattedType = `${formattedType}(${charMaxLen})`;
		} else if (
			numPrecision &&
			(formattedType === "NUMERIC" || formattedType === "DECIMAL")
		) {
			formattedType = numScale
				? `${formattedType}(${numPrecision},${numScale})`
				: `${formattedType}(${numPrecision})`;
		}

		return formattedType;
	}

	private buildMermaidContent(
		tableData: Map<string, ColumnInfo[]>,
		relationships: RelationshipInfo[],
	): string {
		const lines: string[] = [];

		lines.push("---");
		lines.push("config:");
		lines.push("    layout: elk");
		lines.push("---");
		lines.push("erDiagram");
		lines.push(
			"    %% Generated automatically from PostgreSQL database schema",
		);
		lines.push("    %% using scripts/generate-mermaid-erd.ts");
		lines.push("    %% To regenerate: npm run db:generate-erd-docs");
		lines.push("");
		lines.push("    direction LR");
		lines.push("");

		for (const [tableName, columns] of tableData.entries()) {
			const entityName = this.toEntityName(tableName);
			lines.push(`    ${entityName} {`);

			for (const column of columns) {
				let typeAndName = `${column.type} ${column.name}`;
				if (column.constraints.length > 0) {
					typeAndName += ` ${column.constraints.join(",")}`;
				}
				if (column.comment) {
					typeAndName += ` "${column.comment}"`;
				}
				lines.push(`        ${typeAndName}`);
			}

			lines.push("    }");
			lines.push("");
		}

		lines.push("    %% Relationships");

		const relationshipGroups: Record<
			RelationshipCardinality,
			RelationshipInfo[]
		> = {
			"||--||": [],
			"||--o{": [],
			"}o--o{": [],
		};

		for (const rel of relationships) {
			relationshipGroups[rel.cardinality].push(rel);
		}

		for (const [cardinality, rels] of Object.entries(relationshipGroups)) {
			if (rels.length === 0) {
				continue;
			}
			const cardinalityName = {
				"||--||": "One-to-One",
				"||--o{": "One-to-Many",
				"}o--o{": "Many-to-Many",
			}[cardinality as RelationshipCardinality];
			lines.push(`    %% ${cardinalityName} relationships`);

			for (const rel of rels) {
				const parentEntity = this.toEntityName(rel.parentTable);
				const childEntity = this.toEntityName(rel.childTable);
				const label = rel.label || "has";
				lines.push(
					`    ${parentEntity} ${cardinality} ${childEntity} : ${label}`,
				);
			}
			lines.push("");
		}

		lines.push("    %% Styling");
		lines.push(
			"    classDef primaryEntity fill:#e1f5fe,stroke:#01579b,stroke-width:2px",
		);
		lines.push(
			"    classDef associationEntity fill:#f3e5f5,stroke:#4a148c,stroke-width:2px",
		);
		lines.push(
			"    classDef metadataEntity fill:#e8f5e8,stroke:#1b5e20,stroke-width:2px",
		);
		lines.push("");

		for (const tableName of tableData.keys()) {
			const entityName = this.toEntityName(tableName);
			const styleClass = this.getEntityStyleClass(tableName);
			if (styleClass) {
				lines.push(`    class ${entityName} ${styleClass}`);
			}
		}

		return `${lines.join("\n")}\n`;
	}

	private toEntityName(tableName: string): string {
		const specialCases: Record<string, string> = {
			issue_assignee: "IssueAssignee",
			issue_comment: "IssueComment",
			issue_label: "IssueLabel",
			pull_request_review: "PullRequestReview",
			pull_request_review_comment: "PullRequestReviewComment",
			pull_request_requested_reviewers: "PullRequestRequestedReviewer",
			pullrequestbadpractice: "PullRequestBadPractice",
			bad_practice_detection: "BadPracticeDetection",
			bad_practice_feedback: "BadPracticeFeedback",
			repository_to_monitor: "RepositoryToMonitor",
			team_labels: "TeamLabel",
			team_members: "TeamMember",
			team_repositories: "TeamRepository",
		};

		if (specialCases[tableName]) {
			return specialCases[tableName];
		}

		let singular = tableName;
		if (tableName.endsWith("ies")) {
			singular = `${tableName.slice(0, -3)}y`;
		} else if (tableName.endsWith("s") && !tableName.endsWith("ss")) {
			singular = tableName.slice(0, -1);
		}

		return singular
			.split("_")
			.map((word) => word.charAt(0).toUpperCase() + word.slice(1))
			.join("");
	}

	private getEntityStyleClass(tableName: string): string | null {
		const primaryEntities = [
			"user",
			"repository",
			"issue",
			"milestone",
			"label",
			"team",
			"workspace",
		];

		const associationEntities = [
			"issue_assignee",
			"issue_label",
			"team_members",
			"team_labels",
			"team_repositories",
			"pull_request_requested_reviewers",
		];

		const metadataEntities = ["session", "message", "repository_to_monitor"];

		if (primaryEntities.includes(tableName)) {
			return "primaryEntity";
		}
		if (associationEntities.includes(tableName)) {
			return "associationEntity";
		}
		if (metadataEntities.includes(tableName)) {
			return "metadataEntity";
		}
		return null;
	}
}

function parseJdbcUrl(jdbcUrl: string): JdbcInfo {
	const match = /^jdbc:postgresql:\/\/([^:/]+)(?::(\d+))?\/(.+)$/.exec(jdbcUrl);
	if (!match) {
		throw new Error(
			`Invalid JDBC URL format: ${jdbcUrl}. Expected jdbc:postgresql://host:port/database`,
		);
	}
	const host = match[1];
	const port = match[2] ? Number.parseInt(match[2], 10) : 5432;
	const database = match[3];
	if (!host || !database || !Number.isFinite(port)) {
		throw new Error(`Invalid JDBC URL: ${jdbcUrl}`);
	}
	return { host, port, database };
}

function ensureNonEmpty(value: string | undefined, message: string): string {
	if (!value || value.trim().length === 0) {
		throw new Error(message);
	}
	return value;
}

function maskSecret(value: string): string {
	if (value.length <= 4) {
		return "****";
	}
	return `${value.slice(0, 2)}****${value.slice(-2)}`;
}

function parseLogLevel(value: string): LogLevel {
	if (value === "debug" || value === "info" || value === "silent") {
		return value;
	}
	throw new InvalidArgumentError(`Invalid log level: ${value}`);
}

type CliOptions = {
	jdbcUrl?: string;
	username?: string;
	password?: string;
	output?: string;
	schema: string;
	includeLiquibase: boolean;
	dryRun: boolean;
	logLevel: LogLevel;
};

async function main() {
	const program = new Command();
	program
		.name("generate-mermaid-erd")
		.description("Generate a Mermaid ERD from a PostgreSQL database schema")
		.argument("[jdbcUrl]", "JDBC URL (jdbc:postgresql://host:port/database)")
		.argument("[username]", "Database username")
		.argument("[password]", "Database password")
		.argument("[outputFile]", "Output file path")
		.option("--jdbc-url <url>", "JDBC URL (overrides positional argument)")
		.option("--username <username>", "Database username (overrides positional)")
		.option("--password <password>", "Database password (overrides positional)")
		.option("--output <file>", "Output file path")
		.option("--schema <schema>", "Database schema to inspect", "public")
		.option("--include-liquibase", "Include Liquibase metadata tables", false)
		.option("--dry-run", "Validate configuration and exit", false)
		.addOption(
			new Option("--log-level <level>", "Log verbosity")
				.choices(["debug", "info", "silent"])
				.default("info")
				.argParser(parseLogLevel),
		);

	program.parse(process.argv);

	const options = program.opts<CliOptions>();
	const args = program.args as string[];
	const jdbcUrl =
		options.jdbcUrl ?? args[0] ?? process.env.HEPHAESTUS_DB_JDBC_URL;
	const username = options.username ?? args[1] ?? process.env.POSTGRES_USER;
	const password = options.password ?? args[2] ?? process.env.POSTGRES_PASSWORD;
	const outputFile = options.output ?? args[3] ?? DEFAULT_OUTPUT_PATH;

	const logger = createLogger(options.logLevel);

	const resolvedJdbcUrl = ensureNonEmpty(
		jdbcUrl,
		"JDBC URL is required. Provide --jdbc-url or positional argument.",
	);
	const resolvedUsername = ensureNonEmpty(
		username,
		"Database username is required. Provide --username or positional argument.",
	);
	const resolvedPassword = ensureNonEmpty(
		password,
		"Database password is required. Provide --password or positional argument.",
	);
	const resolvedOutput = ensureNonEmpty(
		outputFile,
		"Output file path is required. Provide --output or positional argument.",
	);

	const parsedJdbc = parseJdbcUrl(resolvedJdbcUrl);
	const config: DatabaseConfig = {
		...parsedJdbc,
		username: resolvedUsername,
		password: resolvedPassword,
	};

	logger.debug(
		`Resolved configuration: jdbcUrl=${resolvedJdbcUrl}, username=${resolvedUsername}, password=${maskSecret(resolvedPassword)}, output=${resolvedOutput}, schema=${options.schema}`,
	);

	if (options.dryRun) {
		logger.info("Dry run enabled. Configuration validated successfully.");
		return;
	}

	const generator = new MermaidErdGenerator(config, {
		schema: options.schema,
		includeLiquibase: options.includeLiquibase,
		logger,
	});

	await generator.connect();
	try {
		await generator.generate(resolvedOutput);
	} finally {
		await generator.disconnect();
	}
}

main().catch((error) => {
	const message = error instanceof Error ? error.message : String(error);
	console.error(`ERD generation failed: ${message}`);
	process.exit(1);
});
