# ns-common shared resources

This module contains shared code and default resources used across the notification-service project.

## Global Defaults
- `src/main/resources/properties.yaml` — default application properties used by `NsConfig`.
- `src/main/resources/log4j2.yaml` — default logging configuration used for dev and testing.

Runtime overrides
- External configuration file can be provided via environment variable `NS_PROPERTIES_YAML_PATH`.
- For production use in Kubernetes, prefer mounting a ConfigMap with a custom `properties.yaml` and
  setting `NS_PROPERTIES_YAML_PATH` to that path.
- For logging, mount a ConfigMap with a custom `log4j2.yaml` and set `-Dlog4j2.configurationFile=/etc/path/log4j2.yaml`.

Tests
- A small smoke test ensures the defaults are available on the classpath and `NsConfig` can load them.
