import { serve } from "@hono/node-server";

import app from "./app";
import env from "./env";

const port = env.PORT;
console.log(`Server is running on port http://localhost:${port}`);

serve({
  fetch: app.fetch,
  port,
});
