// Define the type of the environment variables.
declare interface Env {
  readonly NODE_ENV: string;
  readonly APPLICATION_SERVER_URL: string;
}

// Use import.meta.env.YOUR_ENV_VAR in your code.
declare interface ImportMeta {
  readonly env: Env;
}
