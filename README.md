# DataFixerUpper-J8

Mojang's [DataFixerUpper](https://github.com/Mojang/DataFixerUpper) 10.0.21, repackaged to run on Java 8 and Minecraft 1.7.10.

The build:
1. Downloads upstream DFU from `libraries.minecraft.net`.
2. Redirects the Guava 21+ calls DFU makes to `com.gtnewhorizons.dfu.GuavaCompat`, which works with the Guava 17 shipped by 1.7.10.
3. Redirects DFU's slf4j logging to 1.7.10's log4j2 through `com.gtnewhorizons.dfu.DfuLogger`, since 1.7.10 has no slf4j.
4. Downgrades everything to Java 8 bytecode with [JVM Downgrader](https://github.com/unimined/JvmDowngrader), and shades its runtime stubs into `com.gtnewhorizons.dfu.jvmdg`, so the jar is self-contained.

## Usage

```kotlin
repositories { maven("https://nexus.gtnewhorizons.com/repository/public/") }
dependencies { implementation("com.github.GTNewHorizons:DataFixerUpper-J8:10.0.21-1") }
```

Or grab the jar from [Releases](https://github.com/GTNewHorizons/DataFixerUpper-J8/releases).

## Runtime requirements

The jar declares no dependencies. At runtime it only needs Guava, Gson, log4j2 and fastutil, which come with Minecraft 1.7.10 and GTNH. The JVM Downgrader stubs are shaded in, so no GTNHLib or `jvmdowngrader-java-api` is required.

Both the codec API and the data fixers work.

A DFU update fails the build if it calls a Guava method the compat class doesn't cover, or an slf4j method `DfuLogger` doesn't implement.

## Building

```sh
./gradlew build
```

The release publishes three jars in `build/libs/`:

- `DataFixerUpper-J8-<version>.jar`: the self-contained, downgraded jar with relocated JVM Downgrader stubs.
- `DataFixerUpper-J8-<version>-preshadow.jar`: the downgraded jar without the stubs, for runtimes that provide them separately.
- `DataFixerUpper-J8-<version>-sources.jar`: upstream DFU sources plus the local compatibility sources.

The patched jar before downgrading stays in `build/intermediates/`.

The version defaults to `10.0.21-1`. CI overrides it with the git tag.

CI uses the shared [GTNH workflows](https://github.com/GTNewHorizons/GTNH-Actions-Workflows): `build-and-test` on pushes and PRs, and `release-tags` on a tag push, which makes the GitHub release and publishes to the GTNH maven. `setupCIWorkspace` is a no-op task that exists only because those workflows call it.

## License

DataFixerUpper and this repackaging are MIT, see [LICENSE](LICENSE).

The jar is processed by [JVM Downgrader](https://github.com/unimined/JvmDowngrader) and calls its `java-api` stubs, which are LGPL 2.1. Its license is in [licenses/jvmdowngrader](licenses/jvmdowngrader/LICENSE.md) and is also shipped in the jar under `META-INF/licenses/`.

`GuavaCompat` adapts small parts of Guava, which is Apache 2.0, see [licenses/guava](licenses/guava/LICENSE.txt).
