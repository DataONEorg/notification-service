package org.dataone.notifications.storage;

import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import org.dataone.notifications.api.resource.SubscriptionResourceType;
import org.slf4j.Logger;
import org.dataone.notifications.util.StringUtils;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A class that encapsulate and provide access to CRUD actions on the data store. It uses a
 * {@link DataSource} to connect to the database, and performs an initial migration if needed, via a
 * {@link NsDBMigrator}.
 */
@Singleton
@Default
public class NsDataRepository implements DataRepository {

    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private final DataSource dataSource;

    @Inject
    public NsDataRepository(DataSource source, NsDBMigrator migrator) {
        if (source != null) {
            this.dataSource = source;
        } else {
            throw new IllegalStateException("DataRepository not initialized: missing DataSource");
        }

        try (Connection connection = source.getConnection()) {
            DatabaseMetaData dbMeta = connection.getMetaData();
            log.info("Connected to database: {} {}", dbMeta.getDatabaseProductName(),
                     dbMeta.getDatabaseProductVersion());
        } catch (SQLException e) {
            log.error("* * * * * *  Database connection error: {}  * * * * * *", e.getMessage());
            throw new PersistenceException(
                "DataRepository not initialized: database connection error", e);
        }

        if (migrator != null) {
            migrator.migrate();
        } else {
            throw new IllegalStateException("DataRepository not initialized: missing DBMigrator");
        }
    }

    @Override
    public List<String> getSubscriptions(String subject, SubscriptionResourceType resourceType)
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
            throw new PersistenceException("Database error retrieving subscriptions", e);
        }
        return pids;
    }

    @Override
    public Subscription addSubscription(String subject, SubscriptionResourceType resourceType, String pid) {

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
            log.error("Database error adding subscription: {}", e.getMessage());
            throw new PersistenceException("Database error adding subscription", e);
        }
        return new Subscription(subject, resourceType, List.of(pid));
    }

    @Override
    public Subscription deleteSubscriptions(
        String subject, SubscriptionResourceType resourceType, List<String> pidList) {

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
            throw new PersistenceException("Database error deleting subscriptions", e);
        }
        return new Subscription(subject, resourceType, deletedPids);
    }

    @Override
    public List<String> getResourceTypesByPid(String subject, String pid)
        throws NotAuthorizedException, NotFoundException {

        log.debug("Get ResourceTypes subscribed to with pid {} by user {}", pid, subject);
        validateInput(pid, subject);

        List<String> pids = new ArrayList<>();

        String sql = "SELECT DISTINCT resource_type FROM subscriptions WHERE pid=? AND subject=?";

        List<String> resourceTypes = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pid);
            statement.setString(2, subject);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    resourceTypes.add(resultSet.getString("resource_type"));
                }
            }
        } catch (SQLException e) {
            log.error("Database error: {} retrieving resource types", e.getMessage());
            throw new PersistenceException("Database error retrieving resource types", e);
        }
        return resourceTypes;
    }

    @Override
    public List<Subscription> getSubscriptionsByPid(String pid, 
        SubscriptionResourceType type) throws  NotFoundException {
        log.debug("Get subscriptions to {} for pid {}", type, pid);
        validateInput(pid, type);

        List<Subscription> subscriptions = new ArrayList<>();

        String sql = "SELECT subject FROM subscriptions WHERE resource_type=? AND pid=?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.toString());
            statement.setString(2, pid);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    subscriptions.add(new Subscription(
                        resultSet.getString("subject"), 
                        type, List.of(pid)));
                }
            }
        } catch (SQLException e) {
            log.error("Database error: {} retrieving subscriptions", 
                e.getMessage());
            throw new PersistenceException(
                "Database error retrieving subscriptions", e);
        }
        return subscriptions;
    }

    private void validateInput(String subject, Object resource) {
        if (StringUtils.isBlank(subject)) {
            log.error("Subject is null or empty");
            throw new NotAuthorizedException("Subject is null or empty");
        }
        if (resource == null) {
            log.error("resource is null");
            throw new NotFoundException("resource is null");
        }
    }

    private void validateInput(String subject, 
        SubscriptionResourceType resourceType, String pid) {

        validateInput(subject, resourceType);

        if (StringUtils.isBlank(pid)) {
            log.error("PID is null or empty");
            throw new NotFoundException("PID is null or empty");
        }
    }
}
