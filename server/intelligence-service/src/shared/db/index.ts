import { drizzle } from "drizzle-orm/node-postgres";
import { Pool } from "pg";
import pino from "pino";

import env from "@/env";

import * as schema from "./schema";

const logger = pino({ name: "db" });

/**
 * Database connection pool with production-ready configuration.
 *
 * Pool settings:
 * - max: Maximum connections (10 is reasonable for most workloads)
 * - idleTimeoutMillis: Close idle connections after 30 seconds
 * - connectionTimeoutMillis: Fail fast if can't connect in 5 seconds
 */
const pool = new Pool({
	connectionString: env.DATABASE_URL,
	max: 10,
	idleTimeoutMillis: 30_000,
	connectionTimeoutMillis: 5_000,
});

// Log pool errors (don't crash, just log)
pool.on("error", (err) => {
	logger.error({ err }, "Database pool error");
});

const db = drizzle(pool, {
	casing: "snake_case",
	schema,
});

export { pool };
export default db;
