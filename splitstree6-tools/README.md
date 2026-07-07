# SplitsTree6 command-line tools

A standalone, extensible tools package (mirroring megan7-tools). Thin launchers
run the SplitsTree6 tool classes -- and the application itself, via `splitstree6`
-- using the **system Java**.

    splitstree6-tools/
      tools/            the launchers: splitstree6, workflow-run, workflow-export,
                        webserver, mash-sketches, sample-trees, genome-context, bfilter-tool
      lib/
        splitstree-env  shared: finds system Java + assembles the module path
        jars/           the module path (installed layout) -- SEE NOTE

Each launcher sources `lib/splitstree-env`, then execs the system `java` with
`--module-path` + `--add-modules splitstreesix` and the tool's main class.
`splitstree6` additionally roots the GitHub-updater module when present.

## Adding a tool

Copy any launcher, change the one-line description and the final main class.
That is the whole extension surface -- everything else comes from splitstree-env.

## The one thing that is easy to get wrong: JavaFX

These tools run on the *system* JDK, which has no JavaFX, and the `splitstreesix`
module requires JavaFX -- so `lib/jars/` must be a **complete** runtime module
path INCLUDING the JavaFX jars for the running platform. (This is why the package
is built per platform: JavaFX ships platform-specific native jars.) In a Maven
checkout the tools instead use `<source>/target/*.jar` + `target/dependency/*.jar`;
make sure JavaFX is present in `dependency/` there too.

## Before use, verify against your source

- `splitstree6.main.SplitsTree6` is the application main class (must match the
  jpackage `--main-class`).
- `MIN_JAVA_VERSION` in `splitstree-env` matches your `maven.compiler.release`.
- `-Xmx` in each launcher -- set to whatever your old `SplitsTree.vmoptions` used.
