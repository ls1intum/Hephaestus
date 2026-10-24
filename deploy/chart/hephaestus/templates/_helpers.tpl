{{/*
A name of at most .max characters. A name that fits is used as it is. A longer name keeps its first
characters and ends with 8 characters of the SHA-256 of the whole name, so two long names that
differ only at the end stay different, and the same input always gives the same name.
Called with (dict "name" <name> "max" <limit>).
*/}}
{{- define "hephaestus.boundedName" -}}
{{- if le (len .name) (int .max) -}}
{{- .name -}}
{{- else -}}
{{- printf "%s-%s" (trunc (int (sub .max 9)) .name | trimSuffix "-") (sha256sum .name | trunc 8) -}}
{{- end -}}
{{- end -}}

{{/*
Object names: "<release>-<component>", so two releases in one namespace never collide. Every object
reference, selector and setting that names an object reads this helper. The limit is 52: the
StatefulSet controller labels each revision with the StatefulSet name, a dash and a hash of up to
10 characters, and a label value has at most 63. The same limit keeps the first pod's name
("<name>-0") and its statefulset.kubernetes.io/pod-name label within 63, and every Service name
within the 63 of a DNS label. The app.kubernetes.io/instance label keeps the full release name.
*/}}
{{- define "hephaestus.name" -}}
{{- include "hephaestus.boundedName" (dict "name" (printf "%s-%s" .root.Release.Name .component) "max" 52) -}}
{{- end -}}

{{- define "hephaestus.selectorLabels" -}}
app.kubernetes.io/name: hephaestus
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .component }}
{{- end -}}

{{- define "hephaestus.labels" -}}
{{ include "hephaestus.selectorLabels" . }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
app.kubernetes.io/part-of: hephaestus
app.kubernetes.io/version: {{ .root.Values.release.version | quote }}
helm.sh/chart: {{ printf "%s-%s" .root.Chart.Name .root.Chart.Version | replace "+" "_" | quote }}
{{- end -}}

{{/*
Restricted Pod Security Standard. The pod and container halves are applied to every workload the
chart runs; nothing in values can widen them.
*/}}
{{- define "hephaestus.podSecurityContext" -}}
runAsNonRoot: true
runAsUser: {{ .user }}
runAsGroup: {{ .group }}
fsGroup: {{ .group }}
fsGroupChangePolicy: OnRootMismatch
seccompProfile:
  type: RuntimeDefault
{{- end -}}

{{- define "hephaestus.containerSecurityContext" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
privileged: false
capabilities:
  drop:
    - ALL
{{- end -}}

{{/*
The sandbox namespace, of review sandboxes, the Heph host and turn sandboxes. The chart owns this
name. The worker receives the same string, and the runtime derives no name of its own. The name
includes the release namespace and the release, so it is unique in the cluster, and a namespace name
is a DNS label of at most 63 characters.
*/}}
{{- define "hephaestus.sandboxNamespace" -}}
{{- include "hephaestus.boundedName" (dict "name" (printf "%s-%s-sandboxes" .root.Release.Namespace .root.Release.Name) "max" 63) -}}
{{- end -}}

{{/* The one worker's pod name. The StatefulSet gives its first pod this name. */}}
{{- define "hephaestus.workerPod" -}}
{{- printf "%s-0" (include "hephaestus.name" (dict "root" .root "component" "worker")) -}}
{{- end -}}

{{/*
The existing Sandbox Gateway port (SANDBOX_API_PORT, default 8081). The worker setting, the container
port and both gateway policies use this one value.
*/}}
{{- define "hephaestus.gatewayPort" -}}
8081
{{- end -}}

{{- define "hephaestus.image" -}}
{{- index .root.Values.images .name -}}
{{- end -}}

{{/* One environment variable read from the release Secret. */}}
{{- define "hephaestus.secretEnv" -}}
- name: {{ .env }}
  valueFrom:
    secretKeyRef:
      name: {{ .root.Values.secret.name }}
      key: {{ .key | default .env }}
      {{- if .optional }}
      optional: true
      {{- end }}
{{- end -}}

{{/*
PostgreSQL TLS: one existing Secret in the release namespace. The roles that connect mount only its
ca.crt; the bundled server mounts only its tls.crt and tls.key. No role receives the private key of
another, and the chart creates no certificate, key or CA.
*/}}
{{- define "hephaestus.postgresqlTlsSecret" -}}
{{- required "postgresql.tls.existingSecret is required: name the Secret with the PostgreSQL CA (ca.crt) and, for the bundled server, its certificate and key (tls.crt, tls.key)" .Values.postgresql.tls.existingSecret -}}
{{- end -}}

{{- define "hephaestus.postgresqlCaDirectory" -}}
/etc/hephaestus/postgresql-ca
{{- end -}}

{{/* The CA volume and its mount, for each role that connects to PostgreSQL. */}}
{{- define "hephaestus.postgresqlCaVolume" -}}
- name: postgresql-ca
  secret:
    secretName: {{ include "hephaestus.postgresqlTlsSecret" . }}
    items:
      - key: ca.crt
        path: ca.crt
    defaultMode: 292 # 0444
{{- end -}}

{{- define "hephaestus.postgresqlCaMount" -}}
- name: postgresql-ca
  mountPath: {{ include "hephaestus.postgresqlCaDirectory" . }}
  readOnly: true
{{- end -}}

{{/*
Every role verifies the server's certificate and name. The bundled server is dialled at its Service
name in the release namespace, so its certificate must name exactly that host.
*/}}
{{- define "hephaestus.databaseUrl" -}}
{{- $host := "" -}}
{{- $port := 5432 -}}
{{- if .Values.postgresql.bundled -}}
{{- $host = printf "%s.%s.svc" (include "hephaestus.name" (dict "root" . "component" "postgresql")) .Release.Namespace -}}
{{- else -}}
{{- $host = required "postgresql.external.host is required when postgresql.bundled is false" .Values.postgresql.external.host -}}
{{- $port = int .Values.postgresql.external.port -}}
{{- end -}}
{{- printf "postgresql://%s:%d/%s?sslmode=verify-full&sslrootcert=%s/ca.crt" $host $port .Values.postgresql.database (include "hephaestus.postgresqlCaDirectory" .) -}}
{{- end -}}

{{- define "hephaestus.natsUrl" -}}
{{- if .Values.nats.bundled -}}
{{- printf "nats://%s:4222" (include "hephaestus.name" (dict "root" . "component" "nats")) -}}
{{- else -}}
{{- required "nats.external.url is required when nats.bundled is false" .Values.nats.external.url -}}
{{- end -}}
{{- end -}}

{{/*
Downward-API pod address. The management listener (9090) binds to it, the counterpart of
"$HOSTNAME.shared-network" in Compose: reachable for probes and the metrics scraper, never through a
Service or the Gateway.
*/}}
{{- define "hephaestus.env.pod" -}}
- name: POD_IP
  valueFrom:
    fieldRef:
      fieldPath: status.podIP
- name: MANAGEMENT_SERVER_ADDRESS
  value: $(POD_IP)
{{- end -}}

{{/* What the verified release lock says this container runs, reported under `release` in /actuator/info. */}}
{{- define "hephaestus.env.release" -}}
- name: APP_VERSION
  value: {{ trimPrefix "v" .Values.release.version | quote }}
- name: HEPHAESTUS_RELEASE_COMMIT
  value: {{ .Values.release.commit | quote }}
- name: HEPHAESTUS_IMAGE_APPLICATION_SERVER
  value: {{ include "hephaestus.image" (dict "root" . "name" "application-server") | quote }}
- name: DEPLOYMENT_ENVIRONMENT
  value: {{ .Values.deploymentEnvironment | quote }}
- name: TRACING_SAMPLING_PROBABILITY
  value: {{ .Values.config.tracing.samplingProbability | quote }}
- name: TRACING_OTLP_ENABLED
  value: {{ .Values.config.tracing.otlpEnabled | quote }}
- name: TRACING_OTLP_ENDPOINT
  value: {{ .Values.config.tracing.otlpEndpoint | quote }}
{{- end -}}

{{/* Settings every application role reads. */}}
{{- define "hephaestus.env.shared" -}}
- name: APPLICATION_HOST_URL
  value: {{ printf "https://%s" .Values.host | quote }}
- name: HEPHAESTUS_TRUSTED_PROXIES
  value: {{ .Values.trustedProxies | quote }}
- name: DATABASE_URL
  value: {{ include "hephaestus.databaseUrl" . | quote }}
{{ include "hephaestus.secretEnv" (dict "root" . "env" "DATABASE_USERNAME") }}
{{ include "hephaestus.secretEnv" (dict "root" . "env" "DATABASE_PASSWORD") }}
{{ include "hephaestus.secretEnv" (dict "root" . "env" "HEPHAESTUS_SECURITY_ENCRYPTION_KEY") }}
{{ include "hephaestus.secretEnv" (dict "root" . "env" "HEPHAESTUS_SECURITY_CREDENTIAL_ENCRYPTION_KEY") }}
- name: HEPHAESTUS_SECURITY_CREDENTIAL_ENCRYPTION_KEY_VERSION
  value: {{ .Values.config.credentialKey.version | int | quote }}
{{ include "hephaestus.secretEnv" (dict "root" . "env" "HEPHAESTUS_SECURITY_PRIOR_CREDENTIAL_ENCRYPTION_KEY" "optional" true) }}
- name: HEPHAESTUS_SECURITY_PRIOR_CREDENTIAL_ENCRYPTION_KEY_VERSION
  value: {{ .Values.config.credentialKey.priorVersion | quote }}
- name: HEPHAESTUS_SECURITY_CREDENTIAL_ROTATION_ENABLED
  value: {{ .Values.config.credentialKey.rotationEnabled | quote }}
{{- /* Both agent image guards load in every role (ADR 0031): a reference the workers cannot run
fails every pod, so each role is owed the lock's digest. */}}
- name: HEPHAESTUS_AGENT_IMAGE_REQUIRE_DIGEST
  value: "true"
- name: HEPHAESTUS_AGENT_IMAGE_REFERENCE
  value: {{ include "hephaestus.image" (dict "root" . "name" "agent-pi") | quote }}
{{- /* Outline and Slack load role-agnostic beans; every role gets the same switches. */}}
- name: HEPHAESTUS_INTEGRATION_OUTLINE_ENABLED
  value: {{ .Values.config.outline.enabled | quote }}
- name: HEPHAESTUS_INTEGRATION_OUTLINE_ALLOWED_ORIGINS
  value: {{ .Values.config.outline.allowedOrigins | quote }}
- name: HEPHAESTUS_INTEGRATION_SLACK_ENABLED
  value: {{ .Values.config.slack.enabled | quote }}
{{- end -}}

{{/* The server and webhook roles both talk to JetStream and verify webhook deliveries. */}}
{{- define "hephaestus.env.ingestion" -}}
{{ include "hephaestus.secretEnv" (dict "root" . "env" "NATS_USERNAME") }}
{{ include "hephaestus.secretEnv" (dict "root" . "env" "NATS_PASSWORD") }}
- name: NATS_DURABLE_CONSUMER_NAME
  value: {{ .Values.config.durableConsumerName | quote }}
{{ include "hephaestus.secretEnv" (dict "root" . "env" "WEBHOOK_SECRET") }}
{{ include "hephaestus.secretEnv" (dict "root" . "env" "WEBHOOK_ROUTING_SECRET") }}
{{ include "hephaestus.secretEnv" (dict "root" . "env" "WEBHOOK_ROUTING_PREVIOUS_SECRET" "optional" true) }}
{{ include "hephaestus.secretEnv" (dict "root" . "env" "HEPHAESTUS_INTEGRATION_SLACK_SIGNING_SECRET" "optional" true) }}
{{- end -}}

{{/* The server and worker roles both reach the SCM. */}}
{{- define "hephaestus.env.scm" -}}
- name: GH_APP_ID
  value: {{ .Values.config.github.appId | int | quote }}
{{ include "hephaestus.secretEnv" (dict "root" . "env" "GH_APP_PRIVATE_KEY" "optional" true) }}
{{ include "hephaestus.secretEnv" (dict "root" . "env" "GH_AUTH_TOKEN" "optional" true) }}
- name: GIT_CHECKOUT_ENABLED
  value: {{ .Values.config.gitCheckoutEnabled | quote }}
- name: GITLAB_ENABLED
  value: {{ .Values.config.gitlab.enabled | quote }}
- name: GITLAB_DEFAULT_SERVER_URL
  value: {{ .Values.config.gitlab.oauthBaseUrl | quote }}
- name: HEPHAESTUS_FABRIC_ROOT
  value: /data/git-repos
{{- end -}}

{{/*
Probes read the management listener's health groups, the same groups that /livez and /readyz serve
on the application port. The worker binds its application port to loopback, so only the management
port is reachable on every role.
*/}}
{{- define "hephaestus.probes" -}}
startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: management
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: {{ .startupFailures }}
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: management
  periodSeconds: 15
  timeoutSeconds: 5
  failureThreshold: 3
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: management
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3
{{- end -}}

{{/* A NetworkPolicy peer: pods with these labels in this namespace. */}}
{{- define "hephaestus.peer" -}}
- namespaceSelector:
    matchLabels:
      kubernetes.io/metadata.name: {{ .namespace }}
  podSelector: {{ dict "matchLabels" (.podLabels | default dict) | toJson }}
{{- end -}}

{{- define "hephaestus.componentPeer" -}}
- podSelector:
    matchLabels:
      {{- include "hephaestus.selectorLabels" . | nindent 6 }}
{{- end -}}

{{/* NetworkPolicy rules that several components share. Each is called with the root context. */}}
{{- define "hephaestus.policy.databaseEgress" -}}
{{- if .Values.postgresql.bundled -}}
- to:
    {{- include "hephaestus.componentPeer" (dict "root" . "component" "postgresql") | nindent 4 }}
  ports:
    - port: 5432
      protocol: TCP
{{- else -}}
- to:
    - ipBlock:
        cidr: {{ .Values.postgresql.external.cidr }}
  ports:
    - port: {{ .Values.postgresql.external.port }}
      protocol: TCP
{{- end -}}
{{- end -}}

{{- define "hephaestus.policy.natsEgress" -}}
{{- if .Values.nats.bundled -}}
- to:
    {{- include "hephaestus.componentPeer" (dict "root" . "component" "nats") | nindent 4 }}
  ports:
    - port: 4222
      protocol: TCP
{{- else -}}
- to:
    - ipBlock:
        cidr: {{ .Values.nats.external.cidr }}
  ports:
    - port: {{ .Values.nats.external.port }}
      protocol: TCP
{{- end -}}
{{- end -}}

{{/* HTTPS to the internet: Git providers, OAuth, AI providers. Private ranges stay excluded. */}}
{{- define "hephaestus.policy.externalEgress" -}}
- to:
    - ipBlock:
        cidr: {{ .Values.networkPolicy.externalEgress.cidr }}
        {{- with .Values.networkPolicy.externalEgress.except }}
        except:
          {{- toYaml . | nindent 10 }}
        {{- end }}
  ports:
    - port: 443
      protocol: TCP
{{- end -}}

{{/*
The sandbox pods that may reach the one worker's gateway: review sandboxes and the Heph host that
the runtime stamped with that worker's pod name. Turn sandboxes never match.
*/}}
{{- define "hephaestus.policy.gatewaySources" -}}
matchLabels:
  hephaestus.build/worker-pod: {{ include "hephaestus.workerPod" . }}
matchExpressions:
  - key: hephaestus.build/sandbox-kind
    operator: In
    values: ["review", "host"]
{{- end -}}

{{- define "hephaestus.policy.metricsIngress" -}}
{{- if and .Values.networkPolicy.metrics.namespace (not .Values.networkPolicy.metrics.podLabels) -}}
{{- fail "networkPolicy.metrics.podLabels needs at least one label: a namespace alone would admit every pod in it." -}}
{{- end -}}
{{- if .Values.networkPolicy.metrics.namespace -}}
- from:
    {{- include "hephaestus.peer" .Values.networkPolicy.metrics | nindent 4 }}
  ports:
    - port: management
      protocol: TCP
{{- end -}}
{{- end -}}
