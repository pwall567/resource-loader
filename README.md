# resource-loader

[![Build Status](https://travis-ci.com/pwall567/resource-loader.svg?branch=main)](https://travis-ci.com/github/pwall567/resource-loader)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/static/v1?label=Kotlin&message=v1.8.22&color=7f52ff&logo=kotlin&logoColor=7f52ff)](https://github.com/JetBrains/kotlin/releases/tag/v1.8.22)
[![Maven Central](https://img.shields.io/maven-central/v/io.kjson/resource-loader?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.kjson%22%20AND%20a:%22resource-loader%22)

Resource loading mechanism

## Background

There are many contexts in which the use of relative URLs to locate resources is important.
In a web page, any `href` attribute is resolved relative to the URL of the page itself (_i.e._ if the document's URL is
`http://kjson.io/testing/abc1.html`, then `href="abc2.html"` resolves to `http://kjson.io/testing/abc2.html`).
In JSON Schema, a `$ref` property is resolved relative to the containing schema.
The use of relative references means that bundles of resources can be packaged together and retain their ability to
cross-reference each other.

The JVM standard libraries provide functionality for accessing resources by means of URLs, but they do not meet all the
requirements of a system based on relative URLs.
This library provides a means of locating resources in files, via `http` or `https` addresses, from the classpath or
from any JAR file.

**BREAKING CHANGE:** Version 4.0 of this library makes major changes to the way this library is used; any code written
for earlier versions will require significant changes.
All of the previous functionality should be available, but the means of accessing it may be very different.

## Quick Start

For anyone wishing to use this library, a good example is the `XMLLoader` object in the tests in the project source.

`XMLLoader` extends the base class `ResourceLoader`, providing two overriding values and one function:
- `defaultExtension`: the default extension to be be used (in this case "`xml`").
- `defaultMIMEType`: the default MIME type
- `load`: a function to load a resource using the information provided in a `ResourceDescriptor` (which includes an
  `InputStream`, the URL and other details about the resource if available)

The `ResourceLoader` provides three functions to get a `Resource`, specifying a `File`, a `Path` or a `URL`.
The `Resource` may then be used to load the resource, or as a basis for resolving a relative address.

(At this point it's worth noting that the `Class.getResource()` function from the standard JVM library returns a `URL`
so the `XMLLoader` can be used to access resources on the classpath just as easily as those in the local file system, or
on an HTTP(S) server.)

If the resource is a file (as opposed to a directory) the `load()` function will retrieve the content &ndash; in the
case of a `Resource` obtained from the `XMLLoader`, parsing it as XML.

The `resolve(String)` function will resolve the string relative to the base `Resource`, and return a new `Resource`;
this can then be used to load the actual resource, and also may be used to locate other resources relative to itself.

More documentation to follow...

## Dependency Specification

The latest version of the library is 4.0, and it may be obtained from the Maven Central repository.

### Maven
```xml
    <dependency>
      <groupId>io.kjson</groupId>
      <artifactId>resource-loader</artifactId>
      <version>4.0</version>
    </dependency>
```
### Gradle
```groovy
    implementation 'io.kjson:resource-loader:4.0'
```
### Gradle (kts)
```kotlin
    implementation("io.kjson:resource-loader:4.0")
```

Peter Wall

2023-12-11
