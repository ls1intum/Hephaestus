import { drizzle } from "drizzle-orm/node-postgres";
import { Pool } from "pg";

import env from "@/env";

import * as schema from "./schema";

const pool = new Pool({ connectionString: env.DATABASE_URL });

const db = drizzle(pool, {
	casing: "snake_case",
	schema,
});

export { pool };
export default db;
