package de.tum.cit.aet.hephaestus.agent.metrics;

public final class AgentMetrics {

    public static final String AGENT_JOB_CLAIM_LATENCY = "agent.job.claim.latency";
    public static final String AGENT_JOB_CONCURRENCY_REJECTED = "agent.job.concurrency.rejected";
    public static final String AGENT_JOB_DELIVERY_RECOVERED = "agent.job.delivery.recovered";
    public static final String AGENT_JOB_EXECUTION_DURATION = "agent.job.execution.duration";
    public static final String AGENT_JOB_INFRA_RETRY_REQUEUED = "agent.job.infra.retry.requeued";
    public static final String AGENT_JOB_TOTAL = "agent.job.total";
    public static final String AGENT_JOB_DURATION = "agent.job.duration";
    public static final String AGENT_JOB_ORPHAN_FAILED = "agent.job.orphan.failed";
    public static final String AGENT_JOB_ORPHAN_REQUEUED = "agent.job.orphan.requeued";
    public static final String AGENT_JOB_RETENTION_DELETED = "agent.job.retention.deleted";
    public static final String AGENT_JOB_RETENTION_STRIPPED = "agent.job.retention.stripped";
    public static final String AGENT_JOB_SNAPSHOT_UNREADABLE = "agent.job.snapshot.unreadable";
    public static final String AGENT_JOB_ZOMBIE_REAPED = "agent.job.zombie.reaped";
    public static final String AGENT_QUEUE_DEPTH = "agent.queue.depth";
    public static final String AGENT_QUEUE_HEALTH_SAMPLER_FAILURES = "agent.queue.health.sampler.failures";
    public static final String AGENT_QUEUE_HELD = "agent.queue.held";
    public static final String AGENT_QUEUE_OLDEST_AGE_SECONDS = "agent.queue.oldest_age_seconds";
    public static final String AGENT_QUEUE_RUNNING = "agent.queue.running";
    public static final String AGENT_REVIEW_PRACTICE_COVERAGE_ELIGIBLE = "agent.review.practice.coverage.eligible";
    public static final String AGENT_REVIEW_PRACTICE_COVERAGE_EVALUATED = "agent.review.practice.coverage.evaluated";
    public static final String AGENT_REVIEW_PRACTICE_COVERAGE_RATIO = "agent.review.practice.coverage.ratio";
    public static final String LLM_PROXY_DURATION = "llm.proxy.duration";
    public static final String MENTOR_ATTACH_DURATION = "mentor.attach.duration";
    public static final String MENTOR_ATTACH_FAILURE = "mentor.attach.failure";
    public static final String MENTOR_FRAME_PARSE_ERROR = "mentor.frame.parse.error";
    public static final String MENTOR_RING_BUFFER_DROPPED = "mentor.ring.buffer.dropped";
    public static final String MENTOR_SEND_FRAME_BYTES = "mentor.send.frame.bytes";
    public static final String MENTOR_SEND_REJECTED = "mentor.send.rejected";
    public static final String MENTOR_SESSION_ACTIVE = "mentor.session.active";
    public static final String MENTOR_SESSION_EVICTION = "mentor.session.eviction";
    public static final String MENTOR_SESSION_LIFETIME = "mentor.session.lifetime";
    public static final String MENTOR_SESSION_SUBSCRIBERS_AT_CLOSE = "mentor.session.subscribers.at.close";
    public static final String MENTOR_SUBSCRIBER_DROPPED = "mentor.subscriber.dropped";
    public static final String MENTOR_SUBSCRIBER_ERROR = "mentor.subscriber.error";
    public static final String MENTOR_TURN_COMPLETED = "mentor.turn.completed";
    public static final String MENTOR_TURN_COST_USD = "mentor.turn.cost.usd";
    public static final String MENTOR_TURN_DURATION = "mentor.turn.duration";
    public static final String MENTOR_TURN_STARTED = "mentor.turn.started";
    public static final String SANDBOX_EXECUTIONS = "sandbox.executions";
    public static final String SANDBOX_EXECUTION_DURATION = "sandbox.execution.duration";
    public static final String SANDBOX_RECONCILER_DURATION = "sandbox.reconciler.duration";
    public static final String SANDBOX_RECONCILER_ORPHANED = "sandbox.reconciler.orphaned";
    public static final String SANDBOX_RECONCILER_SWEEPS = "sandbox.reconciler.sweeps";
    public static final String WORKER_CONTROL_CHANNEL_CONNECTED = "worker.control.channel.connected";
    public static final String WORKER_CONTROL_FRAMES_DROPPED = "worker.control.frames.dropped";
    public static final String WORKER_CONTROL_FRAMES_RECEIVED = "worker.control.frames.received";
    public static final String WORKER_CONTROL_FRAMES_SENT = "worker.control.frames.sent";
    public static final String WORKER_CONTROL_RECONNECTS = "worker.control.reconnects";
    public static final String WORKER_DRAIN_ACTIVE = "worker.drain.active";
    public static final String WORKER_HEARTBEATS_FAILED = "worker.heartbeats.failed";
    public static final String WORKER_HEARTBEATS_SENT = "worker.heartbeats.sent";
    public static final String WORKER_LIVENESS_HEARTBEAT_FAILURES = "worker.liveness.heartbeat.failures";

    private AgentMetrics() {}
}
