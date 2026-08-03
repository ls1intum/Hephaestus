package de.tum.cit.aet.hephaestus.integration.core.egress;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import java.util.Objects;
import org.springframework.graphql.client.ClientGraphQlRequest;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.GraphQlClientInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

@Component
public class SilentModeGraphQlInterceptor implements GraphQlClientInterceptor {

    private static final Object MUTATION_OPERATION = new Object();

    private final OutboundEgressGuard egressGuard;
    private final Cache<String, Boolean> mutationDocuments = Caffeine.newBuilder().maximumSize(256).build();

    public SilentModeGraphQlInterceptor(OutboundEgressGuard egressGuard) {
        this.egressGuard = egressGuard;
    }

    @Override
    public Mono<ClientGraphQlResponse> intercept(ClientGraphQlRequest request, Chain chain) {
        if (!mutationDocuments.get(request.getDocument(), SilentModeGraphQlInterceptor::isMutation)) {
            return chain.next(request);
        }

        String operationName = Objects.requireNonNullElse(request.getOperationName(), "anonymous");
        return chain.next(request).contextWrite(context -> context.put(MUTATION_OPERATION, operationName));
    }

    ExchangeFilterFunction httpAttemptFilter() {
        return (request, next) ->
            Mono.deferContextual(context -> {
                if (context.hasKey(MUTATION_OPERATION)) {
                    egressGuard.requireDeliveryAllowed("scm.graphql." + context.get(MUTATION_OPERATION));
                }
                return next.exchange(request);
            });
    }

    private static boolean isMutation(String document) {
        return Parser.parse(document)
            .getDefinitionsOfType(OperationDefinition.class)
            .stream()
            .anyMatch(operation -> operation.getOperation() == OperationDefinition.Operation.MUTATION);
    }
}
