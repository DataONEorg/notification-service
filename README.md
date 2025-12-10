# Notification Service

- **Authors**: Last, First (ORCID); ...
- **License**: [Apache 2](http://opensource.org/licenses/Apache-2.0)
- [Package source code on GitHub](https://github.com/DataONEorg/reponame)
- [**Submit Bugs and feature requests**](https://github.com/DataONEorg/reponame/issues)
- Contact us: support@dataone.org
- [DataONE discussions](https://github.com/DataONEorg/dataone/discussions)

Notification Service provides a notification system for datasets and portals, to notify both portal owners/editors and the community about events such as downloads, views, citations, derived products, new datasets added to a portal, reminders to update a portal, etc.

DataONE in general, and notification-service in particular, are open source, community projects. We [welcome contributions](./CONTRIBUTING.md) in many forms, including code, graphics, documentation, bug reports, testing, etc.

Use the [DataONE discussions](https://github.com/DataONEorg/dataone/discussions) to discuss these contributions with us.

## Documentation

Documentation is a work in progress, and can be found here in the README

## API Usage Examples

Example API interactions, using curl:

```shell
# BASE_URL: the base URL of the notification service instance you're using
# TOKEN: a valid JWT token from the CN used by the installation under test
#
BASE_URL="https://notifications.test.dataone.org"
TOKEN="your-jwt-token-here"

# Subscribe user authenticated with jwt $TOKEN, to update-notifications for
# the dataset identified by {pid}
#
$ curl --request POST "${BASE_URL}/notifications/datasetChanges/{pid}" \
       --header "Authorization: Bearer $TOKEN"  |  jq

# Get a list of subscriptions for user authenticated with jwt $TOKEN:
#
$ curl --request GET "${BASE_URL}/notifications/datasetChanges" \
       --header "Authorization: Bearer $TOKEN"  |  jq

# Unsubscribe user authenticated with jwt $TOKEN, from notifications for the
# dataset identified by {pid}
#
$ curl --request DELETE "${BASE_URL}/notifications/datasetChanges/{pid}" \
       --header "Authorization: Bearer $TOKEN"  |  jq
```

> [!TIP]
> You can get an auth token by logging into a metacat instance that uses the same CN as the installation being tested. For example:
> - If it's a Production notification service, it should use `https://cn.dataone.org/cn/v2`; get a token from any prod metacat, e.g. [arcticdata.io](https://arcticdata.io/catalog)
> - If it's a test/development notification service, it will likely use  `https://cn-stage-2.test.dataone.org/cn/v2`; get a token from the [nceas dev metacat](https://dev.nceas.ucsb.edu/data)

## Getting Started - Running `Notification Service` Yourself

> [!NOTE]
> Notification Service requires a pre-existing PostgreSQL database (deployed either within or outside a Kubernetes cluster). We recommend CloudNative PG. Installing CNPG Operator is beyond the scope of this document, but the process is easy - see the [DataONE K8s Cluster documentation](https://github.com/DataONEorg/k8s-cluster/blob/main/postgres/postgres.md#cloudnativepg-operator-installation). Once the oprator is running, a Postrges cluster can be created using the [dataone-cnpg Helm chart](https://github.com/DataONEorg/dataone-cnpg/pkgs/container/charts%2Fcnpg). Both of these are one-time setup steps.

The simplest way to try out the notification service is to use the Helm chart to deploy
in a Kubernetes cluster. For example, assuming you are using [Rancher Desktop](https://rancherdesktop.io/), and have installed [ingress-nginx](https://kubernetes.github.io/ingress-nginx/deploy/#quick-start) and a CNPG cluster on your local machine (see note above), it's easy to install the latest Helm chart:

```shell
helm upgrade --install ns --debug -n notify --create-namespace \
    -f ./helm/examples/values-dev-cluster-ns-example.yaml \
    oci://ghcr.io/dataoneorg/charts/notifications
```

> [!CAUTION]
> This is a simplified process, for evaluation purposes only, and is not intended for production use, since it uses defaults for the namespace, release name and secret credentials.
>
> For production use, you should set credentials appropriately, and may need to override more of the values.yaml settings.

## Developer Guide

This section is for developers who want to build, modify, or test the application, or run it locally on their development machine.

### Jakarta EE
Notification Service uses the Jakarta EE framework. Jakarta EE is the latest version of what was formerly Oracle's Java Enterprise Edition (originally J2EE). It has now been moved to the Eclipse Foundation, where it is maintained as open source software.

Here are some useful links for those unfamiliar with Jakarta EE:
- [Jakarta EE 10](https://jakarta.ee/release/10/)
- [Jakarta EE 10 API](https://jakarta.ee/specifications/platform/10/apidocs/)
- [Jakarta EE REST Service
  Tutorial](https://jakarta.ee/learn/starter-guides/how-to-build-a-restful-web-service/)

## Development build

This is a java application, built using the Maven build tool.

> [!NOTE]
> **Prerequisites:**
> The build and tests require:
> 1.  Java 21. It can be downloaded from [Adoptium](https://adoptium.net/temurin/releases?version=21&os=any&arch=any). You can also set Java 21 as the default for only the current directory, using the simple, lightweight [jenv](https://www.jenv.be/) tool. This allows you to set global and local Java versions, and switch between them easily.
> 2. [Maven v3.9+](https://maven.apache.org/download.cgi).

```shell
$ mvn clean package  [ -DskipTests ]
```

### Running the Tests

The tests and their resources all reside in the `src/test/java` directory. They fall into three categories, and it is important to distinguish between them and adhere to their respective conventions:

### Unit Tests

Unit tests are intended to test **individual classes and methods**.

> [!IMPORTANT]
> Unit tests must have all their dependencies mocked out. They should **NEVER** require a running instance of the application, or other components like a database.

Unit tests are named `*Test.java`, and are run using the Maven `test` goal:

```shell
mvn clean test
```

### Integration Tests

Integration tests are intended to test **the integration and interactions of multiple classes or modules**.

> [!IMPORTANT]
> Integration tests should **NEVER** require a running instance of the application (See [Smoke Tests](#smoke-tests)). They may, however, rely on other components (such as a database), which are provided and managed by the test framework. (For example, see [TestUtils::getTestDb](./src/test/java/org/dataone/notifications/util/TestUtils.java), which uses [TestContainers](https://www.testcontainers.org/)). This enables the test suite to be self-contained, so it will run in CI/CD pipelines.

Integration tests are named `*IT.java`, and are run using the Maven `verify` goal (which also runs the unit tests first):

```shell
mvn clean verify
```

### Smoke Tests

Smoke tests are intended to be **executed against a running instance of the application** (e.g. in production), to verify that it is working as expected, after installation or upgrade.

Smoke tests are named `*SmokeIT.java`, and are run using the Maven `verify` goal, with the following additional command-line args:
- `-PsmokeTest`: this tells Maven to run only the smoke tests.
- `-DBASE_URL`: the base URL of the notification service instance you're using
- `-DTOKEN`: a valid JWT token from the CN used by the installation under test (see [API Usage Examples](#api-usage-examples) for details of how to get a token)

```shell
# Can set this as an environment variable, to maintain secrecy:
export TOKEN="your-jwt-token-here"

mvn verify -PsmokeTest -DBASE_URL="https://notifications.test.dataone.org" -DTOKEN="$TOKEN"
```

### Building and Running on a Localhost Web Application Server

> [!NOTE]
> **Prerequisites:**
> In addition to the Java and maven versions listed above, you will need:
> 1. [Apache TomEE](https://tomee.apache.org) MicroProfile v10+ (or another web application server that is fully compliant with [Jakarta EE](#jakarta-ee) 10.
 Tomcat version 10 is NOT yet fully compliant with Jakarta EE 10, so for the time being, it is recommended to use **Apache TomEE**, which is an Apache-maintained combination of Tomcat and the additional libraries needed to support Jakarta EE. TomEE is available in different "flavors"; choose the `MicroProfile` version.)
> 2. A running PostgreSQL database. This can easily be started in a container, using the provided script:
>
> ```shell
> ./scripts/docker-run-db.sh
> ```

Build with maven and copy the war file to your TomEE webapps directory:

```shell
$ mvn clean package -DskipTests

$ cp ./target/notifications-[VERSION].war $TOMEE_HOME/webapps
```
...and (re)start TomEE. You can then visit the URL:
http://localhost:8080/notifications/metrics/ping, which should return `{"status":"ok"}`. You can also validate that the service is working correctly by running the [smoke tests](#smoke-tests).

## License
```
Copyright [2024] [Regents of the University of California]

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Acknowledgements
Work on this package was supported by:

- DataONE Network
- Arctic Data Center: NSF-PLR grant #2042102 to M. B. Jones, A. Budden, M. Schildhauer, and
  J. Dozier

Additional support was provided for collaboration by the National Center for Ecological Analysis and
Synthesis, a Center funded by the University of California, Santa Barbara, and the State of
California.

<a href="https://dataone.org">
<img src="https://user-images.githubusercontent.com/6643222/162324180-b5cf0f5f-ae7a-4ca6-87c3-9733a2590634.png"
  alt="DataONE_footer" style="width:44%;padding-right:5%;">
</a>
<a href="https://www.nceas.ucsb.edu">
<img src="https://www.nceas.ucsb.edu/sites/default/files/2020-03/NCEAS-full%20logo-4C.png"
  alt="NCEAS_footer" style="width:44%;padding-top:3%;padding-bottom:3%; background-color: white;">
</a>
