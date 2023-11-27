package io.quarkiverse.solace.util;

import java.util.concurrent.CompletionStage;

import com.solace.messaging.receiver.AcknowledgementSupport;

import io.quarkiverse.solace.incoming.SolaceInboundMessage;
import io.smallrye.mutiny.Uni;

public class SolaceAckHandler {

    private final AcknowledgementSupport ackSupport;

    public SolaceAckHandler(AcknowledgementSupport ackSupport) {
        this.ackSupport = ackSupport;
    }

    public CompletionStage<Void> handle(SolaceInboundMessage<?> msg) {
        return Uni.createFrom().voidItem()
                .invoke(() -> ackSupport.ack(msg.getMessage()))
                .runSubscriptionOn(msg::runOnMessageContext)
                .subscribeAsCompletionStage();
    }
}
