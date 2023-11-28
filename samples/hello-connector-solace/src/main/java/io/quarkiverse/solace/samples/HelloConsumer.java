package io.quarkiverse.solace.samples;

import java.nio.charset.Charset;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import io.quarkus.logging.Log;

@ApplicationScoped
public class HelloConsumer {

    /**
     * Publish a simple string from using TryMe in Solace broker and you should see the message printed
     *
     * @param p
     */
    @Incoming("hello-in")
    void consume(byte[] p) {
        Log.infof("Received message: %s", new String(p, Charset.defaultCharset()));
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
