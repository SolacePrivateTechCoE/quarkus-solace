package io.quarkiverse.solace.util;

import java.util.Properties;

import com.solace.messaging.config.SolaceProperties;
import com.solace.messaging.publisher.OutboundMessage;
import com.solace.messaging.publisher.OutboundMessageBuilder;
import com.solace.messaging.receiver.InboundMessage;

import io.quarkiverse.solace.SolaceConnectorIncomingConfiguration;

public class OutboundErrorMessageMapper {

    public OutboundMessage mapError(OutboundMessageBuilder messageBuilder, InboundMessage inputMessage,
            SolaceConnectorIncomingConfiguration incomingConfiguration) {
        Properties extendedMessageProperties = new Properties();

        extendedMessageProperties.setProperty(SolaceProperties.MessageProperties.PERSISTENT_DMQ_ELIGIBLE,
                Boolean.toString(incomingConfiguration.getPersistentErrorMessageDmqEligible().booleanValue()));
        messageBuilder.fromProperties(extendedMessageProperties);

        incomingConfiguration.getPersistentErrorMessageTtl().ifPresent(ttl -> {
            messageBuilder.withTimeToLive(incomingConfiguration.getPersistentErrorMessageTtl().get());
        });

        return messageBuilder.build(inputMessage.getPayloadAsBytes());
    }
}
