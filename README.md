## Notification Service

- **Authors**: Last, First (ORCID); ...
- **License**: [Apache 2](http://opensource.org/licenses/Apache-2.0)
- [Package source code on GitHub](https://github.com/DataONEorg/reponame)
- [**Submit Bugs and feature requests**](https://github.com/DataONEorg/reponame/issues)
- Contact us: support@dataone.org
- [DataONE discussions](https://github.com/DataONEorg/dataone/discussions)

Notification Service provides a notification system for datasets and portals, to notify both portal
owners/editors and the community about events such as downloads, views, citations, derived products,
new datasets added to a portal, reminders to update a portal, etc.

DataONE in general, and notification-service in particular, are open source, community projects.
We [welcome contributions](./CONTRIBUTING.md) in many forms, including code, graphics,
documentation, bug reports, testing, etc.

Use the [DataONE discussions](https://github.com/DataONEorg/dataone/discussions) to discuss these
contributions with us.

## Documentation

Documentation is a work in progress, and can be found here in the README

### Jakarta EE
Jakarta EE is the latest version of what was formerly Oracle's Java Enterprise Edition (originally
J2EE). It has now been moved to the Eclipse Foundation, where it is maintained as open source
software.

Here are some useful links for those unfamiliar with Jakarta EE:
- [Jakarta EE 10](https://jakarta.ee/release/10/)
- [Jakarta EE 10 API](https://jakarta.ee/specifications/platform/10/apidocs/)
- [Jakarta EE REST Service
  Tutorial](https://jakarta.ee/learn/starter-guides/how-to-build-a-restful-web-service/)

### Web Application Server

> Jakarta EE 10 is supported only by compliant web application servers. Tomcat version 10 is NOT yet
> fully compliant with Jakarta EE 10, so for the time being, it is recommended to use
> **Apache TomEE**, which is an Apache-maintained combination of Tomcat and the additional
> libraries needed to support Jakarta EE.

TomEE can be downloaded from https://tomee.apache.org:
- Version 9.1 (Webprofile) is a Final Release that is only Jakarta 9 EE compliant, but seems to work
OK with the current v10 codebase
- Version 10.0.0-M2 (Webprofile) is a Milestone Release that is Jakarta EE 10 compliant.

## Development build

This is a java application, built using the Maven build tool.

## Usage Example

build with maven and copy the war file to your tomcat webapps directory
```shell
$ mvn clean package  [ -DskipTests ]

$ cp ./target/notifications.war $TOMCAT_HOME/webapps
```
...and restart Tomcat.

Example API interactions, using curl:
```shell
# Subscribe user authenticated with jwt $TOKEN, to update-notifications for
# the dataset identified by {pid}
#
$ curl --request POST "http://localhost:8080/notifications/datasets/{pid}" \
       --header "Authorization: Bearer $TOKEN"  |  jq

# Get a list of subscriptions for user authenticated with jwt $TOKEN:
#
$ curl --request GET "http://localhost:8080/notifications/datasets" \
       --header "Authorization: Bearer $TOKEN"  |  jq

# Unsubscribe user authenticated with jwt $TOKEN, from notifications for the
# dataset identified by {pid}
#
$ curl --request DELETE "http://localhost:8080/notifications/datasets/{pid}" \
       --header "Authorization: Bearer $TOKEN"  |  jq
```


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
