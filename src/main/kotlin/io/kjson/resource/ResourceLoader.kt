/*
 * @(#) ResourceLoader.kt
 *
 * resource-loader  Resource loading mechanism
 * Copyright (c) 2023, 2024, 2025 Peter Wall
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
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.nio.file.Path

import io.kjson.resource.FileResource.Companion.createFileResource
import io.kjson.resource.HTTPResource.Companion.createHTTPResource
import io.kjson.resource.JARResource.Companion.createJARResource
import net.pwall.text.Wildcard

/**
 * The base `ResourceLoader` class.
 *
 * @author  Peter Wall
 */
abstract class ResourceLoader<T>(
    val baseURL: URL = defaultBaseURL(),
) {

    internal val connectionFilters = mutableListOf<(URLConnection) -> URLConnection?>()

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
    fun resource(resourceFile: File): Resource<T> = createFileResource(resourceFile, this)

    /**
     * Get a [Resource], specifying a [Path].
     */
    fun resource(resourcePath: Path): Resource<T> = createFileResource(resourcePath, this)

    /**
     * Get a [Resource], specifying a [URL].
     */
    fun resource(resourceURL: URL): Resource<T> = when (resourceURL.protocol) {
        "http", "https" -> createHTTPResource(resourceURL, this)
        "file" -> createFileResource(resourceURL, this)
        "jar" -> createJARResource(resourceURL, this)
        else -> throw ResourceLoaderException("URL not recognised: $resourceURL")
    }

    /**
     * Create a [SyntheticResource], specifying the name and value.
     */
    fun syntheticResource(name: String, value: T): SyntheticResource<T> = SyntheticResource(name, value, this)

    /**
     * Load the resource identified by the specified [Resource].  This function is open for extension to allow, for
     * example, caching implementations to provide a returned resource bypassing the regular mechanism.
     */
    open fun load(resource: Resource<T>): T = load(resource.open())

    /**
     * Load the resource identified by the specified [URL].
     */
    fun load(resourceURL: URL): T = load(resource(resourceURL))

    /**
     * Load the resource identified by an identifier string, which is resolved against the base URL.
     */
    fun load(resourceId: String): T = load(baseURL.resolve(resourceId))

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
    fun addPrefixRedirectionFilter(fromPrefix: String, toPrefix: String) {
        addConnectionFilter(PrefixRedirectionFilter(fromPrefix, toPrefix))
    }

    class AuthorizationFilter(
        val hostWildcard: Wildcard,
        val headerName: String,
        val headerValue: String?,
    ) : (URLConnection) -> URLConnection? {

        override fun invoke(connection: URLConnection): URLConnection {
            if (connection is HttpURLConnection && hostWildcard matches connection.url.host)
                connection.addRequestProperty(headerName, headerValue)
            return connection
        }

    }

    class RedirectionFilter(
        val fromHost: String,
        val fromPort: Int = -1,
        val toHost: String,
        val toPort: Int = -1,
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
        val fromPrefix: String,
        val toPrefix: String,
    ) : (URLConnection) -> URLConnection? {

        override fun invoke(connection: URLConnection): URLConnection = connection.url.toString().let {
            if (it.startsWith(fromPrefix))
                URL(toPrefix + it.substring(fromPrefix.length)).openConnection()
            else
                connection
        }

    }

    companion object {

        internal fun URL.matchesHost(target: String): Boolean = if (target.startsWith("*."))
            host.endsWith(target.substring(1)) || host == target.substring(2)
        else
            host == target

        fun URL.resolve(relativeURL: String) = URL(this, relativeURL)

        fun defaultBaseURL(): URL = File(".").canonicalFile.toURI().toURL()

        fun createFileURL(path: String): String = "file:$path"

        fun createFileURL(file: File): String = createFileURL(file.canonicalPath)

    }

}
