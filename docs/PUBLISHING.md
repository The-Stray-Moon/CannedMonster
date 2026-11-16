# Publishing

Each target applies `gradle/target-conventions/publish.gradle`. The shared script publishes the target's Java component to the Sighs Maven repository and derives its Maven coordinates from that target:

```text
groupId:    mod_group_id
artifactId: <mod_name>-<loader>-<minecraft-version>
version:    mod_version
```

A version containing `SNAPSHOT` is sent to `maven-snapshots`; every other version is sent to `maven-releases`. Set credentials only in the publishing shell or CI secret store:

```powershell
$env:SIGHS_PUBLISH_USER = '<username>'
$env:SIGHS_PUBLISH_PASSWORD = '<password>'
```

Build or publish one target from its own directory with the JDK declared in its `ci.properties`:

```powershell
cd targets\fabric-1.20.1
.\gradlew.bat publish
```

The generated POM intentionally omits the target build classpath. Minecraft, loader, mappings, and `common` are packaged or supplied by the runtime rather than exposed as Maven dependencies.

The shared convention script also injects the common resources through the relative path `../../common/src/main/resources`, so every published jar carries the shared assets. For the published artifact it prefers `remapJar` first, then `reobfJar`, and falls back to `jar`.

NeoForge 1.21.1 publishes a single outer service jar from `jar`. Install that complete file in `mods`; do not distribute `build/embedded/game.jar` or `build/bootstrap/fusion-asm-dev.jar` separately. The outer jar contains the ModLauncher ASM transformation service and a NeoForge candidate locator. The embedded `META-INF/cannedmonster/game.jar` contains the mod metadata, Mixins, game classes and shared assets. The old coremod service and nested bootstrap library are absent. Artifact tests check this separation. External publishing platforms or tools that only inspect root-level mod metadata have not been verified against this packaging; a successful local build is not proof of platform acceptance.
