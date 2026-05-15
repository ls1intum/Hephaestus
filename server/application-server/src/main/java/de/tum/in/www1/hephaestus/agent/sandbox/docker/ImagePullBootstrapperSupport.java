package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;

final class ImagePullBootstrapperSupport {

    private ImagePullBootstrapperSupport() {}

    static void applyPolicy(
        String image,
        ImagePullPolicy policy,
        DockerImageOperations imageOps,
        String metricBase,
        MeterRegistry meterRegistry,
        Logger log
    ) {
        if (policy == ImagePullPolicy.NEVER) {
            if (!imageOps.imageIsPresent(image)) {
                log.warn("pull-policy=NEVER but {} is not in the local Docker daemon — container creation will fail.", image);
            }
            return;
        }

        if (policy == ImagePullPolicy.IF_NOT_PRESENT && imageOps.imageIsPresent(image)) {
            log.debug("Image {} already cached — skipping pull.", image);
            return;
        }

        if (!imageOps.ping()) {
            log.warn("Docker daemon not reachable; skipping startup image pull for {}", image);
            meterRegistry.counter(metricBase + ".skipped", "reason", "docker_unreachable").increment();
            return;
        }
        log.info("Pulling image {} (policy={}) …", image, policy);
        Timer.Sample sample = Timer.start(meterRegistry);
        boolean ok = imageOps.pullImage(image);
        sample.stop(meterRegistry.timer(metricBase + ".duration", "outcome", ok ? "success" : "failure"));
        if (ok) {
            log.info("Image pull complete: {}", image);
        } else {
            log.warn("Startup pull of {} did not complete; daemon's cached image will be used.", image);
            meterRegistry.counter(metricBase + ".failure").increment();
        }
    }
}
