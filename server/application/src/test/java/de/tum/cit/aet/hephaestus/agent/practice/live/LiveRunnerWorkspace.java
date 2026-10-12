package de.tum.cit.aet.hephaestus.agent.practice.live;

import static de.tum.cit.aet.hephaestus.agent.practice.live.PracticeCriteriaCaseFixtures.FIXTURE_DIR;
import static de.tum.cit.aet.hephaestus.agent.practice.live.PracticeCriteriaCaseFixtures.fixtureCommand;

import de.tum.cit.aet.hephaestus.agent.catalog.ModelKind;
import de.tum.cit.aet.hephaestus.agent.config.FrozenModel;
import de.tum.cit.aet.hephaestus.agent.practice.PracticeAgentRequest;
import de.tum.cit.aet.hephaestus.agent.practice.PracticePiAdapter;
import de.tum.cit.aet.hephaestus.agent.runtime.AgentImageProperties;
import de.tum.cit.aet.hephaestus.agent.runtime.PiResultParser;
import de.tum.cit.aet.hephaestus.agent.runtime.PiRuntimeFactory;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.sandbox.ImagePullPolicy;
import de.tum.cit.aet.hephaestus.agent.task.Task;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelope;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.testconfig.PiSdkInstallation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A practice review workspace for the live tests, staged as the sandbox stages it: the files and the variables of the
 * real {@link PracticePiAdapter} spec, the toolkit link that the image makes, a real checkout of the change, and the
 * inputs each admitted practice's program reads. Only paths move: the container's {@code /workspace} and
 * {@code /home/agent} become directories this harness owns.
 */
final class LiveRunnerWorkspace {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** The toolkit that the image copies to {@code /opt/practice-scripts/lib}. */
    static final Path TOOLKIT = Path.of("..", "..", "docker", "agents", "practice-scripts", "lib")
            .toAbsolutePath()
            .normalize();

    /** The job timeout of a live run: the runner's own budget is this minus the shutdown buffer. */
    static final int TIMEOUT_SECONDS = 300;

    private LiveRunnerWorkspace() {}

    /** The practice adapter with the server's default work per practice and direct model budget. */
    static PracticePiAdapter adapter() {
        return new PracticePiAdapter(
                new PiRuntimeFactory(MAPPER),
                new PiResultParser(MAPPER, new SimpleMeterRegistry()),
                new AgentImageProperties("hephaestus/agent-pi:live", ImagePullPolicy.NEVER),
                new PracticeReviewProperties(false, 15, 5, null, 12, 16000, 200_000, 1000),
                MAPPER);
    }

    /** The spec the worker would run for this model, with these practice script models bound. */
    static PracticeAgentRequest request(String modelId, String jobToken, Map<ModelKind, FrozenModel> models) {
        return new PracticeAgentRequest(
                "openai-completions", modelId, 131_072, 4096, null, jobToken, TIMEOUT_SECONDS, models);
    }

    /** Writes every input file of the spec, and links the SDK and the toolkit where the image has them. */
    static void stageSpec(Path workspace, Map<String, byte[]> inputFiles) throws IOException {
        for (Map.Entry<String, byte[]> file : inputFiles.entrySet()) {
            Path target = workspace.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.write(target, file.getValue());
        }
        // ESM resolution walks node_modules upward from the importing file; production binds the SDK there.
        link(workspace.resolve("node_modules"), PiSdkInstallation.SDK_DIR.resolve("node_modules"));
        // The link the practice runner profile makes before Node starts (PracticeRunnerProfile.setupCommands).
        Path toolkit = workspace.resolve(SandboxLayout.PRACTICE_SCRIPT_LIBRARY_LINK);
        Files.createDirectories(toolkit.getParent());
        link(toolkit, TOOLKIT);
        Files.createDirectories(workspace.resolve(".home"));
    }

    /** The change of the practice script live tests: added comments, which every model slot of the fixture reads. */
    static final Path CART_CHANGE = Path.of("..", "..", "docker", "agents", "practice-scripts", "fixtures")
            .resolve("cart-change.diff")
            .toAbsolutePath()
            .normalize();

    /** The captured witnesses of the reviewed checkout, under {@code scm.repository.tree}: admission needs both. */
    static final List<String> CHECKOUT_WITNESSES = List.of(
            SandboxLayout.REPO_MOUNT_RELATIVE + ".git/HEAD",
            SandboxLayout.REPO_MOUNT_RELATIVE + ".git/" + GitRepositoryManager.CAPTURED_REFS);

    /** The fixture's {@code LoginService.swift}, added to an empty base. */
    static ObjectNode stageLoginChange(Path workspace) throws IOException, InterruptedException {
        return stageChange(
                workspace,
                Map.of(),
                Map.of("LoginService.swift", Files.readString(FIXTURE_DIR.resolve("LoginService.swift"))),
                "Add login service");
    }

    /**
     * The shared {@link #CART_CHANGE}, beside the toolkit's {@code lib/diff-parser.ts}, which the fixture's agent
     * searches the checkout for.
     */
    static ObjectNode stageCartChange(Path workspace) throws IOException, InterruptedException {
        Map<String, String> base = new LinkedHashMap<>();
        Map<String, String> head = new LinkedHashMap<>();
        String library = Files.readString(TOOLKIT.resolve("diff-parser.ts"));
        base.put("lib/diff-parser.ts", library);
        head.put("lib/diff-parser.ts", library);
        StringBuilder before = new StringBuilder();
        StringBuilder after = new StringBuilder();
        String path = null;
        for (String line : Files.readAllLines(CART_CHANGE)) {
            if (line.startsWith("+++ b/")) {
                path = line.substring("+++ b/".length());
                continue;
            }
            // The other headers name the file and the hunk; the lines below them carry its two sides.
            if (line.startsWith("diff ") || line.startsWith("--- ") || line.startsWith("@@")) continue;
            if (line.startsWith("+")) after.append(line.substring(1)).append('\n');
            else if (line.startsWith("-")) before.append(line.substring(1)).append('\n');
            else if (line.startsWith(" ")) {
                before.append(line.substring(1)).append('\n');
                after.append(line.substring(1)).append('\n');
            }
        }
        if (path == null) throw new IllegalStateException("The change names no file: " + CART_CHANGE);
        base.put(path, before.toString());
        head.put(path, after.toString());
        return stageChange(workspace, base, head, "Count the items in the cart");
    }

    /**
     * A real checkout of the reviewed change, captured as the server captures it: the head checked out detached, its
     * {@link #CHECKOUT_WITNESSES}, the pinned change, the record ({@link LiveWorkRecords}), the comments and the folder
     * index that names them.
     *
     * @return the record staged as {@code context/metadata.json}
     */
    static ObjectNode stageChange(Path workspace, Map<String, String> base, Map<String, String> head, String title)
            throws IOException, InterruptedException {
        Path repo = workspace.resolve(SandboxLayout.REPO_MOUNT_RELATIVE);
        Files.createDirectories(repo);
        git(workspace, "init", "-q", repo.toString());
        writeFiles(repo, base);
        git(workspace, "-C", repo.toString(), "add", "--all");
        git(workspace, "-C", repo.toString(), "commit", "-q", "--allow-empty", "-m", "Start the fixture");
        String baseSha =
                git(workspace, "-C", repo.toString(), "rev-parse", "HEAD").strip();
        writeFiles(repo, head);
        git(workspace, "-C", repo.toString(), "add", "--all");
        git(workspace, "-C", repo.toString(), "commit", "-q", "-m", title);
        String headSha =
                git(workspace, "-C", repo.toString(), "rev-parse", "HEAD").strip();
        // The capture checks the head out detached and lists the refs the container may see; the fixture has none.
        git(workspace, "-C", repo.toString(), "checkout", "-q", "--detach", headSha);
        Files.writeString(repo.resolve(".git/" + GitRepositoryManager.CAPTURED_REFS), "");
        int additions = 0;
        for (Map.Entry<String, String> file : head.entrySet()) {
            if (!file.getValue().equals(base.get(file.getKey()))) {
                additions += (int) file.getValue().lines().count();
            }
        }

        Path context = workspace.resolve(SandboxLayout.CONTEXT_PREFIX);
        Files.createDirectories(context);
        Files.write(
                context.resolve("change.json"),
                MAPPER.writeValueAsBytes(Map.of("base_sha", baseSha, "head_sha", headSha)));
        ObjectNode record = LiveWorkRecords.pullRequest(headSha, title, "", additions);
        Files.write(context.resolve("metadata.json"), LiveWorkRecords.staged(record));
        Files.copy(FIXTURE_DIR.resolve("comments.json"), context.resolve("comments.json"));
        Files.write(
                workspace.resolve(SandboxLayout.MANIFEST_PATH),
                MAPPER.writeValueAsBytes(manifest(capturedArtifacts())));
        return record;
    }

    /** The repository tree source's identity of the staged checkout: its head and that head's tree. */
    static String treeIdentity(Path workspace) throws IOException, InterruptedException {
        String repo = workspace.resolve(SandboxLayout.REPO_MOUNT_RELATIVE).toString();
        return git(workspace, "-C", repo, "rev-parse", "HEAD").strip() + ":"
                + git(workspace, "-C", repo, "rev-parse", "HEAD^{tree}").strip();
    }

    /** What {@link #stageChange} captured, by path, with the source that captured each. */
    static Map<String, String> capturedArtifacts() {
        Map<String, String> artifacts = new LinkedHashMap<>();
        artifacts.put(SandboxLayout.CONTEXT_PREFIX + "metadata.json", "scm.pull-request.core");
        artifacts.put(SandboxLayout.CONTEXT_PREFIX + "change.json", "scm.pull-request.diff");
        artifacts.put(SandboxLayout.CONTEXT_PREFIX + "comments.json", "scm.pull-request.comments");
        CHECKOUT_WITNESSES.forEach(witness -> artifacts.put(witness, "scm.repository.tree"));
        return artifacts;
    }

    /** The folder index of these artifacts, every source available, as {@code JobFolderIndex} serializes one. */
    static ObjectNode manifest(Map<String, String> artifacts) {
        ObjectNode manifest = MAPPER.createObjectNode().put("artifactKind", "scm.pull_request");
        var sources = manifest.putArray("sources");
        var registered = manifest.putArray("artifacts");
        new LinkedHashSet<>(artifacts.values()).forEach(kind -> {
            ObjectNode source = sources.addObject().put("kind", kind);
            source.putObject("state").put("availability", "AVAILABLE").put("content", "NON_EMPTY");
            var listed = source.putArray("artifacts");
            artifacts.forEach((path, owner) -> {
                if (owner.equals(kind)) listed.addObject().put("path", path);
            });
        });
        artifacts.forEach((path, kind) ->
                registered.addObject().put("kind", kind).putObject("artifact").put("path", path));
        return manifest;
    }

    private static void writeFiles(Path repo, Map<String, String> files) throws IOException {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path target = repo.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
        }
    }

    /** The practice catalog of one practice, as the catalog injector stages it. */
    static void stagePractice(Path workspace, String slug, String name, String criteria, long revisionId)
            throws IOException {
        Path practices = workspace.resolve(SandboxLayout.PRACTICES_PREFIX);
        Files.createDirectories(practices);
        Files.write(
                practices.resolve("index.json"),
                MAPPER.writeValueAsBytes(
                        List.of(Map.of("slug", slug, "name", name, "group", "security", "revisionId", revisionId))));
        Files.writeString(practices.resolve(slug + ".md"), criteria);
    }

    /**
     * What {@code PracticeScriptInputs} stages for every practice in the catalog index: why it is reviewed and its
     * earlier claim, none here. A practice without a program runs the built-in review.
     */
    static void stagePracticeInputs(Path workspace, String kind, @Nullable Map<String, String> programs)
            throws IOException {
        JsonNode index = MAPPER.readTree(
                workspace.resolve(SandboxLayout.PRACTICES_PREFIX + "index.json").toFile());
        Path staged = workspace.resolve(SandboxLayout.PRACTICE_SCRIPT_PRACTICES_PREFIX);
        Files.createDirectories(staged);
        for (JsonNode practice : index) {
            String slug = practice.path("slug").asString();
            ObjectNode cause = MAPPER.createObjectNode().put("kind", kind);
            cause.putObject("subject").put("role", "AUTHOR");
            cause.putObject("practice")
                    .put("revisionId", practice.path("revisionId").asLong(1));
            cause.putObject("scope")
                    .put("key", kind + ":1:AUTHOR")
                    .put("mode", "change")
                    .put("description", "What its author contributed to the reviewed work");
            ObjectNode why = cause.putObject("cause")
                    .put("type", "requested")
                    .put("origin", "MANUAL")
                    .put("admittedAt", LiveWorkRecords.AT.toString());
            ObjectNode state = why.putObject("state").put("state", "OPEN");
            if (kind.equals("scm.pull_request")) {
                state.put("draftStatus", "NOT_DRAFT");
            }
            Files.write(
                    staged.resolve(slug + SandboxLayout.PRACTICE_SCRIPT_CAUSE_SUFFIX), MAPPER.writeValueAsBytes(cause));
            Files.writeString(staged.resolve(slug + SandboxLayout.PRACTICE_SCRIPT_PREVIOUS_SUFFIX), "null\n");
            String program = programs == null ? null : programs.get(slug);
            if (program != null) {
                Files.writeString(staged.resolve(slug + ".ts"), program);
            }
        }
    }

    /** The task envelope, built from the production record so that a new field fails here at compile time. */
    static void stageTask(Path workspace, String prompt) throws IOException {
        TaskEnvelope envelope = TaskEnvelope.of(UUID.randomUUID(), 1L, new Task(prompt, 1, "test/fixture"));
        Files.write(
                workspace.resolve(SandboxLayout.TASK_ENVELOPE_FILENAME),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(envelope));
    }

    /**
     * Starts the runner with the spec's variables and the proxy's URL and job token, as the sandbox adapter adds
     * them. Nothing else of this JVM's environment reaches it: the review session has a bash tool. With a log, its
     * output and errors go there; without one, the caller reads them.
     */
    static Process spawn(
            Path workspace, Map<String, String> specEnvironment, String proxyUrl, String jobToken, @Nullable Path log)
            throws IOException {
        ProcessBuilder runner = new ProcessBuilder("node", SandboxLayout.RUNNER_SCRIPT_FILENAME);
        runner.directory(workspace.toFile());
        if (log != null) {
            runner.redirectErrorStream(true).redirectOutput(log.toFile());
        }
        Map<String, String> env = runner.environment();
        String path = env.getOrDefault("PATH", "/usr/local/bin:/usr/bin:/bin");
        env.clear();
        Map<String, String> moved = new HashMap<>();
        specEnvironment.forEach((name, value) -> moved.put(
                name,
                value.replace(SandboxLayout.WORKSPACE_ROOT, workspace.toString())
                        .replace("/home/agent", workspace.resolve(".home").toString())));
        env.putAll(moved);
        env.put("PATH", path);
        env.put("LANG", "C.UTF-8");
        env.put("PI_RUNNER_CWD", workspace.toString());
        env.put("LLM_PROXY_URL", proxyUrl);
        env.put("LLM_PROXY_TOKEN", jobToken);
        // Some Node versions skip a symlinked node_modules; NODE_PATH resolves the SDK's bare specifiers.
        env.put("NODE_PATH", PiSdkInstallation.SDK_DIR.resolve("node_modules").toString());
        Files.createDirectories(Path.of(
                moved.getOrDefault("TMPDIR", workspace.resolve(".home/tmp").toString())));
        return runner.start();
    }

    private static String git(Path workspace, String... arguments) throws IOException, InterruptedException {
        List<String> identity = List.of(
                "git",
                "-c",
                "user.name=Example Developer",
                "-c",
                "user.email=developer@example.org",
                "-c",
                "commit.gpgsign=false");
        String[] command = new String[identity.size() + arguments.length];
        System.arraycopy(identity.toArray(String[]::new), 0, command, 0, identity.size());
        System.arraycopy(arguments, 0, command, identity.size(), arguments.length);
        return fixtureCommand(workspace, command);
    }

    private static void link(Path link, Path target) throws IOException {
        if (Files.exists(link) || Files.isSymbolicLink(link)) {
            Files.delete(link);
        }
        Files.createSymbolicLink(link, target);
    }
}
