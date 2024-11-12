# resource-loader

[![Build Status](https://github.com/pwall567/resource-loader/actions/workflows/build.yml/badge.svg)](https://github.com/pwall567/resource-loader/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/static/v1?label=Kotlin&message=v1.9.24&color=7f52ff&logo=kotlin&logoColor=7f52ff)](https://github.com/JetBrains/kotlin/releases/tag/v1.9.24)
[![Maven Central](https://img.shields.io/maven-central/v/io.kjson/resource-loader?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.kjson%22%20AND%20a:%22resource-loader%22)

Resource loading mechanism

## Background

There are many contexts in which the use of relative URLs to locate resources is important.
In a web page, any `href` attribute is resolved relative to the URL of the page itself (_i.e._ if the document&rsquo;s
URL is `http://kjson.io/testing/abc1.html`, then `href="abc2.html"` resolves to `http://kjson.io/testing/abc2.html`).
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

```kotlin
object XMLLoader : ResourceLoader<Document>() {

    override val defaultExtension: String = "xml"

    override val defaultMIMEType: String = "text/xml"

    override fun load(rd: ResourceDescriptor): Document = rd.inputStream.use { inputStream ->
        val inputSource = InputSource(inputStream)
        inputSource.systemId = rd.url.toString()
        rd.charsetName?.let { inputSource.encoding = it }
        getDocumentBuilder().parse(inputSource)
    }

    private val documentBuilderFactory: DocumentBuilderFactory by lazy {
        DocumentBuilderFactory.newInstance()
    }

    private fun getDocumentBuilder(): DocumentBuilder = documentBuilderFactory.newDocumentBuilder()

}
```

`XMLLoader` extends the abstract base class `ResourceLoader`, specifying the result type to be the XML `Document` class,
  and providing two overriding values and one function (only the function is required; the values are optional):
- `defaultExtension`: the default extension to be used (in this case &ldquo;`xml`&rdquo;)
- `defaultMIMEType`: the default MIME type
- `load`: a function to load a resource using the information provided in a `ResourceDescriptor` (which includes an
  `InputStream`, the URL and other details about the resource if available)

## More Detail

The `ResourceLoader` is capable of handling resources from three source types, each with its own type of URL, and each
with its own set of complications:
1. A classic network resource, with a URL starting with `http:` or `https:`
2. A file in the local file system, identified by a `File` or a `Path`, or located using a `file:` URL
3. A file in a JAR, identified by a `jar:` URL

The `resource` function of `ResourceLoader` will accept a <span title="java.net.URL">`URL`</span>, a
<span title="java.io.File">`File`</span> or a <span title="java.nio.file.Path">`Path`</span>, and return a `Resource`.
The `Resource` may then be used to load the resource, or as a basis for resolving a relative address.

(At this point it's worth noting that the `Class.getResource()` function from the standard JVM library returns a `URL`
so the `XMLLoader` can be used to access resources on the classpath just as easily as those in the local file system, or
on an HTTP(S) server.)

If the resource is a file (as opposed to a directory) the `load()` function will retrieve the content &ndash; in the
case of a `Resource` obtained from the `XMLLoader`, parsing it as XML.

The `resolve(String)` function will resolve the string relative to the base `Resource`, and return a new `Resource`;
this can then be used to load the actual resource, and also may be used to locate other resources relative to itself.

More documentation to follow...

## Filters

Regardless of the way in which a `Resource` is obtained, access to the underlying resource will be by means of the URL,
and filters may be used to modify, replace or block the connection.

To add a filter:
```kotlin
    resourceLoader.addConnectionFilter { if (it !is HttpURLConnection || it.url.host in acceptableHosts) it else null }
```

The signature of the filter is `(URLConnection) -> URLConnection?`, and there are, in effect, four options for the
return value:
1. The input value may be returned unmodified.
2. The input `URLConnection` may be modified (for example, by adding headers to an `HttpURLConnection`) and returned.
3. A new `URLConnection` may be returned in place of the original (for example, substituting a file-based
   `URLConnection` for an `HttpURLConnection`).
4. A return value of `null` will cause the connection to be vetoed; this may be used to ensure that external references
   in included files may be blocked from accessing resources from untrusted sites (see the example above).

### Authorization Filter

The `AuthorizationFilter` allows a single request header to be added to the connection.
If multiple headers are required, multiple filters may be used.

A convenience function will add an `AuthorizationFilter`:
```kotlin
    resourceLoader.addAuthorizationFilter(
        host = "*.example.com",
        headerName = "Authorization",
        headerValue = accessToken,
    )
```
This will cause an "`Authorization`" header to be added to all requests to hosts with names ending with
&ldquo;`.example.com`&rdquo; (standard wildcard rules apply &ndash; `?` matches a single character and `*` matches zero
or more characters).

### Redirection Filter

The `RedirectionFilter` allows requests to a nominated host to be redirected to a substitute location.
When used in conjunction with an `AuthorizationFilter`, the `RedirectionFilter` must be added first.

Again, a convenience function will add the filter:
```kotlin
    resourceLoader.addRedirectionFilter(
        fromHost = "example.com",
        fromPort = -1, // -1 means unspecified
        toHost = "localhost",
        toPort = 8080,
    )
```
This will cause all requests to "`example.com`" to be redirected to "`localhost:8080`".

### Prefix-Based Redirection Filter

The `PrefixRedirectionFilter` allows requests to be redirected based on the prefix substring of the URL.
This may be used, for example, to redirect external HTTP(S) calls to the local filesystem.

Again, a convenience function will add the filter:
```kotlin
    resourceLoader.addRedirectionFilter(
        fromPrefix = "https://example.com/api/schema/",
        toPrefix = localDirectory.absoluteFile.toURI().toString(),
    )
```
This will cause all requests to addresses starting with `https://example.com/api/schema/` to be redirected to a location
in the local filesystem described by `localDirectory` (a `java.io.File`).

## Dependency Specification

The latest version of the library is 5.2, and it may be obtained from the Maven Central repository.

### Maven
```xml
    <dependency>
      <groupId>io.kjson</groupId>
      <artifactId>resource-loader</artifactId>
      <version>5.2</version>
    </dependency>
```
### Gradle
```groovy
    implementation 'io.kjson:resource-loader:5.2'
```
### Gradle (kts)
```kotlin
    implementation("io.kjson:resource-loader:5.2")
```

Peter Wall

2024-08-13
