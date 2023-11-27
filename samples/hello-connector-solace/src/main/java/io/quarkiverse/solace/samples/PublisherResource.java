package io.quarkiverse.solace.samples;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

import io.quarkiverse.solace.outgoing.SolaceOutboundMetadata;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;

@Path("/hello")
public class PublisherResource {

    @Channel("hello")
    MutinyEmitter<Person> foobar;

    @POST
    public Uni<Void> publish(Person person) {

        SolaceOutboundMetadata outboundMetadata = SolaceOutboundMetadata.builder()
                .setApplicationMessageId("test").setDynamicDestination("test/topic").createPubSubOutboundMetadata();
        Message<Person> personMessage = Message.of(person, Metadata.of(outboundMetadata));
        return foobar.sendMessage(personMessage);
    }

}
