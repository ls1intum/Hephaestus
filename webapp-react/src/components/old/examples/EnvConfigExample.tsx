import env from "@/environment";

/**
 * Example component showing how to use environment configuration in components
 */
export const EnvConfigExample = () => {
  return (
    <div className="p-4 bg-gray-100 rounded-lg">
      <h2 className="text-xl font-bold mb-4">Environment Configuration</h2>
      <div className="grid gap-2">
        <div>
          <span className="font-medium">Version:</span> {env.version}
        </div>
        <div>
          <span className="font-medium">Client URL:</span> {env.clientUrl}
        </div>
        <div>
          <span className="font-medium">Server URL:</span> {env.serverUrl}
        </div>
        <div>
          <span className="font-medium">Keycloak:</span> {env.keycloak.url} (Realm: {env.keycloak.realm})
        </div>
        <div>
          <span className="font-medium">Sentry Environment:</span> {env.sentry.environment}
        </div>
      </div>
    </div>
  );
};