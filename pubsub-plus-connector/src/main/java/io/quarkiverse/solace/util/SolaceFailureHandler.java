package io.quarkiverse.solace.util;

import java.util.concurrent.CompletionStage;

import org.eclipse.microprofile.reactive.messaging.Metadata;

import com.solace.messaging.MessagingService;
import com.solace.messaging.config.MessageAcknowledgementConfiguration;
import com.solace.messaging.receiver.AcknowledgementSupport;

import io.quarkiverse.solace.i18n.SolaceLogging;
import io.quarkiverse.solace.incoming.SolaceInboundMessage;
import io.smallrye.mutiny.Uni;

public class SolaceFailureHandler {

    private final String channel;
    private final AcknowledgementSupport ackSupport;

    private final MessagingService solace;

    public SolaceFailureHandler(String channel, AcknowledgementSupport ackSupport, MessagingService solace) {
        this.channel = channel;
        this.ackSupport = ackSupport;
        this.solace = solace;
    }

    public CompletionStage<Void> handle(SolaceInboundMessage<?> msg, Throwable reason, Metadata metadata, boolean ackMessage) {
        MessageAcknowledgementConfiguration.Outcome outcome;
        if (metadata != null) {
            outcome = metadata.get(SettleMetadata.class)
                    .map(SettleMetadata::getOutcome)
                    .orElseGet(() -> ackMessage ? MessageAcknowledgementConfiguration.Outcome.ACCEPTED
                            : MessageAcknowledgementConfiguration.Outcome.REJECTED /* TODO get outcome from reason */);
        } else {
            outcome = ackMessage ? MessageAcknowledgementConfiguration.Outcome.ACCEPTED
                    : MessageAcknowledgementConfiguration.Outcome.REJECTED;
        }

        SolaceLogging.log.messageNacked(channel, outcome.toString().toLowerCase());
        return Uni.createFrom().voidItem()
                .invoke(() -> ackSupport.settle(msg.getMessage(), outcome))
                .runSubscriptionOn(msg::runOnMessageContext)
                .subscribeAsCompletionStage();
    }
}
