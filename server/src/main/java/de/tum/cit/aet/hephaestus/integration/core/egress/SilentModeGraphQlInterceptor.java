package de.tum.cit.aet.hephaestus.integration.core.egress;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
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
    private final Cache<String, List<OperationDefinition>> operationsByDocument = Caffeine.newBuilder()
        .maximumSize(256)
        .build();

    public SilentModeGraphQlInterceptor(OutboundEgressGuard egressGuard) {
        this.egressGuard = egressGuard;
    }

    @Override
    public Mono<ClientGraphQlResponse> intercept(ClientGraphQlRequest request, Chain chain) {
        if (!isMutation(request.getDocument(), request.getOperationName())) {
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

    private boolean isMutation(String document, @Nullable String operationName) {
        List<OperationDefinition> operations = operationsByDocument.get(document, source ->
            Parser.parse(source).getDefinitionsOfType(OperationDefinition.class)
        );
        if (operationName != null) {
            for (OperationDefinition operation : operations) {
                if (operationName.equals(operation.getName())) {
                    return operation.getOperation() == OperationDefinition.Operation.MUTATION;
                }
            }
        }
        return operations
            .stream()
            .anyMatch(operation -> operation.getOperation() == OperationDefinition.Operation.MUTATION);
    }
}
