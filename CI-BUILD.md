# Building Nodes Military

Run `bash build.sh` from a checkout with JDK 21. The script downloads MilzzyNodes dev.5.11 and its Paper compile libraries from an immutable core commit and checks their SHA-256 digests. It compiles the addon directly against that API and runs the same regression suite used for release builds.

| Entry point | Output |
| --- | --- |
| `bash build.sh` | `dist/Nodes-Military.jar` |
| `mvn package` | `target/Nodes-Military.jar` and `dist/Nodes-Military.jar` |

For an offline build, set `NODES_JAR` and `NODES_COMPILE_LIBS` to absolute filenames. The earlier `PAPER_API`, `DEPS_DIR`, `NODES_JAR` interface also works for an already-extracted dependency directory. The shell build uses Bash, curl, Python 3, unzip and sha256sum.

The release workflow packages the committed source, downloads both uploaded assets and compares them with its build outputs before publishing the release.

[Release workflow](.github/workflows/release-military.yml) · [Compatibility checks](TEST_REPORT.md)
