package io.quarkiverse.solace;

public class SolaceExtensionHeaders {
    private static final String PREFIX = "scst_";

    /**
     * Indicates the name of the target destination the extension should use if they
     * choose (or have capabilities) to optimize sending output messages to
     * dynamic destinations.
     * <br>
     * <br>
     * NOTE: The extension only defines this header, but does nothing else in parsing the payload.
     * So it is up to applications to choose to use it or not. Applications should create the dynamic destination topic and pass
     * it in this header.
     * The extension will override the default destination if this header is present.
     */
    public static final String TARGET_DESTINATION = PREFIX + "targetDestination";
}
