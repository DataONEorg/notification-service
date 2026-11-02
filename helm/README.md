# Notification Service Helm Chart README

## Prerequisites for Running in a Kubernetes ("K8s") Cluster

1. **PostgreSQL database:** We recommend using [CloudNative PG](https://cloudnative-pg.io/docs). Installing CNPG Operator is beyond the scope of this document, but the process is easy - see the [DataONE K8s Cluster documentation](https://github.com/DataONEorg/k8s-cluster/blob/main/operators/postgres/postgres.md#cloudnativepg-operator-installation). Once the operator is running, a Postgres cluster can be created using the [dataone-cnpg Helm chart](https://github.com/DataONEorg/dataone-cnpg/pkgs/container/charts%2Fcnpg). Both of these are one-time setup steps.
2. **RabbitMQ:** We recommend having the RabbitMQ Cluster Operator pre-installed (see the [DataONE k8s-cluster documentation](https://github.com/DataONEorg/k8s-cluster/blob/main/operators/rabbitmq/rabbitmq-operator.md#rabbitmq-cluster-operator-installationupgrade)), which allows the helm chart to deploy and configure its own RabbitMQ instance. Alternatively, you may provide your own K8s RabbitMQ instance, which requires setting `rmq.rmqOperator.enabled: false` in helm/values.yaml, and configuring the `rmq.rmqConnection.*` connection parameters to point to your own instance.
3. **Traefik Ingress Controller:** See the [Traefik documentation](https://doc.traefik.io/traefik/getting-started/kubernetes/) or the [DataONE k8s-cluster documentation](https://github.com/DataONEorg/k8s-cluster/blob/main/control-plane/control-plane.md#ingress-controller) for installation instructions.

## Installing and Upgrading the Helm Chart

Once you have deployed the above prerequisites, it's easy to install or upgrade to the latest Helm chart:

```shell
helm upgrade --install ns -n notify --create-namespace \
      oci://ghcr.io/dataoneorg/charts/notifications --debug \
          -f ./helm/examples/values-rancher-desktop-ns-example.yaml
          # values-rancher-desktop-ns-example.yaml assumes you are using Rancher
          # Desktop. Other examples are available in the helm/examples directory
```

> [!CAUTION]
> This is a simplified process, for evaluation purposes only, and is not intended for production use, since it uses defaults for the namespace, release name and secret credentials.
>
> For production use, you should set credentials appropriately, and may need to override more of the values.yaml settings - see the [Parameters](#parameters) section.

## Parameters

### Application-Specific Properties for notification-service

| Name                                            | Description                                                                  | Value                                     |
| ----------------------------------------------- | ---------------------------------------------------------------------------- | ----------------------------------------- |
| `ns.logLevel`                                   | The log level for the notification service.                                  | `warn`                                    |
| `ns.auth.api.baseUrl`                           | The base URL for the authentication API (typically the CN)                   | `https://cn.dataone.org/cn/v2`            |
| `ns.auth.api.authenticate`                      | The endpoint for authentication (CN uses '/diag/subject')                    | `/diag/subject`                           |
| `ns.auth.api.userAgent`                         | The User-Agent string to use when making requests to the auth API            | `DataONE notification-service/1.0`        |
| `ns.auth.api.connectTimeoutMilliSec`            | The timeout limit for opening a connection to the auth API                   | `10000`                                   |
| `ns.auth.api.readTimeoutMilliSec`               | The timeout limit for reading data from the auth API                         | `20000`                                   |
| `ns.database.dbName`                            | The name of the database used by the notification service.                   | `notifications`                           |
| `ns.database.existingSecret`                    | (REQUIRED) Basic Auth Secret name for DB username & password                 | `nsdb-cnpg-app`                           |
| `ns.database.jdbcHost`                          | (REQUIRED) database connection hostname (postgresql k8s service)             | `nsdb-cnpg-rw`                            |
| `ns.database.jdbcPort`                          | The port for the database connection.                                        | `5432`                                    |
| `replicaCount`                                  | The number of replicas for the notification web-api service deployment.      | `1`                                       |
| `image.repository`                              | image repository                                                             | `ghcr.io/dataoneorg/notification-service` |
| `image.pullPolicy`                              | image pull policy                                                            | `IfNotPresent`                            |
| `image.tag`                                     | Overrides the image tag. Will default to the chart appVersion if set to ""   | `""`                                      |
| `serviceAccount.create`                         | Should a service account be created to run notifications?                    | `false`                                   |
| `serviceAccount.annotations`                    | Annotations to add to the service account                                    | `{}`                                      |
| `serviceAccount.name`                           | The name to use for the service account.                                     | `""`                                      |
| `podAnnotations`                                | Annotations to add to the pod                                                | `{}`                                      |
| `podLabels`                                     | Labels to add to the pod                                                     | `{}`                                      |
| `podSecurityContext`                            | Security context for the pod                                                 | `{}`                                      |
| `securityContext`                               | Security context for the container                                           | `{}`                                      |
| `ingress.enabled`                               | Enable or disable the ingress                                                | `true`                                    |
| `ingress.className`                             | ClassName of the ingress provider in your cluster                            | `traefik`                                 |
| `ingress.annotations`                           | Annotations to add to the ingress;                                           | `{}`                                      |
| `ingress.corsEnabled`                           | Enable or disable CORS functionality for the ingress                         | `true`                                    |
| `ingress.hosts[0].host`                         | The hostname for the ingress                                                 | `localhost`                               |
| `ingress.hosts[0].paths[0].path`                | The path for the ingress host                                                | `/`                                       |
| `ingress.hosts[0].paths[0].pathType`            | The path type for the ingress host                                           | `Prefix`                                  |
| `ingress.hosts[0].paths[1].path`                | The path for the ingress host                                                | `/notifications/v1/private`               |
| `ingress.hosts[0].paths[1].pathType`            | The path type for the ingress host                                           | `Prefix`                                  |
| `ingress.hosts[0].paths[1].backend.serviceName` | The service name for the backend                                             | `dev-null-service`                        |
| `ingress.hosts[0].paths[1].backend.servicePort` | The service port for the backend                                             | `8080`                                    |
| `ingress.tls`                                   | List of TLS configurations for the ingress                                   | `[]`                                      |
| `startupProbe.enabled`                          | Enable readinessProbe for notifications container                            | `true`                                    |
| `startupProbe.enabled`                          | Enable startupProbe for the notifications container                          | `true`                                    |
| `startupProbe.httpGet.path`                     | The url path to probe during startup                                         | `/`                                       |
| `startupProbe.httpGet.port`                     | The containerPort to probe                                                   | `8080`                                    |
| `startupProbe.successThreshold`                 | Min consecutive successes for probe to be successful                         | `1`                                       |
| `startupProbe.failureThreshold`                 | No. of consecutive failures before the container restarted                   | `75`                                      |
| `startupProbe.periodSeconds`                    | Interval (in seconds) between startup checks                                 | `2`                                       |
| `startupProbe.timeoutSeconds`                   | Timeout (in seconds) for each startup check                                  | `2`                                       |
| `livenessProbe.enabled`                         | Enable livenessProbe for notifications container                             | `true`                                    |
| `livenessProbe.httpGet.path`                    | The url path to probe.                                                       | `/notifications/v1/metrics/ping`          |
| `livenessProbe.httpGet.port`                    | The named containerPort to probe                                             | `http`                                    |
| `livenessProbe.periodSeconds`                   | Period seconds for readinessProbe                                            | `1`                                       |
| `livenessProbe.timeoutSeconds`                  | Timeout seconds for readinessProbe                                           | `5`                                       |
| `livenessProbe.successThreshold`                | Min consecutive successes for probe to be successful                         | `1`                                       |
| `livenessProbe.failureThreshold`                | No. consecutive failures before container marked unhealthy                   | `100`                                     |
| `readinessProbe.enabled`                        | Enable readinessProbe for notifications container                            | `true`                                    |
| `readinessProbe.httpGet.path`                   | The url path to probe.                                                       | `/notifications/v1/metrics/ping`          |
| `readinessProbe.httpGet.port`                   | The named containerPort to probe                                             | `http`                                    |
| `readinessProbe.initialDelaySeconds`            | Initial delay seconds for readinessProbe                                     | `5`                                       |
| `readinessProbe.periodSeconds`                  | Period seconds for readinessProbe                                            | `1`                                       |
| `readinessProbe.timeoutSeconds`                 | Timeout seconds for readinessProbe                                           | `5`                                       |
| `readinessProbe.successThreshold`               | Min consecutive successes for probe to be successful                         | `1`                                       |
| `readinessProbe.failureThreshold`               | No. consecutive failures before container marked unhealthy                   | `50`                                      |
| `autoscaling.enabled`                           | Enable or disable autoscaling for the notification service deployment        | `false`                                   |
| `autoscaling.minReplicas`                       | Minimum number of replicas for the notification service deployment           | `1`                                       |
| `autoscaling.maxReplicas`                       | Maximum number of replicas for the notification service deployment           | `10`                                      |
| `autoscaling.targetCPUUtilizationPercentage`    | Target CPU utilization percentage for the notification service deployment    | `80`                                      |
| `autoscaling.targetMemoryUtilizationPercentage` | Target memory utilization percentage for the notification service deployment | `80`                                      |
| `extraVolumes`                                  | Optional additional volumes to be added to the pod.                          | `[]`                                      |
| `extraVolumeMounts`                             | Optional additional volume mounts to be added to the container.              | `[]`                                      |
| `service.port`                                  | The port for the notification service.                                       | `8080`                                    |
| `imagePullSecrets`                              | Optional list of image pull secrets (if image repo requires auth)            | `[]`                                      |
| `fullnameOverride`                              | Override the full name of the chart                                          | `""`                                      |
| `nameOverride`                                  | Override the name of the chart                                               | `""`                                      |
| `nodeSelector`                                  | Node labels for pod assignment                                               | `{}`                                      |
| `tolerations`                                   | Tolerations for pod assignment                                               | `[]`                                      |
| `affinity`                                      | Affinity for pod assignment                                                  | `{}`                                      |
| `resources.limits.cpu`                          | Maximum CPU the nsconsumer container may use                                 | `200m`                                    |
| `resources.limits.memory`                       | Maximum memory the nsconsumer container may use                              | `256Mi`                                   |
| `resources.requests.cpu`                        | CPU requested for the nsconsumer container                                   | `100m`                                    |
| `resources.requests.memory`                     | Memory requested for the nsconsumer container                                | `128Mi`                                   |

### Tomcat Configuration

| Name                                            | Description                                                               | Value                                                                                         |
| ----------------------------------------------- | ------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| `tomcat.extraCatalinaOpts`                      | Extra JVM options to pass to Tomcat. SEE IMPORTANT NOTES BELOW:           | `["-XX:MaxRAMPercentage=75"]`                                                                 |
| `tomcat.jmxEnabled`                             | Enable JMX for Tomcat, to inspect JVM usage of CPU, memory, etc.          | `false`                                                                                       |
| `tomcat.jmxPort`                                | The port to use for JMX connections. IMPORTANT: If you change this...     | `9010`                                                                                        |
| `tomcat.jmxCatalinaOpts`                        | Tomcat JVM options for enabling JMX                                       | `see values.yaml`                                                                             |
| `tomcat.remoteDebugEnabled`                     | Enable remote debugging for Tomcat                                        | `false`                                                                                       |
| `tomcat.remoteDebugPort`                        | The port to use for remote debugging connections                          | `5005`                                                                                        |
| `nsconsumer.replicaCount`                       | The number of replicas for the nsconsumer deployment                      | `1`                                                                                           |
| `nsconsumer.nameOverride`                       | String to partially override the nsconsumer chart name                    | `""`                                                                                          |
| `nsconsumer.fullNameOverride`                   | String to fully override the nsconsumer resource names                    | `""`                                                                                          |
| `nsconsumer.image.repository`                   | The nsconsumer image repository                                           | `ghcr.io/dataoneorg/nsconsumer`                                                               |
| `nsconsumer.image.pullPolicy`                   | The nsconsumer image pull policy                                          | `IfNotPresent`                                                                                |
| `nsconsumer.resources.limits.cpu`               | Maximum CPU the nsconsumer container may use                              | `200m`                                                                                        |
| `nsconsumer.resources.limits.memory`            | Maximum memory the nsconsumer container may use                           | `256Mi`                                                                                       |
| `nsconsumer.resources.requests.cpu`             | CPU requested for the nsconsumer container                                | `100m`                                                                                        |
| `nsconsumer.resources.requests.memory`          | Memory requested for the nsconsumer container                             | `128Mi`                                                                                       |
| `nsconsumer.serviceAccount.create`              | Should a service account be created for nsconsumer?                       | `false`                                                                                       |
| `nsconsumer.serviceAccount.name`                | The name of the service account to use for nsconsumer                     | `""`                                                                                          |
| `nsconsumer.readinessProbe.enabled`             | Enable the readinessProbe for the nsconsumer container                    | `true`                                                                                        |
| `nsconsumer.readinessProbe.exec.command`        | The command that checks the consumer's readiness file is fresh (<30s old) | `["/bin/sh","-c","test $(($(date +%s) - $(stat -c %Y /tmp/rmq-consumer-readiness))) -lt 30"]` |
| `nsconsumer.readinessProbe.initialDelaySeconds` | Seconds to wait after container start before probing                      | `60`                                                                                          |
| `nsconsumer.readinessProbe.periodSeconds`       | How often (in seconds) to perform the probe                               | `10`                                                                                          |
| `nsconsumer.readinessProbe.timeoutSeconds`      | Seconds after which the probe times out                                   | `5`                                                                                           |
| `nsconsumer.livenessProbe.enabled`              | Enable the livenessProbe for the nsconsumer container                     | `true`                                                                                        |
| `nsconsumer.livenessProbe.exec.command`         | The command that checks the consumer's readiness file is fresh (<30s old) | `["/bin/sh","-c","test $(($(date +%s) - $(stat -c %Y /tmp/rmq-consumer-readiness))) -lt 30"]` |
| `nsconsumer.livenessProbe.initialDelaySeconds`  | Seconds to wait after container start before probing                      | `90`                                                                                          |
| `nsconsumer.livenessProbe.periodSeconds`        | How often (in seconds) to perform the probe                               | `10`                                                                                          |
| `nsconsumer.livenessProbe.timeoutSeconds`       | Seconds after which the probe times out                                   | `5`                                                                                           |
| `nsconsumer.extraEnvVars`                       | Optional additional env vars to be added to the container.                | `{}`                                                                                          |

### Configuration for the SMTP server used to send email notifications

| Name                            | Description                                                | Value      |
| ------------------------------- | ---------------------------------------------------------- | ---------- |
| `emailUtil.host`                | The hostname of the SMTP server to use for sending emails. | `ADD_HOST` |
| `emailUtil.port`                | The port of the SMTP server to use for sending emails.     | `1025`     |
| `emailUtil.auth.enabled`        | Enable or disable SMTP authentication.                     | `true`     |
| `emailUtil.auth.existingSecret` | Secret holding SMTP credentials. This won't be             | `""`       |

### RabbitMQ Connection Configuration

| Name                               | Description                                       | Value                       |
| ---------------------------------- | ------------------------------------------------- | --------------------------- |
| `rmq.rmqConnection.existingSecret` | Name of Secret holding RMQ credentials (REQUIRED) | `ns-rmq-4-1-3-default-user` |
| `rmq.rmqConnection.hostname`       | RMQ hostname (REQUIRED)                           | `ns-rmq-4-1-3`              |
| `rmq.rmqConnection.port`           | RMQ amqp port                                     | `5672`                      |
| `rmq.rmqConnection.virtualHost`    | RMQ virtual host                                  | `/`                         |
| `rmq.rmqConnection.queueName`      | RMQ queue name                                    | `dataone.notifications`     |
| `rmq.rmqConnection.prefetchCount`  | RMQ prefetch count                                | `25`                        |
| `rmq.rmqConnection.username`       | The username needed for rabbitmq access.          | `""`                        |
| `rmq.rmqConnection.rmqUserKey`     | key for rabbitmq username in existing secret      | `username`                  |
| `rmq.rmqConnection.rmqPasswordKey` | key for rabbitmq password in existing secret      | `password`                  |

### RabbitMQ Operator Configuration

| Name                                                        | Description                                                        | Value       |
| ----------------------------------------------------------- | ------------------------------------------------------------------ | ----------- |
| `rmq.rmqOperator.enabled`                                   | Enable or disable the RabbitMQ Operator installation               | `true`      |
| `rmq.rmqOperator.nameOverride`                              | REQUIRED partial override for resourcename used for pods, PVCs etc | `rmq-4-1-3` |
| `rmq.rmqOperator.replicaCount`                              | Number of desired rabbitmq pods (always use an odd number)         | `1`         |
| `rmq.rmqOperator.resources.limits.cpu`                      | Maximum CPU the nsconsumer may use                                 | `500m`      |
| `rmq.rmqOperator.resources.limits.memory`                   | Maximum memory the nsconsumer may use                              | `500Mi`     |
| `rmq.rmqOperator.resources.requests.cpu`                    | CPU requested for the nsconsumer                                   | `800m`      |
| `rmq.rmqOperator.resources.requests.memory`                 | Memory requested for the nsconsumer                                | `500Mi`     |
| `rmq.rmqOperator.persistence.storageClassName`              | Storage class name for rabbitmq data PV                            | `""`        |
| `rmq.rmqOperator.persistence.size`                          | Size of persistent volume for rabbitmq data                        | `10Gi`      |
| `rmq.rmqOperator.envConfig`                                 | Additional rabbitmq environment variable settings                  | `{}`        |
| `rmq.rmqOperator.additionalConfig.disk_free_limit.relative` | set_disk_free_limit mem_relative                                   | `1.0`       |


## Creating and Publishing a New Helm Chart Version

> [!TIP]
> You do not need to package the helm chart or container images yourself, as they are already built and published to the [GitHub Container Registry (GHCR)](https://github.com/orgs/DataONEorg/packages?repo_name=notification-service) each time changes are pushed to GitHub, by [this GitHub Action](../.github/workflows/ci-pipeline.yaml).

To package and publish the chart manually:

```shell
# Package the chart
helm package -u helm
```

...which creates a zipped tar file named `dataone-indexer-<version>.tgz`, where `<version>` reflects
the chart `version:` in [`Chart.yaml`](./Chart.yaml). The chart can then be published to the
correct ghcr.io URL, after logging in with a suitable GitHub PAT:

```shell
# Log in to GHCR with a GitHub Personal Access Token (PAT) that has the "write:packages" scope
GITHUB_PAT="your-own-secret-GitHub-Personal-Access-Token-goes-here"
echo $GITHUB_PAT | docker login ghcr.io -u <your-gh-username> --password-stdin

# Publish the chart to GHCR
helm push notification-service-<version>.tgz oci://ghcr.io/dataoneorg/charts
```

> [!IMPORTANT]
> Always use `/charts` in the oci url, to distinguish helm charts from docker images.
