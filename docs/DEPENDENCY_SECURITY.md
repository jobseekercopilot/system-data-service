# Dependency security and Jackson compatibility

## Policy

The resolved runtime dependency set is scanned, not only `pom.xml`. Trivy must
inventory Java packages and the repository policy rejects unaccepted Critical
or High findings. A temporary exception requires a private tracking issue and
an expiry no more than 30 days away.

The authoritative local sequence is:

```bash
mvn -B clean verify
mvn -B dependency:copy-dependencies \
  -DincludeScope=runtime \
  -DoutputDirectory=target/dependency-scan
# Run the same pinned Trivy 0.72.0 rootfs/library scan as CI.
./scripts/verify-dependency-report.sh \
  target/trivy-dependencies.json \
  config/trivy/.trivyignore
```

The scan must use Trivy `rootfs` mode. That mode loads the Java advisory
database and inventories the materialised JAR directory. A report without Java
package coverage fails the repository policy.

## Jackson 2.21.5 decision

Spring Boot 3.5.16 is the latest published Boot 3.5 maintenance release as of
26 July 2026 and manages Jackson BOM 2.21.4. A resolved-runtime Trivy 0.72.0
scan found these Medium issues in `jackson-databind` 2.21.4:

- `CVE-2026-54515`
- `CVE-2026-59889`
- `GHSA-mhm7-754m-9p8w`

Trivy identifies 2.21.5 as a fixed version for all three. The project overrides
Spring Boot's `jackson-bom.version` property to 2.21.5 rather than overriding
Databind alone. This keeps core, Databind, datatypes and modules on the same
FasterXML-tested BOM patch line.

The post-change local evidence resolves Jackson core, Databind, both Java
datatypes and the parameter-names module to 2.21.5 (annotations remains at the
BOM's intentional 2.21 version). Maven passes all 67 tests. Trivy inventories
46 Java runtime packages and reports zero findings, including none of the three
IDs above and no Critical or High finding.

This is a narrow maintenance override:

- Spring Boot remains on supported 3.5.16 and Java 17.
- Jackson remains on Boot's selected 2.21 minor line.
- FasterXML publishes a complete 2.21.5 BOM with aligned component versions.
- Spring Boot's support policy permits patch-level third-party upgrades within
  a Boot patch line and recommends the latest supported maintenance release.
- The override can be removed when a later supported Boot 3.5 release manages
  Jackson 2.21.5 or newer within the compatible line.

Primary references:

- [Spring Boot 3.5.16 release](https://github.com/spring-projects/spring-boot/releases/tag/v3.5.16)
- [Spring Boot support and dependency policy](https://github.com/spring-projects/spring-boot/wiki/Supported-Versions)
- [Jackson 2.21.5 BOM on Maven Central](https://repo.maven.apache.org/maven2/com/fasterxml/jackson/jackson-bom/2.21.5/jackson-bom-2.21.5.pom)

Do not replace this BOM-level override with a single-artifact Databind pin or
an unreviewed scanner suppression.
