package de.tum.in.www1.hephaestus.gitprovider.sync;

import io.nats.client.ConsumerContext;
import io.nats.client.JetStreamApiException;
import io.nats.client.Message;
import io.nats.client.MessageConsumer;
import io.nats.client.StreamContext;
import io.nats.client.api.ConsumerConfiguration;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a NATS consumer for a single workspace (or installation).
 *
 * <h2>Purpose</h2>
 * Processes messages SEQUENTIALLY to avoid race conditions within a workspace.
 * Each workspace gets its own consumer instance, enabling parallel processing
 * across workspaces while maintaining sequential ordering within each.
 *
 * <h2>Thread Safety</h2>
 * This class is thread-safe with the following guarantees:
 * <ul>
 *   <li>{@code running} - AtomicBoolean for shutdown signaling across threads</li>
 *   <li>{@code messageQueue} - LinkedBlockingQueue for thread-safe message passing</li>
 *   <li>Lifecycle methods ({@code start}, {@code stop}, {@code updateSubjects}) are
 *       synchronized to prevent concurrent state transitions</li>
 *   <li>{@code subscription} and {@code currentSubjects} are volatile for safe publication</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Construct with workspace ID, consumer context, and message handler</li>
 *   <li>Call {@code start()} to begin consuming - spawns a virtual thread processor</li>
 *   <li>Optionally call {@code updateSubjects()} to change subscribed subjects</li>
 *   <li>Call {@code stop()} to gracefully shutdown - drains queue and NAKs remaining messages</li>
 * </ol>
 *
 * @see NatsConsumerService
 */
public class WorkspaceNatsConsumer {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceNatsConsumer.class);

    private final Long workspaceId; // null for installation consumer
    private final String consumerName;
    private final ConsumerContext context;
    private final StreamContext streamContext;
    private final Consumer<Message> messageHandler;

    private volatile String[] currentSubjects;
    private volatile MessageConsumer subscription;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Queue for sequential message processing.
     * Messages are added by the NATS callback and processed one at a time.
     */
    private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();

    /** Virtual thread for processing messages from the queue */
    private volatile Thread processorThread;

    /**
     * Creates a new workspace consumer with a custom message handler.
     *
     * @param workspaceId    the workspace ID (null for installation consumer)
     * @param consumerName   the durable consumer name
     * @param context        the NATS consumer context
     * @param streamContext  the NATS stream context
     * @param subjects       the subjects to subscribe to
     * @param messageHandler the handler for processing messages
     */
    public WorkspaceNatsConsumer(
        Long workspaceId,
        String consumerName,
        ConsumerContext context,
        StreamContext streamContext,
        String[] subjects,
        Consumer<Message> messageHandler
    ) {
        this.workspaceId = workspaceId;
        this.consumerName = consumerName;
        this.context = context;
        this.streamContext = streamContext;
        this.currentSubjects = subjects.clone();
        this.messageHandler = messageHandler;
    }

    /**
     * Creates a new workspace consumer without a message handler.
     * <p>
     * <b>Warning:</b> This constructor is deprecated. Messages will be ACKed without
     * processing, which means events will be silently dropped. Use the constructor
     * that takes a {@code messageHandler} parameter instead.
     *
     * @param workspaceId   the workspace ID (null for installation consumer)
     * @param consumerName  the durable consumer name
     * @param context       the NATS consumer context
     * @param streamContext the NATS stream context
     * @param subjects      the subjects to subscribe to
     * @deprecated Use {@link #WorkspaceNatsConsumer(Long, String, ConsumerContext, StreamContext, String[], Consumer)}
     */
    @Deprecated(forRemoval = true)
    public WorkspaceNatsConsumer(
        Long workspaceId,
        String consumerName,
        ConsumerContext context,
        StreamContext streamContext,
        String[] subjects
    ) {
        this(workspaceId, consumerName, context, streamContext, subjects, msg -> {
            log.warn("No message handler configured, ACKing message without processing: {}", msg.getSubject());
            msg.ack();
        });
    }

    /**
     * Gets the consumer name.
     */
    public String getConsumerName() {
        return consumerName;
    }

    /**
     * Gets the workspace ID.
     *
     * @return the workspace ID, or null for installation consumer
     */
    public Long getWorkspaceId() {
        return workspaceId;
    }

    /**
     * Checks if this consumer is running.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Starts the consumer.
     * <p>
     * Spawns a virtual thread for sequential message processing and
     * subscribes to the configured NATS subjects.
     *
     * @throws IOException           if subscription fails
     * @throws JetStreamApiException if JetStream API call fails
     */
    public synchronized void start() throws IOException, JetStreamApiException {
        if (running.get()) {
            return;
        }

        running.set(true);

        // Start the sequential message processor on a virtual thread
        String threadName = "nats-processor-" + (workspaceId != null ? "ws-" + workspaceId : "installation");
        processorThread = Thread.ofVirtual().name(threadName).start(this::processMessagesSequentially);

        // Subscribe to NATS - messages go to queue for sequential processing
        subscription = context.consume(this::enqueueMessage);

        String label = workspaceId != null ? "workspace-" + workspaceId : "installation";
        log.debug("Started consumer for {} with {} subjects", label, currentSubjects.length);
    }

    /**
     * Stops the consumer gracefully.
     * <p>
     * Closes the subscription, interrupts the processor thread, and NAKs
     * any remaining messages in the queue.
     */
    public synchronized void stop() {
        running.set(false);

        if (subscription != null) {
            try {
                subscription.close();
            } catch (Exception e) {
                log.debug("Error closing subscription: {}", e.getMessage());
            }
            subscription = null;
        }

        // Interrupt the processor thread to wake it from blocking
        if (processorThread != null) {
            processorThread.interrupt();
            processorThread = null;
        }

        // Drain remaining messages (NAK them) - exceptions during shutdown are expected
        drainMessageQueue();

        String label = workspaceId != null ? "workspace-" + workspaceId : "installation";
        log.debug("Stopped consumer for {}", label);
    }

    /**
     * Updates the subjects this consumer listens to.
     * <p>
     * Reconfigures the NATS consumer and restarts the subscription to
     * pick up the new subjects.
     *
     * @param newSubjects the new subjects to subscribe to
     * @throws IOException           if subscription restart fails
     * @throws JetStreamApiException if consumer update fails
     */
    public synchronized void updateSubjects(String[] newSubjects) throws IOException, JetStreamApiException {
        if (Arrays.equals(currentSubjects, newSubjects)) {
            return;
        }

        // Update the consumer configuration with new subjects
        var config = context.getConsumerInfo().getConsumerConfiguration();
        streamContext.createOrUpdateConsumer(ConsumerConfiguration.builder(config).filterSubjects(newSubjects).build());

        currentSubjects = newSubjects.clone();

        // Restart subscription to pick up new subjects
        if (subscription != null) {
            try {
                subscription.close();
            } catch (Exception e) {
                log.warn("Error closing subscription during subject update: {}", e.getMessage());
            }
        }
        subscription = context.consume(this::enqueueMessage);

        String label = workspaceId != null ? "workspace-" + workspaceId : "installation";
        log.debug("Updated consumer for {} with {} subjects", label, newSubjects.length);
    }

    /**
     * Enqueues a message for sequential processing.
     * <p>
     * Called by the NATS subscription callback. If the consumer is not
     * running, the message is immediately NAKed.
     */
    private void enqueueMessage(Message msg) {
        if (!running.get()) {
            msg.nak();
            return;
        }

        try {
            messageQueue.put(msg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            msg.nak();
        }
    }

    /**
     * Processes messages from the queue ONE AT A TIME.
     * <p>
     * This ensures sequential processing within a workspace,
     * avoiding race conditions for shared entities.
     */
    private void processMessagesSequentially() {
        String label = workspaceId != null ? "workspace-" + workspaceId : "installation";
        log.debug("Started sequential message processor for {}", label);

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // Block waiting for next message (with timeout for shutdown check)
                Message msg = messageQueue.poll(1, TimeUnit.SECONDS);
                if (msg != null) {
                    messageHandler.accept(msg);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in message processor for {}: {}", label, e.getMessage(), e);
            }
        }

        log.debug("Stopped sequential message processor for {}", label);
    }

    /**
     * Drains remaining messages from the queue, NAKing each one.
     * <p>
     * Errors during shutdown are expected (connection already closed)
     * and are logged at trace level.
     */
    private void drainMessageQueue() {
        Message msg;
        while ((msg = messageQueue.poll()) != null) {
            try {
                msg.nak();
            } catch (Exception e) {
                // Expected during shutdown when connection is already closed
                log.trace("Ignored NAK error during shutdown: {}", e.getMessage());
            }
        }
    }
}
