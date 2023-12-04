package io.quarkiverse.solace.samples;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;

@ApplicationScoped
public class HelloConsumer {

    /**
     * Publish a simple string from using TryMe in Solace broker and you should see the message printed
     *
     * @param p
     */
    @Incoming("hello-in")
    void consume(JsonObject p) {
        Log.infof("Received message: %s", p.toString());
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
