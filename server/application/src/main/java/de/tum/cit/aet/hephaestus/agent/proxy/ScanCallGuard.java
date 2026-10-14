package de.tum.cit.aet.hephaestus.agent.proxy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.config.FrozenModel;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.agent.scan.ScanBudget;
import de.tum.cit.aet.hephaestus.agent.gateway.SandboxGatewayProperties;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnWorkerRole;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsageSourceType;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshotQuery;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Serializes scan calls on their owning gateway until usage commits, including parallel program calls. */
@Component
@ConditionalOnWorkerRole
@RequiredArgsConstructor
public class ScanCallGuard {
    private final ReviewJobRepository jobs;
    private final ReviewSnapshotQuery reviews;
    private final ScanBudget budget;
    private final PracticeReviewProperties limits;
    private final SandboxGatewayProperties gateway;
    private final ObjectMapper mapper;
    // Active leases and their waiters hold strong references; idle locks can be reclaimed without evicting a live fence.
    private final Cache<Attempt,ReentrantLock> locks=Caffeine.newBuilder().weakValues().build();

    public Optional<Lease> acquire(ProxyRouting route,byte[] body) {
        var attempt=route.attempt();
        Long workspace=route.workspaceId();
        if(attempt==null || workspace==null || attempt.sourceType()==UsageSourceType.MENTOR_TURN)return Optional.empty();
        var job=jobs.findByIdAndWorkspaceId(attempt.sourceId(),workspace).orElseThrow(ScanCallGuard::refused);
        var review=reviews.find(workspace,job.getReviewId()).orElseThrow(ScanCallGuard::refused);
        if(review.kind()!=ReviewKind.SCAN)return Optional.empty();
        var lock=locks.get(new Attempt(job.getId(),attempt.number()),key->new ReentrantLock());
        lock.lock();
        try {
            job=jobs.findByIdAndWorkspaceId(job.getId(),workspace).orElseThrow(ScanCallGuard::refused);
            if(job.getStatus()!=ReviewJobStatus.RUNNING || job.getAttempt()!=attempt.number()
                    || !java.util.Objects.equals(job.getWorkerId(),attempt.workerId()))throw refused();
            var snapshot=ConfigSnapshot.fromJson(job.getConfigSnapshot(),mapper);
            var model=route.practiceScriptSlot()==null || route.practiceScriptSlot()==ModelKind.CHAT
                    ? snapshot.model():snapshot.practiceScriptSlot(route.practiceScriptSlot());
            if(model==null || model.priceSnapshot()==null)throw refused();
            var cap=reviews.budgetUsd(workspace,job.getReviewId()).orElseThrow(ScanCallGuard::refused);
            var spent=budget.spent(workspace,job.getReviewId());
            BigDecimal inFlight=budget.inFlight(job,snapshot);
            long maximumOutput=java.util.Objects.requireNonNullElse(snapshot.maxOutputTokens(),snapshot.contextWindow()==null?0:snapshot.contextWindow());
            if(maximumOutput<=0 || body.length>gateway.maxRequestBytes())throw refused();
            long output=requestedOutput(body,maximumOutput);
            BigDecimal next=ScanBudget.cost(model.priceSnapshot(),body.length,output).setScale(6,java.math.RoundingMode.CEILING);
            if(!spent.priced() || spent.cost().add(inFlight).add(next).compareTo(cap)>0)throw refused();
            if(route.practiceScriptSlot()==null && job.getLlmCalls()>=(long)Math.max(3,job.getInput().assignments().size())*limits.practiceModelCalls())throw refused();
            return Optional.of(new Lease(lock));
        }catch(RuntimeException | Error failure){lock.unlock();throw failure;}
    }

    private long requestedOutput(byte[] body,long limit) {
        var request=mapper.readTree(body);
        long requested=limit;
        for(String field:java.util.List.of("max_tokens","max_completion_tokens","max_output_tokens")) {
            if(!request.has(field))continue;
            var value=request.path(field);
            if(!value.isIntegralNumber() || value.asLong()<=0 || value.asLong()>limit)throw refused();
            requested=Math.min(requested,value.asLong());
        }
        return requested;
    }

    private static ResponseStatusException refused(){return new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,"The scan budget or its frozen execution bounds do not permit this call.");}
    private record Attempt(UUID jobId,int number) {}
    public static final class Lease implements AutoCloseable {
        private final ReentrantLock lock;
        private Lease(ReentrantLock lock){this.lock=lock;}
        @Override public void close(){lock.unlock();}
    }
}
