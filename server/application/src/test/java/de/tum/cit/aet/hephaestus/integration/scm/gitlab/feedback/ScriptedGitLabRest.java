package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/** GitLab's REST discussions API as scripted answers, recording each request with the body actually written. */
final class ScriptedGitLabRest implements ExchangeFunction {

    static final JsonMapper JSON = new JsonMapper();

    record Call(
            HttpMethod method,
            String path,
            String body,
            @Nullable String authorization) {
        @SuppressWarnings("unchecked")
        Map<String, Object> json() {
            return (Map<String, Object>) JSON.readValue(body, Map.class);
        }
    }

    private final Queue<Function<Call, Mono<ClientResponse>>> answers = new ConcurrentLinkedQueue<>();
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final Function<Call, Mono<ClientResponse>> otherwise;

    ScriptedGitLabRest() {
        this(call -> Mono.error(
                new IllegalStateException("Unscripted GitLab REST call " + call.method() + " " + call.path())));
    }

    // A stateful fake answers whatever was not scripted one call at a time.
    ScriptedGitLabRest(Function<Call, Mono<ClientResponse>> otherwise) {
        this.otherwise = otherwise;
    }

    WebClient.Builder builder() {
        return WebClient.builder().exchangeFunction(this);
    }

    List<Call> calls() {
        return List.copyOf(calls);
    }

    void answer(Function<Call, Mono<ClientResponse>> answer) {
        answers.add(answer);
    }

    void answer(int status, Object body) {
        answer(call -> respond(status, body));
    }

    void fail(RuntimeException failure) {
        answer(call -> Mono.error(failure));
    }

    // GitLab creates the thread that was asked for: the first note repeats the body and position, by this author.
    void createAsAsked(long noteId, String type, long authorId) {
        answer(call -> respond(201, created(call, noteId, type, authorId, position -> position)));
    }

    static Map<String, Object> created(
            Call call,
            long noteId,
            String type,
            long authorId,
            Function<@Nullable Map<String, Object>, @Nullable Map<String, Object>> position) {
        Map<String, Object> request = call.json();
        @SuppressWarnings("unchecked")
        Map<String, Object> asked = (Map<String, Object>) request.get("position");
        Map<String, Object> note = new LinkedHashMap<>();
        note.put("id", noteId);
        note.put("type", type);
        note.put("body", request.get("body"));
        note.put("system", false);
        note.put("author", Map.of("id", authorId, "username", "bot"));
        note.put("position", position.apply(asked == null ? null : new LinkedHashMap<>(asked)));
        note.put("resolvable", true);
        note.put("resolved", false);
        Map<String, Object> discussion = new LinkedHashMap<>();
        discussion.put("id", "6a9c1750b37d513a43987b574953fceb50b03ce7");
        discussion.put("individual_note", false);
        discussion.put("notes", new ArrayList<>(List.of(note)));
        return discussion;
    }

    static Mono<ClientResponse> respond(int status, Object body) {
        return Mono.just(ClientResponse.create(HttpStatus.valueOf(status))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body instanceof String text ? text : JSON.writeValueAsString(body))
                .build());
    }

    @Override
    public Mono<ClientResponse> exchange(ClientRequest request) {
        var written = new MockClientHttpRequest(request.method(), request.url());
        request.body()
                .insert(written, new BodyInserter.Context() {
                    @Override
                    public List<HttpMessageWriter<?>> messageWriters() {
                        return ExchangeStrategies.withDefaults().messageWriters();
                    }

                    @Override
                    public Optional<ServerHttpRequest> serverRequest() {
                        return Optional.empty();
                    }

                    @Override
                    public Map<String, Object> hints() {
                        return Map.of();
                    }
                })
                .block();
        String body = written.getBodyAsString().defaultIfEmpty("").block();
        var call = new Call(
                request.method(),
                request.url().getRawPath(),
                body == null ? "" : body,
                request.headers().getFirst(HttpHeaders.AUTHORIZATION));
        calls.add(call);
        var answer = answers.poll();
        return (answer == null ? otherwise : answer).apply(call);
    }
}
