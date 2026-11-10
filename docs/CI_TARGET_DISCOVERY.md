# Target CI Auto-Discovery

`verify-common.yml` does not contain a fixed version matrix. It validates every direct child directory of `targets/`. Shared-code, root build, shared convention, descriptor, and manual changes build every enabled target. Changes confined to `targets/` (except `ci.properties`) build only the changed enabled targets, including source, resource, build script, and wrapper changes. Common unit tests still run in either case.

Discovery accepts `-ChangedFiles <paths>` for this selection. Without that argument it returns the full matrix. An empty selection returns `{"include":[]}` and the workflow skips target jobs. File renames are compared as deletion plus addition so both affected targets are checked. A first push without a base commit verifies the full matrix; a failed diff fails discovery rather than silently skipping validation.

## Target Descriptor

Every `targets/<loader>-<minecraft-version>/` directory must contain both `gradlew.bat` and `ci.properties`. Discovery fails when either is missing, so adding a target cannot silently bypass the shared-code verification gate.

```properties
# ci.enabled defaults to true when omitted, but set it explicitly for reviewability.
ci.enabled=true
# Required. The Temurin JDK major version used by this target's Gradle build.
ci.java=21
# Optional. Network-related Gradle failures are retried; allowed values are 1 through 5.
ci.attempts=3
```

Set `ci.enabled=false` only for a deliberately archived or temporarily unsupported target. Its descriptor and wrapper are still required and checked, but it is excluded from the CI build matrix.

## Adding A Target

1. Add `targets/<name>/` as an independent Gradle project with its own wrapper. Do not include the standalone common build in target settings. After applying the loader plugins, apply `../../gradle/target-conventions/shared.gradle` to attach the original common source directories, resources, local dependencies, and tests. Keep toolchains and loader configuration in the target, and register confirmed source differences with `commonJava { exclude 'path/to/File.java' }`; this filters only shared files, not target replacements. Apply `publish.gradle` separately for publishing. Do not add a common jar or `project(':common')` dependency, or copy shared Java files into build directories.
2. Add `ci.properties`, choosing the JDK required by that target. Set `debug_autojs_version` and `debug_rhino` in its `gradle.properties`, plus `debug_kubejs` when that loader/version has a published KubeJS build. Apply `hot-debug.gradle` after configuring the loader; list Forge remapping dependencies explicitly in `debug_support_mods` and non-mod libraries in `debug_libraries`. Run `verifyHotDebug`, then validate the live game with AutoJS `status`, `eval_js`, and `reload_scripts`.
3. Run `scripts/discover-targets.ps1` from the repository root and confirm the new target appears in the JSON matrix.
4. Build that target locally with `scripts/build-target.ps1 -Target <name>` using the declared JDK.
5. Merge the descriptor with the new target. The next shared-code CI run includes it automatically.

## Local Commands

```powershell
# Print the exact matrix CI will use.
.\scripts\discover-targets.ps1

# Build any single target without editing a hardcoded script list.
.\scripts\build-target.ps1 -Target fabric-1.20.1
```

The preset targets are examples only. `ci.properties` is the source of truth for CI eligibility, JDK selection, and retry count.

The root Gradle build also scans direct child directories of `targets/` and reads the same descriptors. It generates `buildTarget-<directory-name>` tasks for enabled targets; no version list or per-version task aliases are maintained. `-Ptarget=<name>` selects one enabled target. `-PallTargets=true` selects enabled targets whose `ci.java` is no higher than the current Gradle JVM, prints targets skipped for requiring a newer JDK, and fails if no targets qualify. This is a minimum-JDK filter, not a guarantee that an older target wrapper supports every newer JDK. Use each target independently when its wrapper requires a different JVM. Disabled targets remain independently buildable with their own wrapper.

Publishing discovers the single loader-prefixed `*_minecraft_version` key in each target's `gradle.properties`; it does not enumerate version-specific property names. Initialization discovers template class names from Java source filenames rather than listing version-specific entrypoints.
