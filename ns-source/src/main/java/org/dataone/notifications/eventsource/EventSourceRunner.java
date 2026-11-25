package org.dataone.notifications.eventsource;

import org.apache.commons.configuration2.Configuration;
import org.dataone.notifications.NsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/**
 * Runs each configured event source once, then closes the shared processor.
 */
public class EventSourceRunner {
    private static final Logger log = LoggerFactory.getLogger(EventSourceRunner.class);
    private static final String SOURCES_KEY = "ns.source.eventsources";

    public static void main(String[] args) throws Exception {
        run(NsConfig.getConfig());
    }

    static void run(Configuration config) throws Exception {
        try (AutoCloseable ignored = SubscriptionProcessor::closeInstance) {
            Set<String> names = new TreeSet<>();
            Iterator<String> keys = config.getKeys(SOURCES_KEY);
            while (keys.hasNext()) {
                String key = keys.next();
                if (!key.startsWith(SOURCES_KEY + ".")) {
                    continue;
                }
                String suffix = key.substring(SOURCES_KEY.length() + 1);
                int separator = suffix.indexOf('.');
                names.add(separator < 0 ? suffix : suffix.substring(0, separator));
            }
            if (names.isEmpty()) {
                throw new IllegalArgumentException("At least one " + SOURCES_KEY + " is required");
            }

            Exception failures = null;
            boolean interrupted = false;
            for (String name : names) {
                String className = null;
                try {
                    className = config.getString(SOURCES_KEY + "." + name + ".className");
                    if (className == null || className.isBlank()) {
                        throw new IllegalArgumentException("Missing className for event source " + name);
                    }
                    Class<? extends EventSource> sourceClass =
                        Class.forName(className).asSubclass(EventSource.class);
                    EventSource source = sourceClass.getConstructor().newInstance();
                    log.info("Polling event source {} ({})", name, className);
                    source.poll();
                } catch (Exception | LinkageError e) {
                    log.error("Event source {} ({}) failed", name, className, e);
                    if (failures == null) {
                        failures = new Exception("One or more event sources failed");
                    }
                    failures.addSuppressed(e);
                    if (e instanceof InterruptedException) {
                        interrupted = true;
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            if (failures != null) {
                throw failures;
            }
        }
    }
}
