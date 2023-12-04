package io.quarkiverse.solace.util;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import com.solace.messaging.MessagingService;
import com.solace.messaging.PubSubPlusClientException;
import com.solace.messaging.publisher.OutboundMessage;
import com.solace.messaging.publisher.PersistentMessagePublisher;
import com.solace.messaging.publisher.PersistentMessagePublisher.PublishReceipt;
import com.solace.messaging.resources.Topic;

import io.quarkiverse.solace.SolaceConnectorIncomingConfiguration;
import io.quarkiverse.solace.i18n.SolaceLogging;
import io.quarkiverse.solace.incoming.SolaceInboundMessage;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.UniEmitter;

public class SolaceErrorTopicPublisherHandler implements PersistentMessagePublisher.MessagePublishReceiptListener {

    private final MessagingService solace;
    private final String errorTopic;
    private final PersistentMessagePublisher publisher;
    private final OutboundErrorMessageMapper outboundErrorMessageMapper;

    public SolaceErrorTopicPublisherHandler(MessagingService solace, String errorTopic) {
        this.solace = solace;
        this.errorTopic = errorTopic;

        publisher = solace.createPersistentMessagePublisherBuilder().build();
        publisher.start();
        outboundErrorMessageMapper = new OutboundErrorMessageMapper();
    }

    public CompletableFuture<PublishReceipt> handle(SolaceInboundMessage<?> message,
            SolaceConnectorIncomingConfiguration ic) {
        OutboundMessage outboundMessage = outboundErrorMessageMapper.mapError(this.solace.messageBuilder(),
                message.getMessage(),
                ic);
        //        if (ic.getConsumerQueueErrorMessageWaitForPublishReceipt().get()) {
        publisher.setMessagePublishReceiptListener(this);
        //        }
        return Uni.createFrom().<PublishReceipt> emitter(e -> {
            try {
                //                if (ic.getConsumerQueueErrorMessageWaitForPublishReceipt().get()) {
                //                    publisher.publish(outboundMessage, Topic.of(errorTopic), e);
                //                } else {
                //                    publisher.publish(outboundMessage, Topic.of(errorTopic));
                //                    e.complete(null);
                //                }
                publisher.publish(outboundMessage, Topic.of(errorTopic), e);
            } catch (Throwable t) {
                e.fail(t);
            }
        }).onFailure().retry().withBackOff(Duration.ofSeconds(1)).atMost(ic.getConsumerQueueErrorMessageMaxDeliveryAttempts())
                .subscribeAsCompletionStage();
    }

    @Override
    public void onPublishReceipt(PersistentMessagePublisher.PublishReceipt publishReceipt) {
        UniEmitter<PersistentMessagePublisher.PublishReceipt> uniEmitter = (UniEmitter<PersistentMessagePublisher.PublishReceipt>) publishReceipt
                .getUserContext();
        PubSubPlusClientException exception = publishReceipt.getException();
        if (exception != null) {
            SolaceLogging.log.publishException(this.errorTopic);
            uniEmitter.fail(exception);
        } else {
            uniEmitter.complete(publishReceipt);
        }
    }
}
