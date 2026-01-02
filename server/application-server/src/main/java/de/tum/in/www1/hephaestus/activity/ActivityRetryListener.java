package de.tum.in.www1.hephaestus.activity;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.stereotype.Component;

/**
 * Retry listener for activity event recording operations.
 *
 * <p>Provides observability into retry behavior by:
 * <ul>
 *   <li>Counting retry attempts for alerting</li>
 *   <li>Logging retry events with context</li>
 *   <li>Tracking error types causing retries</li>
 * </ul>
 *
 * <p>Metrics emitted:
 * <ul>
 *   <li>{@code activity.events.retry_attempts} - Counter of individual retry attempts</li>
 * </ul>
 */
@Component("activityRetryListener")
public class ActivityRetryListener implements RetryListener {

    private static final Logger logger = LoggerFactory.getLogger(ActivityRetryListener.class);

    private final Counter retryAttemptsCounter;

    public ActivityRetryListener(MeterRegistry meterRegistry) {
        this.retryAttemptsCounter = Counter.builder("activity.events.retry_attempts")
            .description("Number of retry attempts for activity event recording")
            .register(meterRegistry);
    }

    @Override
    public <T, E extends Throwable> void onError(
        RetryContext context,
        RetryCallback<T, E> callback,
        Throwable throwable
    ) {
        int retryCount = context.getRetryCount();
        String errorType = throwable.getClass().getSimpleName();

        retryAttemptsCounter.increment();

        logger.warn(
            "activity.event.retry_attempt attempt={} errorType={} errorMessage={}",
            retryCount,
            errorType,
            throwable.getMessage()
        );
    }

    @Override
    public <T, E extends Throwable> void close(
        RetryContext context,
        RetryCallback<T, E> callback,
        Throwable throwable
    ) {
        int totalAttempts = context.getRetryCount();
        boolean exhausted = throwable != null;

        if (exhausted) {
            logger.error(
                "activity.event.retry_exhausted totalAttempts={} finalError={}",
                totalAttempts,
                throwable.getClass().getSimpleName()
            );
        } else if (totalAttempts > 0) {
            logger.info("activity.event.retry_succeeded afterAttempts={}", totalAttempts);
        }
    }
}
