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

/**
 * The base `ResourceLoader` class.
 *
 * @author  Peter Wall
 */
abstract class ResourceLoader<T> {

    private val connectionFilters = mutableListOf<(HttpURLConnection) -> HttpURLConnection?>()

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
            val conn: URLConnection = resource.resourceURL.openConnection()
            if (conn is HttpURLConnection) {
                var httpConn: HttpURLConnection = conn
                for (filter in connectionFilters)
                    httpConn = filter(httpConn) ?:
                        throw ResourceLoaderException("Connection vetoed - ${resource.resourceURL}")
                // TODO think about adding support for ifModifiedSince / ETag
                if (httpConn.responseCode == HttpURLConnection.HTTP_NOT_FOUND)
                    throw ResourceNotFoundException(resource.resourceURL)
                if (httpConn.responseCode != HttpURLConnection.HTTP_OK)
                    throw IOException("Error status - ${httpConn.responseCode} - ${resource.resourceURL}")
                val contentLength = httpConn.contentLengthLong.takeIf { it >= 0 }
                val lastModified = httpConn.lastModified.takeIf { it != 0L }?.let { Instant.ofEpochMilli(it) }
                val contentTypeHeader = httpConn.contentType?.let { HTTPHeader.create(it) }
                val charsetName: String? = contentTypeHeader?.element()?.parameter("charset")
                val mimeType: String? = contentTypeHeader?.firstElementText()
                return ResourceDescriptor(
                    inputStream = httpConn.inputStream,
                    url = resource.resourceURL,
                    charsetName = charsetName,
                    size = contentLength,
                    time = lastModified,
                    mimeType = mimeType,
                    eTag = httpConn.getHeaderField("etag"),
                )
            }
            else {
                return ResourceDescriptor(
                    inputStream = conn.getInputStream(),
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

    fun addExtension(s: String): String = when {
        defaultExtension != null && s.indexOf('.', s.lastIndexOf(File.separatorChar) + 1) < 0 -> "$s.$defaultExtension"
        else -> s
    }

    fun addConnectionFilter(filter: (HttpURLConnection) -> HttpURLConnection?) {
        connectionFilters.add(filter)
    }

    fun addAuthorizationFilter(host: String, headerName: String, headerValue: String?) {
        addConnectionFilter(AuthorizationFilter(host, headerName, headerValue))
    }

    class AuthorizationFilter(
        private val host: String,
        private val headerName: String,
        private val headerValue: String?,
    ) : (HttpURLConnection) -> HttpURLConnection? {

        override fun invoke(httpConn: HttpURLConnection): HttpURLConnection {
            if (httpConn.url.matchesHost(host))
                httpConn.addRequestProperty(headerName, headerValue)
            return httpConn
        }

    }

    companion object {

        private val defaultFileSystem = FileSystems.getDefault()
        private val fileSystemCache = Cache<String, FileSystem> {
            FileSystems.newFileSystem(Paths.get(it), null as ClassLoader?)
        }

        fun derivePath(url: URL): Path? {
            val uri = url.toURI()
            return when (uri.scheme) {
                "jar" -> {
                    val schemeSpecific = uri.schemeSpecificPart
                    var start = schemeSpecific.indexOf(':') // probably stepped past "file:"
                    val bang = schemeSpecific.lastIndexOf('!')
                    if (start < 0 || bang < 0 || start > bang)
                        return null
                    start++
                    while (start + 2 < bang && schemeSpecific[start] == '/' && schemeSpecific[start + 1] == '/')
                        start++ // implementations vary in their use of multiple slash characters
                    val fs = fileSystemCache[schemeSpecific.substring(start, bang)]
                    fs.getPath(schemeSpecific.substring(bang + 1))
                }
                "file" -> defaultFileSystem.getPath(uri.path)
                else -> null
            }
        }

        fun URL.matchesHost(target: String): Boolean = if (target.startsWith("*."))
            host.endsWith(target.substring(1)) || host == target.substring(2)
        else
            host == target

    }

}
