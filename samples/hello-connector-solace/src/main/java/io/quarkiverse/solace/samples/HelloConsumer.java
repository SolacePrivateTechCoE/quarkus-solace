package io.quarkiverse.solace.samples;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.reactive.messaging.*;

import io.quarkiverse.solace.incoming.SolaceInboundMessage;
import io.quarkiverse.solace.outgoing.SolaceOutboundMetadata;
import io.quarkus.logging.Log;

@ApplicationScoped
public class HelloConsumer {
    /**
     * Publish a simple string from using TryMe in Solace broker and you should see the message published to topic
     *
     * @param p
     */
    //    @Incoming("hello-in")
    //    @Outgoing("hello-out")
    //    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    //    Message<?> consumeAndPublish(SolaceInboundMessage<?> p) {
    //        Log.infof("Received message: %s", new String(p.getMessage().getPayloadAsBytes(), StandardCharsets.UTF_8));
    //        SolaceOutboundMetadata outboundMetadata = SolaceOutboundMetadata.builder()
    //                //                .setApplicationMessageId("test").setDynamicDestination("test/topic/" + person.name)
    //                .createPubSubOutboundMetadata();
    //        Message<?> outboundMessage = Message.of(p.getPayload(), Metadata.of(outboundMetadata), () -> {
    //            CompletableFuture completableFuture = new CompletableFuture();
    //            p.ack();
    //            completableFuture.complete(null);
    //            return completableFuture;
    //        }, (throwable) -> {
    //            CompletableFuture completableFuture = new CompletableFuture();
    //            p.nack(throwable, p.getMetadata());
    //            completableFuture.complete(null);
    //            return completableFuture;
    //        });
    //        return outboundMessage;
    //    }

    /**
     * Publish a simple string from using TryMe in Solace broker and you should see the message published to dynamic destination
     * topic
     *
     * @param p
     */
    @Incoming("hello-in")
    @Outgoing("dynamic-destination-out")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    Message<?> consumeAndPublishToDynamicTopic(SolaceInboundMessage<?> p) {
        Log.infof("Received message: %s", new String(p.getMessage().getPayloadAsBytes(), StandardCharsets.UTF_8));
        SolaceOutboundMetadata outboundMetadata = SolaceOutboundMetadata.builder()
                .setApplicationMessageId("test").setDynamicDestination("hello/foobar/" + p.getMessage().getSenderId()) // make sure senderId is available on incoming message
                .createPubSubOutboundMetadata();
        Message<?> outboundMessage = Message.of(p.getPayload(), Metadata.of(outboundMetadata), () -> {
            CompletableFuture completableFuture = new CompletableFuture();
            p.ack();
            completableFuture.complete(null);
            return completableFuture;
        }, (throwable) -> {
            CompletableFuture completableFuture = new CompletableFuture();
            p.nack(throwable, p.getMetadata());
            completableFuture.complete(null);
            return completableFuture;
        });
        return outboundMessage;
    }

    /**
     * Publish a simple string from using TryMe in Solace broker and you should receive exception
     *
     * @param p
     */
    //    @Incoming("exception-in")
    //    void consumeAndGetException(JsonObject p) {
    //        Log.infof("Received message: %s", p.toString());
    //    }

}
