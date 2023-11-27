package io.quarkiverse.solace.util;

import java.util.concurrent.CompletionStage;

import com.solace.messaging.MessagingService;
import com.solace.messaging.PubSubPlusClientException;
import com.solace.messaging.publisher.OutboundMessage;
import com.solace.messaging.publisher.PersistentMessagePublisher;
import com.solace.messaging.resources.Topic;

import io.quarkiverse.solace.SolaceConnectorIncomingConfiguration;
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

    public CompletionStage<PersistentMessagePublisher.PublishReceipt> handle(SolaceInboundMessage<?> message,
            SolaceConnectorIncomingConfiguration ic) {
        OutboundMessage outboundMessage = outboundErrorMessageMapper.mapError(this.solace.messageBuilder(),
                message.getMessage(),
                ic);

        return Uni.createFrom().<PersistentMessagePublisher.PublishReceipt> emitter(e -> {
            try {
                if (ic.getPersistentErrorMessageWaitForPublishReceipt().orElse(false)) {
                    publisher.publish(outboundMessage, Topic.of(errorTopic), e);
                } else {
                    publisher.publish(outboundMessage, Topic.of(errorTopic));
                    e.complete(null);
                }
            } catch (Throwable t) {
                e.fail(t);
            }
        }).subscribeAsCompletionStage();
    }

    @Override
    public void onPublishReceipt(PersistentMessagePublisher.PublishReceipt publishReceipt) {
        UniEmitter<PersistentMessagePublisher.PublishReceipt> uniEmitter = (UniEmitter<PersistentMessagePublisher.PublishReceipt>) publishReceipt
                .getUserContext();
        PubSubPlusClientException exception = publishReceipt.getException();
        if (exception != null) {
            uniEmitter.fail(exception);
        } else {
            uniEmitter.complete(publishReceipt);
        }
    }
}
