package org.dataone.notifications.messaging.consumer;

import com.rabbitmq.client.Delivery;
import org.dataone.notifications.api.resource.SubscriptionResourceType;
import org.dataone.notifications.event.NsEventMessage;
import org.dataone.notifications.exception.IgnoredResourceTypeException;
import org.dataone.notifications.exception.UnsupportedResourceTypeException;
import org.dataone.notifications.messaging.SubscriptionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import notifications.util.StringUtils;

/**
 * Processor invoked after a RabbitMQ message is consumed.
 */
public class SubscriptionMessageProcessor {
    private static final Pattern PORTAL_FORMAT_ID_PATTERN =
        Pattern.compile("https://purl\\.dataone\\.org/portals-.*");
    private static final Logger log = LoggerFactory.getLogger(SubscriptionMessageProcessor.class);
    private final ObjectMapper mapper = new ObjectMapper();

    public void process(Delivery delivery) throws IOException {

        String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
        SubscriptionEvent event;
        try {
            event = deserialize(body);

        } catch (IgnoredResourceTypeException | UnsupportedResourceTypeException e) {
            log.debug("Ignoring message because: {}. Body is: {}", e.getMessage(), body);
            return;

        } catch (IOException e) {
            log.error("Error: {}; while deserializing message: {}", e.getMessage(), body);
            throw e;
        }
        log.debug("Processing subscription event: {}", event);

        String messagesHost = System.getenv("NS_MESSAGES_HOST");
        String messagesPort = System.getenv("NS_MESSAGES_PORT");
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(
                "http://" + messagesHost + ":" + messagesPort
                    + "/notifications/v1/private/eventhandler/send?resource=" + event.resourceType()
                    + "&pid=" + event.pid())).header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("")).build();

        log.debug("Sending request to {}", request.uri());
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("Response Status Code::Body: {}::{}", response.statusCode(), response.body());
        } catch (IOException | InterruptedException e) {
            log.error("Error: {}; making request to uri: {}", e.getMessage(), request.uri());
        }
    }

    /**
     * Deserialize the message body into a SubscriptionEvent
     *
     * @param body String representation of the message body.
     * @return A SubscriptionEvent object representing the deserialized message.
     * @throws IOException                      If there is an error during deserialization.
     * @throws IgnoredResourceTypeException     If the resource type is ignored (e.g., "DATA")
     * @throws UnsupportedResourceTypeException If the resource type is unsupported.
     */
    private SubscriptionEvent deserialize(String body)
        throws IOException, IgnoredResourceTypeException, UnsupportedResourceTypeException {
        try {
            log.trace("Message body: {}", body);
            NsEventMessage.NsEvent nsEvent = mapper.readValue(body, NsEventMessage.class).nsEvent();
            SubscriptionResourceType resourceType = getResourceType(nsEvent);
            String pid = requireText(nsEvent.id(), "id");
            log.debug(
                "Deserialized message into SubscriptionEvent with resourceType: {}, pid: {}",
                resourceType, pid);
            return new SubscriptionEvent(resourceType, pid);
        } catch (IgnoredResourceTypeException | UnsupportedResourceTypeException e) {
            log.debug("Ignoring message with body: {}. Reason: {}", body, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error: {}; while deserializing message: {}", e.getMessage(), body);
            throw new IOException();
        }
    }

    /// Extracts the SubscriptionResourceType from the given parameters.
    ///
    /// `formatType` can be `RESOURCE`, `DATA`, or `METADATA`:
    ///
    /// - `DATA` can be ignored because there will always be another event for the corresponding
    /// resourceMap update, and we don't want to alert twice for the same thing.
    /// - `RESOURCE` is a resourceMap, which is always a dataset, so return
    /// `SubscriptionResourceType.datasetChanges`.
    /// - For `METADATA`:
    ///   - If `formatId` matches `https://purl.dataone.org/portals-.*`, return
    /// `SubscriptionResourceType.portalMetaChanges` (portals do not have a resourceMap).
    ///   - If a `resourceMap` field exists, ignore the event because there will be another event
    /// for the corresponding resourceMap update, and we don't want to alert twice for the same
    /// thing.
    ///   - If a `resourceMap` field does not exist, this is a science-metadata-only dataset; return
    /// `SubscriptionResourceType.datasetChanges`.
    ///
    /// References:
    ///
    /// - List of available formats: [DataONE formats](https://cn.dataone.org/cn/v2/formats)
    /// - Enumeration of available resource types:
    /// [org.dataone.notifications.api.resource.SubscriptionResourceType]
    ///
    /// @param nsEvent The nsEvent object containing the fields needed to determine the resource
    /// type
    /// @return The resource type as a SubscriptionResourceType
    /// @throws IgnoredResourceTypeException     If the resource type is ignored (e.g., "DATA")
    /// @throws UnsupportedResourceTypeException If the resource type cannot be determined.
    ///
    protected SubscriptionResourceType getResourceType(NsEventMessage.NsEvent nsEvent)
        throws IgnoredResourceTypeException, UnsupportedResourceTypeException {

        String formatType;
        String formatId;
        try {
            formatType = requireText(nsEvent.formatType(), "formatType");
            formatId = requireText(nsEvent.formatId(), "formatId");
            log.debug(
                "getResourceType() got formatType: {}, formatId: {} & resourceMap(): {}",
                formatType, formatId, nsEvent.resourceMap());
        } catch (IOException e) {
            throw new UnsupportedResourceTypeException(e.getMessage());
        }
        List<String> resourceMap = nsEvent.resourceMap();

        return switch (formatType) {
            case "DATA" -> throw new IgnoredResourceTypeException("formatType: DATA is ignored");
            case "RESOURCE" -> SubscriptionResourceType.datasetChanges;
            case "METADATA" -> {
                if (PORTAL_FORMAT_ID_PATTERN.matcher(formatId).matches()) {
                    log.debug("formatId: {} matches portal pattern -> portalMetaChanges", formatId);
                    yield SubscriptionResourceType.portalMetaChanges;
                }
                if (resourceMap != null && !resourceMap.isEmpty() && !StringUtils.isBlank(
                    resourceMap.getFirst())) {
                    log.debug("resourceMap: {} exists -> ignore to avoid duplicate", resourceMap);
                    throw new IgnoredResourceTypeException(
                        "formatType: METADATA with resourceMap is ignored");
                }
                yield SubscriptionResourceType.datasetChanges;
            }
            default ->
                throw new UnsupportedResourceTypeException("Unsupported formatType: " + formatType);
        };
    }

    private String requireText(String value, String field) throws IOException {
        if (StringUtils.isBlank(value)) {
            throw new IOException("Missing value for field: " + field);
        }
        return value;
    }
}
