# Indico Connector (Read-Only) for midPoint

Connector for **Evolveum midPoint** that reads event registrations from **Indico 3.3.8** through the HTTP Export API. The project keeps the lightweight ConnId style of the original Koha connector but focuses on read-only personas/registrations (`SearchOp`, `SchemaOp`, `TestOp`).

## ✨ Highlights
- **Event registrations as `__ACCOUNT__` objects**: exposes registrant ID, email, person names, state, payment/check-in flags and the parent event identifier.
- **Authentication flexibility**: supports Indico API tokens (preferred) or the legacy API key + HMAC signature workflow.
- **Robust HTTP client**: Java 11 `HttpClient` with configurable timeouts, TLS trust options and exponential backoff for 429/5xx.
- **Safe JSON mapper**: resilient Jackson mapping with trace logging hooks and optional enrichment for nested person data.

## 📋 Requirements
- **Java 11** or newer.
- **Apache Maven 3.8+**.
- Access to an **Indico 3.3.8** instance with the HTTP Export API enabled.

## 🚀 Installation
1. Build the connector:
   ```bash
   mvn clean package
   ```
2. Copy the produced JAR to midPoint:
   ```bash
   cp target/connector-indico-0.0.1.jar $MIDPOINT_HOME/var/icf-connectors/
   ```
3. Restart midPoint so it picks up the new bundle.

## ⚙️ Example midPoint configuration
```xml
<connectorConfiguration>
  <icfc:configurationProperties
    xmlns:icfc="http://midpoint.evolveum.com/xml/ns/public/connector/icf-1/connector-schema-3"
    xmlns:cfg="http://midpoint.evolveum.com/xml/ns/public/connector/icf-1/bundle/connector-indico/com.identicum.connectors.indico">
    <cfg:serviceAddress>https://indico.example.edu</cfg:serviceAddress>
    <cfg:authStrategy>TOKEN</cfg:authStrategy>
    <cfg:apiToken>
      <t:clearValue>YOUR_TOKEN</t:clearValue>
    </cfg:apiToken>
    <cfg:pageSize>200</cfg:pageSize>
    <cfg:defaultEventId>12345</cfg:defaultEventId>
    <cfg:trustAllCertificates>false</cfg:trustAllCertificates>
  </icfc:configurationProperties>
</connectorConfiguration>
```

For legacy API key deployments, swap the `authStrategy` to `API_KEY`, and set `cfg:apiKey` / `cfg:apiSecret` instead of the token.

## 🧭 Operations
- `SearchOp`: Retrieves registrations for a given Indico event. Supports filtering by `__UID__` (registration id) or `email`. Event id must be supplied either through the filter (`eventId` attribute), operation options, or `defaultEventId` in the configuration.
- `SchemaOp`: Publishes the read-only schema for registrant attributes.
- `TestOp`: Performs a lightweight call to `/export/categories.json?limit=1` to verify connectivity and authentication.
- `CreateOp` / `UpdateOp` / `DeleteOp` / `SyncOp`: Not supported in v0.0.1; Indico Export API is read-only. Any attempt to invoke them should be avoided or wrapped externally.

## 🏗️ Architecture overview
```
midPoint → IndicoConnector → RegistrationService → IndicoHttpClient → Indico HTTP Export API
                                   ↓
                             RegistrationMapper (JSON → ConnectorObject)
```
- **IndicoConnector**: Implements `Connector`, `SearchOp`, `SchemaOp`, and `TestOp`. Handles filter translation, pagination and error mapping.
- **IndicoConfiguration**: Defines validated connector properties (service URL, authentication, timeouts, retry policy, pagination).
- **IndicoAuthenticator**: Adds the appropriate headers/query parameters for API token or API key/secret authentication.
- **IndicoHttpClient**: Wrapper around `java.net.http.HttpClient` with retry logic and TLS configuration options.
- **RegistrationService**: Calls `/export/registrants/{eventId}.json` and exposes simple paging helpers.
- **RegistrationMapper**: Converts JSON payloads into `ConnectorObject` instances while tolerating optional fields.

## 🐛 Troubleshooting & Logging
Enable detailed logging in midPoint by adding to `logback.xml`:
```xml
<logger name="com.identicum.connectors.indico" level="DEBUG"/>
```
Use `TRACE` to capture sanitized JSON payloads for diagnostics. Secrets (tokens/keys) are never logged.

## 📄 Limitations (v0.0.1)
- Read-only access to registrations; writes require custom Indico plugins or internal APIs.
- Only the `/export/registrants` endpoint is covered. Optional person enrichment uses data available in the export payload.
- Pagination relies on Indico's export response metadata; extremely large events may require tuning `pageSize` and retry parameters.

## ⚖️ License
[Apache License 2.0](LICENSE)
