--- Create the Notifications Service "Subscriptions" Table
CREATE TABLE subscriptions
(
    id            SERIAL PRIMARY KEY,
    resource_type VARCHAR(255),
    subject       VARCHAR(512),
    pid           VARCHAR(512)
);
