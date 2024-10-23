package de.tum.in.www1.hephaestus.nats;

import io.nats.client.*;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;

import java.io.IOException;
import java.util.Arrays;
import java.time.Duration;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.kohsuke.github.GHEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandlerRegistry;

@Service
public class NatsConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(NatsConsumerService.class);

    @Value("${nats.server}")
    private String natsServer;

    @Value("${monitoring.repositories}")
    private String[] repositoriesToMonitor;

    private Connection natsConnection;

    private final GitHubMessageHandlerRegistry handlerRegistry;

    public NatsConsumerService(GitHubMessageHandlerRegistry handlerRegistry) {
        this.handlerRegistry = handlerRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() throws IOException, InterruptedException, JetStreamApiException {
        Options options = Options.builder().server(natsServer).connectionListener((c, t) -> {
            logger.info("Connection: " + c.getServerInfo().getPort() + " " + t);
        }).maxReconnects(-1).reconnectWait(Duration.ofSeconds(2)).build();
        natsConnection = Nats.connect(options);

        // String consumerName = "github-consumer";
        ConsumerConfiguration consumerConfig = ConsumerConfiguration.builder()
                // .durable(consumerName)
                .filterSubjects(getSubjects())
                .deliverPolicy(DeliverPolicy.ByStartTime)
                .startTime(ZonedDateTime.now().minusDays(30))
                .maxBatch(0)
                .build();

        // JetStreamManagement jsm = natsConnection.jetStreamManagement();
        // logger.info("Consumer names:" + jsm.getConsumerNames("github"));
        // logger.info("Consumers:" + jsm.getConsumers("github"));
        // jsm.deleteConsumer("github", "github-consumer");

        // try {
        StreamContext streamContext = natsConnection.getStreamContext("github");
        ConsumerContext consumerContext = streamContext.createOrUpdateConsumer(consumerConfig);
        // consumerContext = streamContext.getConsumerContext(natsServer)
        // } catch (JetStreamApiException | IOException e) {
        // // JetStreamApiException:
        // // the stream or consumer did not exist
        // // IOException:
        // // likely a connection problem
        // return;
        // }

        // GitHub github = new GitHubBuilder().build();

        MessageHandler handler = msg -> {
            msg.ack();
            String subject = msg.getSubject();
            String lastPart = subject.substring(subject.lastIndexOf(".") + 1);
            GHEvent eventType = GHEvent.valueOf(lastPart.toUpperCase());
            GitHubMessageHandler<?> eventHandler = handlerRegistry.getHandler(eventType);
            if (eventHandler == null) {
                logger.warn("No handler found for event type: " + eventType);
                return;
            }
            eventHandler.onMessage(msg);

            // logger.info("Received message: " + msg.getSubject() + " metaData: " +
            // msg.metaData());
            // byte[] data = msg.getData();
            // String payload = new String(data, StandardCharsets.UTF_8);
            // StringReader reader = new StringReader(payload);

            // String subject = msg.getSubject();
            // if (subject.endsWith(".pull_request")) {
            // try {
            // GHEventPayload.PullRequest pullRequestEvent =
            // github.parseEventPayload(reader,
            // GHEventPayload.PullRequest.class);
            // logger.info("Received pull request: " +
            // pullRequestEvent.getPullRequest().getTitle());
            // } catch (IOException e) {
            // logger.error("Failed to parse pull request payload.", e);
            // }
            // } else if (subject.endsWith(".pull_request_review_comment")) {
            // try {
            // GHEventPayload.PullRequestReviewComment pullRequestReviewCommentEvent =
            // github.parseEventPayload(
            // reader,
            // GHEventPayload.PullRequestReviewComment.class);
            // logger.info("Received pull request review comment: " +
            // pullRequestReviewCommentEvent.getComment().getBody());
            // } catch (IOException e) {
            // logger.error("Failed to parse pull request review comment payload.", e);
            // }
            // } else if (subject.endsWith(".pull_request_review")) {
            // try {
            // GHEventPayload.PullRequestReview pullRequestReviewEvent =
            // github.parseEventPayload(reader,
            // GHEventPayload.PullRequestReview.class);
            // logger.info("Received pull request review: " +
            // pullRequestReviewEvent.getReview().getBody());
            // } catch (IOException e) {
            // logger.error("Failed to parse pull request review payload.", e);
            // }
            // }
        };

        consumerContext.consume(handler);

        // try (MessageConsumer consumer = ) {
        // } catch (JetStreamApiException | IOException e) {
        // // JetStreamApiException:
        // // 1. the stream or consumer did not exist
        // // 2. api calls under the covers theoretically this could fail, but
        // practically
        // // it won't.
        // // IOException:
        // // likely a connection problem
        // } catch (Exception e) {
        // // this is from the FetchConsumer being AutoCloseable, but should never be
        // // called
        // // as work inside the close is already guarded by try/catch
        // }
    }

    /**
     * Subjects to monitor.
     * 
     * @return The subjects to monitor.
     */
    private String[] getSubjects() {
        String[] events = handlerRegistry.getSupportedEvents().stream()
                .map(GHEvent::name)
                .map(String::toLowerCase)
                .toArray(String[]::new);

        return Arrays.stream(repositoriesToMonitor)
                .map(this::getSubjectPrefix)
                .flatMap(prefix -> Arrays.stream(events).map(event -> prefix + "." + event))
                .toArray(String[]::new);
    }

    /**
     * Get subject prefix from ownerWithName for the given repository.
     * 
     * @param ownerWithName The owner and name of the repository.
     * @return The subject prefix, i.e. "github.owner.name" sanitized.
     * @throws IllegalArgumentException if the repository string is improperly
     *                                  formatted.
     */
    private String getSubjectPrefix(String ownerWithName) {
        if (ownerWithName == null || ownerWithName.trim().isEmpty()) {
            throw new IllegalArgumentException("Repository identifier cannot be null or empty.");
        }

        String sanitized = ownerWithName.replace(".", "~");
        String[] parts = sanitized.split("/");

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    String.format("Invalid repository format: '%s'. Expected format 'owner/repository'.",
                            ownerWithName));
        }

        return "github." + parts[0] + "." + parts[1];
    }
}