package org.dataone.notifications.api.internal.event;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
import org.dataone.notifications.resource.SubscriptionResourceType;
import org.dataone.notifications.storage.DataRepository;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrivateResourcesQueryTest extends JerseyTest {
    private DataRepository repository;

    @Override
    protected Application configure() {
        repository = mock(DataRepository.class);
        return new ResourceConfig().registerInstances(new PrivateResources(repository));
    }

    @Test
    void getSubjects_decodesPidQueryParameter() {
        String pid = "https://example.org/a+b?x=1&y=two words";
        when(repository.getSubscriptionsByPid(pid, SubscriptionResourceType.datasetChanges))
            .thenReturn(List.of());

        try (Response response = target("/private/subscriptions/datasetChanges")
            .queryParam("pid", pid)
            .request()
            .get()) {
            assertEquals(200, response.getStatus());
            verify(repository).getSubscriptionsByPid(pid, SubscriptionResourceType.datasetChanges);
        }
    }

    @Test
    void updatePid_decodesQueryParameters() {
        String oldPid = "https://example.org/old+a?x=1&y=2";
        String newPid = "https://example.org/new b";
        String subject = "http://orcid.org/0000-0002-1472-913X";
        when(repository.updateSubscriptionPid(oldPid, newPid, subject)).thenReturn(1);

        try (Response response = target("/private/subscriptions")
            .queryParam("oldPid", oldPid)
            .queryParam("newPid", newPid)
            .queryParam("subject", subject)
            .request()
            .put(Entity.json("{}"))) {
            assertEquals(200, response.getStatus());
            verify(repository).updateSubscriptionPid(oldPid, newPid, subject);
        }
    }
}
