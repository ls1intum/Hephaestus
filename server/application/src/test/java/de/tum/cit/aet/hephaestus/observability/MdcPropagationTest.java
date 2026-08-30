package de.tum.cit.aet.hephaestus.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.core.task.support.TaskExecutorAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.scheduler.Schedulers;

@Tag("unit")
class MdcPropagationTest {

    @BeforeAll
    static void enableContextPropagation() {
        new MdcContextConfiguration().register();
        Hooks.enableAutomaticContextPropagation();
    }

    @AfterAll
    static void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldPropagateMdcAcrossVirtualThreadTask() throws Exception {
        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            TaskExecutorAdapter executor = new TaskExecutorAdapter(executorService);
            executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
            MDC.put("sentinel", "preserved");
            assertEquals("preserved", executor.submit(() -> MDC.get("sentinel")).get());
        }
    }

    @Test
    void shouldPropagateMdcAcrossFluxOperators() {
        MDC.put("sentinel", "preserved");
        AtomicReference<String> observed = new AtomicReference<>();

        DataBuffer buffer = DefaultDataBufferFactory.sharedInstance.wrap(new byte[] {1, 2, 3});
        Flux.just(buffer)
                .publishOn(Schedulers.boundedElastic())
                .map(DataBuffer::readableByteCount)
                .doOnNext(ignored -> observed.set(MDC.get("sentinel")))
                .blockLast();

        assertEquals("preserved", observed.get());
    }
}
