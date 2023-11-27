package io.quarkiverse.solace.incoming;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

import io.quarkiverse.solace.i18n.SolaceLogging;
import org.eclipse.microprofile.reactive.messaging.Message;

import com.solace.messaging.MessagingService;
import com.solace.messaging.PersistentMessageReceiverBuilder;
import com.solace.messaging.config.MissingResourcesCreationConfiguration.MissingResourcesCreationStrategy;
import com.solace.messaging.config.ReceiverActivationPassivationConfiguration;
import com.solace.messaging.config.ReplayStrategy;
import com.solace.messaging.receiver.DirectMessageReceiver;
import com.solace.messaging.receiver.InboundMessage;
import com.solace.messaging.receiver.PersistentMessageReceiver;
import com.solace.messaging.resources.Queue;
import com.solace.messaging.resources.TopicSubscription;

import io.quarkiverse.solace.SolaceConnectorIncomingConfiguration;
import io.quarkiverse.solace.util.SolaceAckHandler;
import io.quarkiverse.solace.util.SolaceErrorTopicPublisherHandler;
import io.quarkiverse.solace.util.SolaceFailureHandler;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.health.HealthReport;
import io.vertx.core.impl.VertxInternal;
import io.vertx.mutiny.core.Context;
import io.vertx.mutiny.core.Vertx;

public class SolaceIncomingChannel implements ReceiverActivationPassivationConfiguration.ReceiverStateChangeListener {

    private final String channel;
    private final Context context;
    private final SolaceAckHandler ackHandler;
    private final SolaceFailureHandler failureHandler;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final PersistentMessageReceiver receiver;
    private final Flow.Publisher<? extends Message<?>> stream;
    private final ExecutorService pollerThread;
    private SolaceErrorTopicPublisherHandler solaceErrorTopicPublisherHandler;

    public SolaceIncomingChannel(Vertx vertx, SolaceConnectorIncomingConfiguration ic, MessagingService solace) {
        this.channel = ic.getChannel();
        this.context = Context.newInstance(((VertxInternal) vertx.getDelegate()).createEventLoopContext());
        DirectMessageReceiver r = solace.createDirectMessageReceiverBuilder().build();
        PersistentMessageReceiverBuilder builder = solace.createPersistentMessageReceiverBuilder()
                .withMessageClientAcknowledgement()
                .withActivationPassivationSupport(this);

        ic.getPersistentSelectorQuery().ifPresent(builder::withMessageSelector);
        ic.getPersistentReplayStrategy().ifPresent(s -> {
            switch (s) {
                case "all-messages":
                    builder.withMessageReplay(ReplayStrategy.allMessages());
                    break;
                case "time-based":
                    builder.withMessageReplay(getTimeBasedReplayStrategy(ic));
                    break;
                case "replication-group-message-id":
                    builder.withMessageReplay(getGroupMessageIdReplayStrategy(ic));
                    break;
            }
        });
        if (ic.getPersistentAddSubscriptions().booleanValue()) {
            String subscriptions = ic.getSubscriptions().orElse(this.channel);
            builder.withSubscriptions(Arrays.stream(subscriptions.split(","))
                    .map(TopicSubscription::of)
                    .toArray(TopicSubscription[]::new));
        }
        switch (ic.getPersistentMissingResourceCreationStrategy()) {
            case "create-on-start":
                builder.withMissingResourcesCreationStrategy(MissingResourcesCreationStrategy.CREATE_ON_START);
                break;
            case "do-not-create":
                builder.withMissingResourcesCreationStrategy(MissingResourcesCreationStrategy.DO_NOT_CREATE);
                break;
        }

        this.receiver = builder.build(getQueue(ic));
        boolean lazyStart = ic.getClientLazyStart();
        this.ackHandler = new SolaceAckHandler(receiver);
        this.failureHandler = new SolaceFailureHandler(channel, receiver, solace);
        ic.getPersistentErrorTopic().ifPresent(et -> {
            this.solaceErrorTopicPublisherHandler = new SolaceErrorTopicPublisherHandler(solace, et);
        });

        // TODO Here use a subscription receiver.receiveAsync with an internal queue
        this.pollerThread = Executors.newSingleThreadExecutor();
        Integer realTimeout;
        final Long expiry = ic.getPersistentQueuePolledWaitTimeInMillis() != null ? ic.getPersistentQueuePolledWaitTimeInMillis() + System.currentTimeMillis() : null;
        if (expiry != null) {
            try {
                realTimeout = Math.toIntExact(expiry - System.currentTimeMillis());
                if (realTimeout < 0) {
                    realTimeout = 0;
                }
            } catch (ArithmeticException e) {
//                SolaceLogging.log.debug("Failed to compute real timeout", e);
                // Always true: expiry - System.currentTimeMillis() < timeoutInMillis
                // So just set it to 0 (no-wait) if we underflow
                realTimeout = 0;
            }
        } else {
            realTimeout = null;
        }
        Integer finalRealTimeout = realTimeout;
        this.stream = Multi.createBy().repeating()
                .uni(() -> Uni.createFrom().item(finalRealTimeout == null ?  receiver.receiveMessage() : (finalRealTimeout == 0 ? receiver.receiveMessage(0) : receiver.receiveMessage(finalRealTimeout)))
                        .runSubscriptionOn(pollerThread))
                .until(__ -> closed.get())
                .emitOn(context::runOnContext)
                .map(consumed -> new SolaceInboundMessage<>(consumed, ackHandler, failureHandler,
                        solaceErrorTopicPublisherHandler, ic))
                .plug(m -> lazyStart ? m.onSubscription().call(() -> Uni.createFrom().completionStage(receiver.startAsync()))
                        : m)
                .onFailure().retry().withBackOff(Duration.ofSeconds(1), Duration.ofSeconds(10)).atMost(3);
        if (!lazyStart) {
            receiver.start();
        }
    }

    private static Queue getQueue(SolaceConnectorIncomingConfiguration ic) {
        String queueType = ic.getPersistentQueueType();
        switch (queueType) {
            case "durable-non-exclusive":
                return Queue.durableNonExclusiveQueue(ic.getPersistentQueueName().orElse(ic.getChannel()));
            case "durable-exclusive":
                return Queue.durableExclusiveQueue(ic.getPersistentQueueName().orElse(ic.getChannel()));
            default:
            case "non-durable-exclusive":
                return ic.getPersistentQueueName().map(Queue::nonDurableExclusiveQueue)
                        .orElseGet(Queue::nonDurableExclusiveQueue);

        }
    }

    private static ReplayStrategy getGroupMessageIdReplayStrategy(SolaceConnectorIncomingConfiguration ic) {
        String groupMessageId = ic.getPersistentReplayReplicationGroupMessageId().orElseThrow();
        return ReplayStrategy.replicationGroupMessageIdBased(InboundMessage.ReplicationGroupMessageId.of(groupMessageId));
    }

    private static ReplayStrategy getTimeBasedReplayStrategy(SolaceConnectorIncomingConfiguration ic) {
        String zoneDateTime = ic.getPersistentReplayTimebasedStartTime().orElseThrow();
        return ReplayStrategy.timeBased(ZonedDateTime.parse(zoneDateTime));
    }

    public Flow.Publisher<? extends Message<?>> getStream() {
        return this.stream;
    }

    public void close() {
        closed.compareAndSet(false, true);
        receiver.terminate(3000);
    }

    public void isStarted(HealthReport.HealthReportBuilder builder) {

    }

    public void isReady(HealthReport.HealthReportBuilder builder) {

    }

    public void isAlive(HealthReport.HealthReportBuilder builder) {

    }

    @Override
    public void onStateChange(ReceiverState receiverState, ReceiverState receiverState1, long l) {

    }
}
