<!--
  webflux-recipes
  -----------------
  A curated set of recipes, utilities and examples for building reactive Spring WebFlux
  applications. This README presents an overview of the repository, modules, quick-start
  instructions and other helpful pointers for contributors and users.
-->

# webflux-recipes

Comprehensive examples, utilities and lightweight libraries demonstrating best practices
for reactive applications using Spring WebFlux and Project Reactor.

Table of Contents
-----------------
- [Project Overview](#project-overview)
- [Modules](#modules)
- [Getting Started](#getting-started)
- [Build & Test](#build--test)
- [Key Concepts & Highlights](#key-concepts--highlights)
- [Examples & How to Use](#examples--how-to-use)
- [Contributing](#contributing)
- [License](#license)

Project Overview
----------------
This multi-module repository provides reusable, well-documented examples and small
libraries for common challenges in Spring WebFlux applications. It covers caching
patterns for reactive sources, scheduler helpers, custom JSON streaming with Jackson,
and configuration recipes for WebClient and R2DBC.

The project is kept intentionally compact and example-driven: each module contains
unit tests that double as executable “recipes”, showing how to use the API or
pattern in practice.

Modules
-------
The repository is divided into several modules, each focusing on a specific topic:

- webflux-cached/
  - A small collection of reactive cache helpers intended to build a Flux/Mono
    backed cache around asynchronous lookups.
  - Notable classes:
    - `io.github.d_sch.webfluxcached.common.cached.Cached`
    - `io.github.d_sch.webfluxcached.common.cache.FluxCache`
    - `io.github.d_sch.webfluxcached.common.cache.impl.FluxCacheImpl`
    - `LRUCacheMap` and the internal `SimpleLinkedQueue` for efficient eviction
  - Use case: deduplicating concurrent lookups and efficient reactive caching.

- webflux-common/
  - General-purpose utilities and functional wrappers used across modules: throwing
    functional interfaces (`ThrowingSupplier`, `ThrowingRunnable`, `ThrowingConsumer`),
    lightweight helpers and `SchedulerContext` abstractions to encapsulate
    scheduler behavior.

- webflux-config/
  - Reusable configuration examples for Spring Boot & WebFlux; contains helpers for
    R2DBC and WebClient configuration, including an `OAuth2WebClientConfiguration`.
  - Notable classes: `AbstractR2DBCConfiguration`, `OAuth2WebClientConfiguration` and
    qualifier annotations for specialized WebClient beans.

- webflux-custom-jackson-stream/
  - Utilities and streaming helpers to write JSON using Jackson directly into
    Reactor `DataBuffer` streams. This enables efficient JSON streaming from
    large datasets without materializing full objects in memory.
  - Notable classes: `JsonWriter` and `DataBufferOutputStream`.

- webflux-recipes-examples/
  - Example applications and wiring that demonstrate how to combine the modules
    in a Spring Boot WebFlux application.
  - Notable examples: `Application`, `WebClientConfiguration`, `R2DBCConfiguration`.

Key Concepts & Highlights
-------------------------
- Reactive caching & deduplication: `webflux-cached` demonstrates how to wrap
  reactive lookups with a cache layer that avoids duplicate inflight calls and
  keeps memory usage bounded using LRU eviction.
- Scheduler context isolation: `webflux-common` provides a `SchedulerContext` helper
  to isolate scheduling and avoid accidental blocking of critical event loops.
- Streaming JSON: `webflux-custom-jackson-stream` demonstrates efficient JSON
  streaming using Jackson + Reactor `DataBuffer` without intermediate copies.

Getting Started
---------------
Prerequisites
- Java 25 or later
- Maven 3.9+ (or use the wrapper scripts `./mvnw` / `mvnw.cmd`)

Build locally (Unix/MacOS):
```bash
./mvnw -T 1C clean install
```

Run a specific module's tests (e.g., `webflux-cached`):
```bash
./mvnw -pl webflux-cached test
```

Build & Test
------------
- Build: `./mvnw clean install`
- Run all tests: `./mvnw test`
- Run tests for a module: `./mvnw -pl <module> test` (e.g. `-pl webflux-custom-jackson-stream`)

Examples & How to Use
---------------------
- Look into each module's unit tests for executable examples (tests are meant as
  documentation and quick-starts):
  - `webflux-cached/src/test/java` contains `CachedTest` and `FluxCacheImplTest`
  - `webflux-custom-jackson-stream/src/test/java` contains `FluxJsonGeneratorTest`
- Example application: `webflux-recipes-examples` contains a runnable `Application`
  and configuration samples demonstrating wiring of caches, R2DBC and WebClient.

Contributing
------------
Contributions are welcome. Please follow these guidelines:

1. Fork the repository.
2. Create a feature branch: `git checkout -b feature/<name>`.
3. If adding new behavior, add unit tests and update module README if needed.
4. Run the full build: `./mvnw clean install`.
5. Create a Pull Request describing your changes.

Code style
- Use existing conventions in the repo. Prefer explicit and small-step commits.

License
-------
This project is available under the terms of the Apache 2.0 license - see
the `LICENSE` file for details.

Need help or want a walkthrough of any module? Open an issue or create a PR and
I'll be happy to help guide the addition of more examples or expand the
documentation for a particular area.

