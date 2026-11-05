--- Create the Notifications Service "Last Successful Runtime" Table
CREATE TABLE last_successful_runtime
(
    resource_type VARCHAR(255)            NOT NULL,
    node_id       VARCHAR(255)            NOT NULL,
    timestamp     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT last_successful_runtime_pk PRIMARY KEY (resource_type, node_id)
);
