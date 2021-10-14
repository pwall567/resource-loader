# resource-loader

[![Build Status](https://travis-ci.com/pwall567/resource-loader.svg?branch=main)](https://travis-ci.com/github/pwall567/resource-loader)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/static/v1?label=Kotlin&message=v1.5.20&color=7f52ff&logo=kotlin&logoColor=7f52ff)](https://github.com/JetBrains/kotlin/releases/tag/v1.5.20)
[![Maven Central](https://img.shields.io/maven-central/v/io.kjson/resource-loader?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.kjson%22%20AND%20a:%22resource-loader%22)

Resource loading mechanism

## Background

There are many contexts in which the use of relative URLs to locate resources is important.
In a web page, any `href` attribute is resolved relative to the URL of the page itself (_i.e._ if the document's URL is
`http://kjson.io/testing/abc1.html`, then `href="abc2.html"` resolves to `http://kjson.io/testing/abc2.html`).
In JSON Schema, a `$ref` property is resolved relative to the containing schema.
The use of relative references means that bundles of pages can be packaged together and retain their ability to
cross-reference each other.

The JVM standard libraries provide functionality for accessing resources by means of URLs, but they do not meet all the
requirements of a system based on relative URLs.
This library provides a means of locating resources in files, via `http` or `https` addresses, from the classpath or
from any JAR file.

## Quick Start

For anyone wishing to use this library, a good example is the `XMLLoader` class in the tests in the project source.
The `XMLLoader` may be instantiated with a `File`, a `Path` or a `URL` &ndash; the default is equivalent to a `File`
specifying the current directory in which the process is running.
(At this point it's worth noting that the `Class.getResource()` function from the standard JVM library returns a `URL`
so the `XMLLoader` can be used to access resources on the classpath just as easily as those in the local file system, or
on an HTTP server.)

If the resource is a file (as opposed to a directory) the `load()` function will retrieve the content &ndash; in the
case of `XMLLoader`, parsing it as XML.

The `resolve(String)` function will resolve the string relative to the base `XMLLoader`, and return a new `XMLLoader`;
this can then be used to load the actual resource, and also may be used to locate other resources relative to itself.

More documentation to follow...

## Dependency Specification

The latest version of the library is 1.0, and it may be obtained from the Maven Central repository.

### Maven
```xml
    <dependency>
      <groupId>io.kjson</groupId>
      <artifactId>resource-loader</artifactId>
      <version>1.0</version>
    </dependency>
```
### Gradle
```groovy
    implementation 'io.kjson:resource-loader:1.0'
```
### Gradle (kts)
```kotlin
    implementation("io.kjson:resource-loader:1.0")
```

Peter Wall

2021-10-07
