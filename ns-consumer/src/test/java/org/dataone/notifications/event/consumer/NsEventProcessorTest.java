package org.dataone.notifications.event.consumer;

import org.dataone.notifications.event.NsEventMessage;
import org.dataone.notifications.event.NsEventMessage.NsEvent;
import org.dataone.notifications.event.notifier.NotificationListener;
import org.dataone.notifications.exception.NotificationDeliveryException;
import org.dataone.notifications.exception.UnsupportedResourceTypeException;
import org.dataone.notifications.resource.SubscriptionResourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NsEventProcessor}. Listeners are instantiated reflectively by class
 * name, so {@link MockedConstruction} is used to substitute Mockito mocks for the stub listener
 * classes below.
 */
@ExtendWith(MockitoExtension.class)
class NsEventProcessorTest {

    private static final NsEvent EVENT = new NsEvent(
        "subscriber-1", "subscribed-pid", "new-pid", SubscriptionResourceType.datasetChanges);

    @Test
    void constructor_nullClassNames_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new NsEventProcessor(null));
    }

    @Test
    void constructor_emptyClassNames_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new NsEventProcessor(new String[0]));
    }

    @Test
    void process_dispatchesDeserializedEventToAllListeners() throws Exception {
        try (MockedConstruction<FirstListener> first = mockConstruction(FirstListener.class);
             MockedConstruction<SecondListener> second = mockConstruction(SecondListener.class)) {

            NsEventProcessor processor = new NsEventProcessor(
                new String[]{FirstListener.class.getName(), SecondListener.class.getName()});
            processor.process(json(EVENT));

            assertEquals(1, first.constructed().size());
            assertEquals(1, second.constructed().size());
            verify(first.constructed().get(0)).handleNotification(EVENT);
            verify(second.constructed().get(0)).handleNotification(EVENT);
        }
    }

    @Test
    void constructor_skipsInvalidClassNames_andRegistersValidOnes() throws Exception {
        try (MockedConstruction<FirstListener> first = mockConstruction(FirstListener.class)) {

            NsEventProcessor processor = new NsEventProcessor(new String[]{
                "org.example.DoesNotExist",          // ClassNotFoundException
                String.class.getName(),              // not a NotificationListener
                FirstListener.class.getName()});
            processor.process(json(EVENT));

            assertEquals(1, first.constructed().size());
            verify(first.constructed().get(0)).handleNotification(EVENT);
        }
    }

    @Test
    void process_unsupportedResourceType_continuesToNextListener() throws Exception {
        try (MockedConstruction<FirstListener> first = mockConstruction(
                FirstListener.class, (mock, ctx) -> when(mock.handleNotification(any()))
                    .thenThrow(new UnsupportedResourceTypeException("unsupported")));
             MockedConstruction<SecondListener> second = mockConstruction(SecondListener.class)) {

            NsEventProcessor processor = new NsEventProcessor(
                new String[]{FirstListener.class.getName(), SecondListener.class.getName()});
            processor.process(json(EVENT));

            verify(first.constructed().get(0)).handleNotification(EVENT);
            verify(second.constructed().get(0)).handleNotification(EVENT);
        }
    }

    @Test
    void process_deliveryFailure_continuesToNextListener() throws Exception {
        try (MockedConstruction<FirstListener> first = mockConstruction(
                FirstListener.class, (mock, ctx) -> when(mock.handleNotification(any()))
                    .thenThrow(new NotificationDeliveryException("failed", null)));
             MockedConstruction<SecondListener> second = mockConstruction(SecondListener.class)) {

            NsEventProcessor processor = new NsEventProcessor(
                new String[]{FirstListener.class.getName(), SecondListener.class.getName()});
            processor.process(json(EVENT));

            verify(first.constructed().get(0)).handleNotification(EVENT);
            verify(second.constructed().get(0)).handleNotification(EVENT);
        }
    }

    @Test
    void process_malformedBody_throwsJacksonException() {
        try (MockedConstruction<FirstListener> ignored = mockConstruction(FirstListener.class)) {
            NsEventProcessor processor =
                new NsEventProcessor(new String[]{FirstListener.class.getName()});

            assertThrows(JacksonException.class, () -> processor.process("not json"));
        }
    }

    private static String json(NsEvent event) {
        return new ObjectMapper().writeValueAsString(new NsEventMessage(event));
    }

    /** Stub listener; behavior is replaced by Mockito via {@link MockedConstruction}. */
    public static class FirstListener implements NotificationListener {
        @Override
        public String handleNotification(NsEvent event) {
            throw new AssertionError("should be mocked");
        }
    }

    /** Stub listener; behavior is replaced by Mockito via {@link MockedConstruction}. */
    public static class SecondListener implements NotificationListener {
        @Override
        public String handleNotification(NsEvent event) {
            throw new AssertionError("should be mocked");
        }
    }
}
