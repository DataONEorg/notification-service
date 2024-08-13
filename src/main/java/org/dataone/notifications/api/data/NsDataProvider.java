package org.dataone.notifications.api.data;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dataone.notifications.api.resource.ResourceType;
import org.dataone.notifications.util.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A class that provides access to the data store for the notifications service.
 * {@code @ApplicationScoped} means this is a singleton bean.
 */
@ApplicationScoped
public class NsDataProvider implements DataProvider {

    private final Logger log = LogManager.getLogger(this.getClass().getName());
    private final DataSource dataSource;


    @Inject
    public NsDataProvider(DataSource dataSource) {
        log.debug("@Injected DataSource into NsDataProvider");
        this.dataSource = dataSource;
    }

    public List<String> getSubscriptions(String subject, ResourceType resourceType)
        throws NotAuthorizedException, NotFoundException {

        log.debug("Get subscriptions to {} for {}", resourceType, subject);
        validateInput(subject, resourceType);

        List<String> pids = new ArrayList<>();

        String sql = "SELECT pid FROM subscriptions WHERE resource_type=? AND subject=?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, resourceType.toString());
            statement.setString(2, subject);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    pids.add(resultSet.getString("pid"));
                }
            }
        } catch (SQLException e) {
            log.error("Database error: {} retrieving subscriptions", e.getMessage());
            throw new RuntimeException("Database error retrieving subscriptions", e);
        }
        return pids;
    }

    public Subscription addSubscription(String subject, ResourceType resourceType, String pid) {

        log.debug("Add new subscription to {}/{} for {}", resourceType, pid, subject);
        validateInput(subject, resourceType, pid);

        String sql = "INSERT INTO subscriptions (resource_type, subject, pid) VALUES (?, ?, ?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, resourceType.toString());
            statement.setString(2, subject);
            statement.setString(3, pid);
            statement.executeUpdate();
        } catch (SQLException e) {
            log.error("Database error: {} adding subscription", e.getMessage());
            throw new RuntimeException("Database error adding subscription", e);
        }
        return new Subscription(subject, resourceType, List.of(pid));
    }

    public Subscription deleteSubscriptions(
        String subject, ResourceType resourceType, List<String> pidList) {

        log.debug("Delete {} subscriptions for {}, to pids {}", resourceType, subject, pidList);
        validateInput(subject, resourceType);

        List<String> deletedPids = new ArrayList<>();

        String sql = "DELETE FROM subscriptions WHERE resource_type=? AND subject=? AND pid IN ("
            + String.join(",", Collections.nCopies(pidList.size(), "?")) + ") RETURNING pid";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, resourceType.toString());
            statement.setString(2, subject);
            for (int i = 0; i < pidList.size(); i++) {
                statement.setString(3 + i, pidList.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    deletedPids.add(resultSet.getString("pid"));
                }
            }
        } catch (SQLException e) {
            log.error("Database error: {} deleting subscriptions", e.getMessage());
            throw new RuntimeException("Database error deleting subscriptions", e);
        }
        return new Subscription(subject, resourceType, deletedPids);
    }

    private void validateInput(String subject, ResourceType resourceType) {
        if (StringUtils.isBlank(subject)) {
            log.error("Subject is null or empty");
            throw new NotAuthorizedException("Subject is null or empty");
        }
        if (resourceType == null) {
            log.error("ResourceType is null");
            throw new NotFoundException("ResourceType is null");
        }
    }

    private void validateInput(String subject, ResourceType resourceType, String pid) {

        validateInput(subject, resourceType);

        if (StringUtils.isBlank(pid)) {
            log.error("PID is null or empty");
            throw new NotFoundException("PID is null or empty");
        }
    }
}
