package org.dataone.notifications.event.consumer;

import org.apache.commons.configuration2.Configuration;
import org.dataone.notifications.NsConfig;
import org.dataone.notifications.event.NsEventMessage;
import org.dataone.notifications.event.NsEventMessage.NsEvent;
import org.dataone.notifications.event.notifier.NotificationListener;
import org.dataone.notifications.exception.NotificationDeliveryException;
import org.dataone.notifications.exception.UnsupportedResourceTypeException;
import org.dataone.notifications.resource.SubscriptionResourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NsEventProcessor}. Listeners are instantiated reflectively by class name,
 * so {@link MockedConstruction} is used to substitute Mockito mocks for the stub listener classes
 * below.
 */
@ExtendWith(MockitoExtension.class)
class NsEventProcessorTest {

    private static final NsEvent EVENT = new NsEvent(
        "subscriber-1", "subscribed-pid", "new-pid", SubscriptionResourceType.datasetChanges);

    private static final String EVENT_JSON =
        new ObjectMapper().writeValueAsString(new NsEventMessage(EVENT));

    @Mock
    private Configuration config;

    @Test
    void constructor_nullClassNames_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new NsEventProcessor(null));
    }

    @Test
    void process_dispatchesDeserializedEventToAllListeners() {
        try (MockedConstruction<FirstListener> first = mockConstruction(FirstListener.class);
             MockedConstruction<SecondListener> second = mockConstruction(SecondListener.class)) {

            NsEventProcessor processor = new NsEventProcessor(nsConfig(
                new String[][] {
                    {FirstListener.listenerName, FirstListener.class.getName()},
                    {SecondListener.listenerName, SecondListener.class.getName()}
                }
            ));
            processor.process(EVENT_JSON);

            assertEquals(1, first.constructed().size());
            assertEquals(1, second.constructed().size());
            verify(first.constructed().getFirst()).handleNotification(EVENT);
            verify(second.constructed().getFirst()).handleNotification(EVENT);
        }
    }

    @Test
    void constructor_skipsInvalidClassNames_andRegistersValidOnes() {
        try (MockedConstruction<FirstListener> first = mockConstruction(FirstListener.class)) {

            NsEventProcessor processor = new NsEventProcessor(nsConfig(new String[][]{
                {"DoesNotExist", "org.example.DoesNotExist"},          // ClassNotFoundException
                {"String", String.class.getName()},                    // not a NotificationListener
                {FirstListener.listenerName, FirstListener.class.getName()}    // valid
            }));
            processor.process(EVENT_JSON);

            assertEquals(1, first.constructed().size());
            verify(first.constructed().getFirst()).handleNotification(EVENT);
        }
    }

    @Test
    void process_unsupportedResourceType_continuesToNextListener() {
        try (
            MockedConstruction<FirstListener> first = mockConstruction(
                FirstListener.class, (mock, _) -> when(mock.handleNotification(any())).thenThrow(
                    new UnsupportedResourceTypeException("unsupported")));
            MockedConstruction<SecondListener> second = mockConstruction(SecondListener.class)) {

            NsEventProcessor processor = new NsEventProcessor(
                nsConfig(new String[][]{
                    {FirstListener.listenerName, FirstListener.class.getName()},
                    {SecondListener.listenerName, SecondListener.class.getName()}
                }));
            processor.process(EVENT_JSON);

            verify(first.constructed().getFirst()).handleNotification(EVENT);
            verify(second.constructed().getFirst()).handleNotification(EVENT);
        }
    }

    @Test
    void process_deliveryFailure_continuesToNextListener() {
        try (
            MockedConstruction<FirstListener> first = mockConstruction(
                FirstListener.class, (mock, _) -> when(mock.handleNotification(any())).thenThrow(
                    new NotificationDeliveryException("failed", null)));
            MockedConstruction<SecondListener> second = mockConstruction(SecondListener.class)) {

            NsEventProcessor processor = new NsEventProcessor(
                nsConfig(new String[][]{
                    {FirstListener.listenerName, FirstListener.class.getName()},
                    {SecondListener.listenerName, SecondListener.class.getName()}
                }));
            processor.process(EVENT_JSON);

            verify(first.constructed().getFirst()).handleNotification(EVENT);
            verify(second.constructed().getFirst()).handleNotification(EVENT);
        }
    }

    @Test
    void process_malformedBody_throwsJacksonException() {
        try (MockedConstruction<FirstListener> ignored = mockConstruction(FirstListener.class)) {
            NsEventProcessor processor =
                new NsEventProcessor(
                    nsConfig(new String[][]{
                        {FirstListener.listenerName, FirstListener.class.getName()}
                    }));

            assertThrows(JacksonException.class, () -> processor.process("not json"));
        }
    }

    // Test helper to mock the configuration for the processor, returning the stub listener classes.
    // @param notifierNamesAndClasses 2D array of notifier names and class names to mock
    // example:
    // nsConfig(new String[][] {
    //     {"FirstListener", FirstListener.class.getName()},
    //     {"SecondListener", SecondListener.class.getName()},
    //     {"InvalidListener", "org.example.DoesNotExist"}
    // });
    //
    private Configuration nsConfig(String[][] notifierNamesAndClasses) {

        List<Object> names = new ArrayList<>();

        for (String[] notifierNamesAndClass : notifierNamesAndClasses) {
            String notifierName = notifierNamesAndClass[0];
            String className = notifierNamesAndClass[1];
            names.add(notifierName);
            when(config.getString(
                "ns.messaging.notifiers." + notifierName + ".className")).thenReturn(className);
        }

        when(config.getList("ns.messaging.notifiers")).thenReturn(names);

        try (MockedStatic<NsConfig> nsConfig = mockStatic(NsConfig.class)) {
            nsConfig.when(NsConfig::getConfig).thenReturn(config);
        }
        return config;
    }

    /**
     * Stub listener; behavior is replaced by Mockito via {@link MockedConstruction}.
     */
    public static class FirstListener implements NotificationListener {
        final static String listenerName = "FirstListener";

        @Override
        public String handleNotification(NsEvent event) {
            throw new AssertionError("should be mocked");
        }
    }

    /**
     * Stub listener; behavior is replaced by Mockito via {@link MockedConstruction}.
     */
    public static class SecondListener implements NotificationListener {
        final static String listenerName = "SecondListener";

        @Override
        public String handleNotification(NsEvent event) {
            throw new AssertionError("should be mocked");
        }
    }
}
