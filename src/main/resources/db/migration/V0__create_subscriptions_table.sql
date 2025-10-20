--- Create the Notifications Service "Subscriptions" Table
CREATE TABLE subscriptions
(
    id            SERIAL PRIMARY KEY,
    resource_type VARCHAR(255) NOT NULL,
    subject       VARCHAR(512) NOT NULL,
    pid           VARCHAR(512) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_subscription UNIQUE (resource_type, pid, subject)
);
