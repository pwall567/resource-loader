# Change Log

The format is based on [Keep a Changelog](http://keepachangelog.com/).

## [6.0] - 2025-01-12
### Changed
- `Resource`, `ResourceLoader`: changed to make better use of JVM URL features
- `ResourceLoader`: minor changes to filters
- `pom.xml`, tests: converted to `should-test` library
- `pom.xml`: updated Ktor dependency (used in tests)
- tests: dynamically create JAR for testing

## [5.3] - 2024-11-20
### Changed
- `ResourceLoader`: add `baseURL`, along with function `load(String)` which uses it, and function `load(URL)`
- `Cache`: changed to allow nullable values

## [5.2] - 2024-11-08
### Changed
- `ResourceLoader`: re-worked `ConnectionFilter` mechanism, added `PrefixRedirectionFilter`
- `Resource`, `ResourceLoader`: changed to accommodate Windows path format

## [5.1] - 2024-08-06
### Changed
- `ResourceLoader`: added `RedirectionFilter`
- `pom.xml`: added null implementation of `slf4j` (test); pinned version of `commons-codec` (also test)

## [5.0] - 2024-07-24
### Changed
- `ResourceLoader`: removed `checkHTTP()` function; replaced with `ConnectionFilter` mechanism (including
  `AuthorizationFilter`)

## [4.3] - 2024-07-11
### Added
- `build.yml`, `deploy.yml`: converted project to GitHub Actions
### Changed
- `pom.xml`: updated Kotlin version to 1.9.24
### Removed
- `.travis.yml`

## [4.2] - 2024-01-22
### Changed
- `Resource`, `ResourceLoader`: moved functions from `Resource` to `ResourceLoader` to allow them to be overridden by
  implementing classes
- `ResourceLoaderException`: added `cause`
- `ResourceDescriptor`: added `eTag`
### Removed
- `ResourceRecursionException`: unused since version 4.0

## [4.1] - 2024-01-03
### Changed
- `Resource`: added `toString()`

## [4.0] - 2023-12-11
### Added
- `ResourceLoader`: new version
### Changed
- `Resource`: renamed from `ResourceLoader`, major changes
- `pom.xml`: updated Kotlin and dependency version
### Removed
- `Loader`: no longer used

## [3.3] - 2023-05-22
### Changed
- `pom.xml`: updated dependency version

## [3.2] - 2023-05-07
### Changed
- `ResourceDescriptor`: modified to avoid what appears to be an obscure Kotlin or ktor bug
- `pom.xml`: updated dependencies

## [3.1] - 2023-05-04
### Changed
- `pom.xml`: updated dependencies, updated Kotlin to 1.7.21

## [3.0] - 2022-12-16
### Added
- `Loader`: general interface for `ResourceLoader`
- `Cache`: general-purpose cache
- `HTTPHeader`: class for parsing HTTP(s) header
### Changed
- `ResourceLoader`: added caching of results
- `ResourceDescriptor`: added URL to descriptor

## [2.0] - 2022-02-07
### Changed
- `ResourceLoader`: added recursion check
### Added
- `ResourceRecursionException`

## [1.0] - 2021-10-14
### Added
- all files: new
