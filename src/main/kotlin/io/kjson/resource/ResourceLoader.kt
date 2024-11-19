/*
 * @(#) ResourceLoader.kt
 *
 * resource-loader  Resource loading mechanism
 * Copyright (c) 2023, 2024 Peter Wall
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.kjson.resource

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

import io.kjson.util.HTTPHeader
import io.kjson.util.Cache
import net.pwall.text.Wildcard

/**
 * The base `ResourceLoader` class.
 *
 * @author  Peter Wall
 */
abstract class ResourceLoader<T>(
    val baseURL: URL = defaultBaseURL(),
) {

    private val connectionFilters = mutableListOf<(URLConnection) -> URLConnection?>()

    open val defaultExtension: String? = null

    open val defaultMIMEType: String? = null

    /**
     * Load the resource, that is, read the external representation of the resource from the `InputStream` in the
     * [ResourceDescriptor] and return the internal form.
     */
    abstract fun load(rd: ResourceDescriptor): T

    /**
     * Get a [Resource], specifying a [File].
     */
    fun resource(resourceFile: File): Resource<T> = Resource(resourceFile.toPath(), resourceFile.toURI().toURL(), this)

    /**
     * Get a [Resource], specifying a [Path].
     */
    fun resource(resourcePath: Path): Resource<T> = Resource(resourcePath, resourcePath.toUri().toURL(), this)

    /**
     * Get a [Resource], specifying a [URL].
     */
    fun resource(resourceURL: URL): Resource<T> = Resource(derivePath(resourceURL), resourceURL, this)

    /**
     * Load the resource identified by the specified [Resource].  This function is open for extension to allow, for
     * example, caching implementations to provide a returned resource bypassing the regular mechanism.
     */
    open fun load(resource: Resource<T>): T = load(openResource(resource))

    /**
     * Load the resource identified by the specified [URL].
     */
    fun load(resourceURL: URL): T = load(resource(resourceURL))

    /**
     * Load the resource identified by an identifier string, which is resolved against the base URL.
     */
    fun load(resourceId: String): T = load(baseURL.resolve(resourceId))

    /**
     * Open a [Resource] for reading.  This function is open for extension to allow non-standard URLs to be mapped to
     * actual resources.  The result of this function is a [ResourceDescriptor], which contains an open `InputStream`
     * and all the metadata known about the resource.
     */
    open fun openResource(resource: Resource<T>): ResourceDescriptor {
        try {
            resource.resourcePath?.let { path ->
                if (!Files.exists(path) || Files.isDirectory(path))
                    throw ResourceNotFoundException(resource.resourceURL)
                return ResourceDescriptor(
                    inputStream = Files.newInputStream(path),
                    url = resource.resourceURL,
                    size = Files.size(path),
                    time = Files.getLastModifiedTime(path).toInstant(),
                )
            }
            var conn: URLConnection = resource.resourceURL.openConnection()
            for (filter in connectionFilters)
                conn = filter(conn) ?: throw ResourceLoaderException("Connection vetoed - ${resource.resourceURL}")
            return if (conn is HttpURLConnection) {
                // TODO think about adding support for ifModifiedSince / ETag
                if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND)
                    throw ResourceNotFoundException(resource.resourceURL)
                if (conn.responseCode != HttpURLConnection.HTTP_OK)
                    throw IOException("Error status - ${conn.responseCode} - ${resource.resourceURL}")
                val contentLength = conn.contentLengthLong.takeIf { it >= 0 }
                val lastModified = conn.lastModified.takeIf { it != 0L }?.let { Instant.ofEpochMilli(it) }
                val contentTypeHeader = conn.contentType?.let { HTTPHeader.parse(it) }
                val charsetName: String? = contentTypeHeader?.element()?.parameter("charset")
                val mimeType: String? = contentTypeHeader?.firstElementText()
                ResourceDescriptor(
                    inputStream = conn.inputStream,
                    url = resource.resourceURL,
                    charsetName = charsetName,
                    size = contentLength,
                    time = lastModified,
                    mimeType = mimeType,
                    eTag = conn.getHeaderField("etag"),
                )
            }
            else {
                ResourceDescriptor(
                    inputStream = conn.inputStream,
                    url = resource.resourceURL,
                )
            }
        }
        catch (rle: ResourceLoaderException) {
            throw rle
        }
        catch (e: Exception) {
            throw ResourceLoaderException("Error opening resource ${resource.resourceURL}", e)
        }
    }

    /**
     * Add the default extension to a file name or URL string.
     */
    fun addExtension(s: String): String = when {
        defaultExtension != null && s.indexOf('.', s.lastIndexOf(File.separatorChar) + 1) < 0 -> "$s.$defaultExtension"
        else -> s
    }

    /**
     * Add a connection filter for HTTP connections.
     */
    fun addConnectionFilter(filter: (URLConnection) -> URLConnection?) {
        connectionFilters.add(filter)
    }

    /**
     * Add an authorization filter for HTTP connections.
     */
    fun addAuthorizationFilter(host: String, headerName: String, headerValue: String?) {
        addConnectionFilter(AuthorizationFilter(Wildcard(host), headerName, headerValue))
    }

    /**
     * Add an authorization filter for HTTP connections (specifying a wildcarded hostname).
     */
    fun addAuthorizationFilter(hostWildcard: Wildcard, headerName: String, headerValue: String?) {
        addConnectionFilter(AuthorizationFilter(hostWildcard, headerName, headerValue))
    }

    /**
     * Add a redirection filter for HTTP connections.
     */
    fun addRedirectionFilter(fromHost: String, fromPort: Int = -1, toHost: String, toPort: Int = -1) {
        addConnectionFilter(RedirectionFilter(fromHost, fromPort, toHost, toPort))
    }

    /**
     * Add a redirection filter for prefix-based redirections.
     */
    fun addRedirectionFilter(fromPrefix: String, toPrefix: String) {
        addConnectionFilter(PrefixRedirectionFilter(fromPrefix, toPrefix))
    }

    class AuthorizationFilter(
        private val hostWildcard: Wildcard,
        private val headerName: String,
        private val headerValue: String?,
    ) : (URLConnection) -> URLConnection? {

        override fun invoke(connection: URLConnection): URLConnection {
            if (connection is HttpURLConnection && hostWildcard matches connection.url.host)
                connection.addRequestProperty(headerName, headerValue)
            return connection
        }

    }

    class RedirectionFilter(
        private val fromHost: String,
        private val fromPort: Int = -1,
        private val toHost: String,
        private val toPort: Int = -1,
    ) : (URLConnection) -> URLConnection? {

        override fun invoke(connection: URLConnection): URLConnection {
            val url = connection.url
            return if (connection !is HttpURLConnection || !url.matchesHost(fromHost) || url.port != fromPort)
                connection
            else
                URL(url.protocol, toHost, toPort, url.file).openConnection() as HttpURLConnection
        }

    }

    class PrefixRedirectionFilter(
        private val fromPrefix: String,
        private val toPrefix: String,
    ) : (URLConnection) -> URLConnection? {

        override fun invoke(connection: URLConnection): URLConnection = connection.url.toString().let {
            if (it.startsWith(fromPrefix))
                URL(toPrefix + it.substring(fromPrefix.length)).openConnection()
            else
                connection
        }

    }

    companion object {

        private val defaultFileSystem = FileSystems.getDefault()
        private val fileSystemCache = Cache<String, FileSystem> {
            FileSystems.newFileSystem(Paths.get(adjustWindowsPath(it)), null as ClassLoader?)
        }

        fun derivePath(url: URL): Path? {
            val uri = url.toURI()
            return when (uri.scheme) {
                "jar" -> {
                    val schemeSpecific = uri.schemeSpecificPart
                    var start = schemeSpecific.indexOf(':') // probably stepped past "file:"
                    val bang = schemeSpecific.lastIndexOf('!')
                    if (start !in 0 until bang)
                        return null
                    start++
                    while (start + 2 < bang && schemeSpecific[start] == '/' && schemeSpecific[start + 1] == '/')
                        start++ // implementations vary in their use of multiple slash characters
                    val fs = fileSystemCache[schemeSpecific.substring(start, bang)]
                    fs.getPath(schemeSpecific.substring(bang + 1))
                }
                "file" -> defaultFileSystem.getPath(adjustWindowsPath(uri.path))
                else -> null
            }
        }

        private fun adjustWindowsPath(path: String): String =
                if (File.separatorChar == '\\' && path[0] == '/' && path[2] == ':') path.substring(1) else path

        fun URL.matchesHost(target: String): Boolean = if (target.startsWith("*."))
            host.endsWith(target.substring(1)) || host == target.substring(2)
        else
            host == target

        fun URL.resolve(relativeURL: String) = URL(this, relativeURL)

        fun defaultBaseURL(): URL = File(".").canonicalFile.toURI().toURL()

    }

}
