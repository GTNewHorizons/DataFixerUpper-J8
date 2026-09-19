# DataFixerUpper-J8

Mojang's [DataFixerUpper](https://github.com/Mojang/DataFixerUpper) 10.0.21, repackaged to run on Java 8 and Minecraft 1.7.10.

The build:
1. Downloads upstream DFU from `libraries.minecraft.net`.
2. Redirects the Guava 21+ calls DFU makes to `com.github.hwx.dfu.GuavaCompat`, which works with the Guava 17 shipped by 1.7.10.
3. Redirects DFU's slf4j logging to 1.7.10's log4j2 through `com.github.hwx.dfu.DfuLogger`, since 1.7.10 has no slf4j.
4. Downgrades everything to Java 8 bytecode with [JVM Downgrader](https://github.com/unimined/JvmDowngrader).

## Usage

```kotlin
repositories { maven("https://jitpack.io") }
dependencies { implementation("com.github.0hwx:DataFixerUpper-J8:10.0.21-1") }
```

## Runtime requirements

The jar declares no dependencies. At runtime it needs:
- Guava, Gson, log4j2 and fastutil, which come with Minecraft 1.7.10 and GTNH.
- `jvmdowngrader-java-api` (`downgraded-8`), which GTNHLib provides.

Both the codec API and the data fixers work.

A DFU update fails the build if it calls a Guava method the compat class doesn't cover, or an slf4j method `DfuLogger` doesn't implement.

## Building

```sh
./gradlew build
```

The jar is written to `build/libs/downgraded/`.

The version defaults to `10.0.21-1`. JitPack overrides it with the git tag.

## License

DataFixerUpper and this repackaging are MIT, see [LICENSE](LICENSE).

The jar is processed by [JVM Downgrader](https://github.com/unimined/JvmDowngrader) and calls its `java-api` stubs, which are LGPL 2.1. Its license is in [licenses/jvmdowngrader](licenses/jvmdowngrader/LICENSE.md) and is also shipped in the jar under `META-INF/licenses/`.

`GuavaCompat` adapts small parts of Guava, which is Apache 2.0, see [licenses/guava](licenses/guava/LICENSE.txt).
