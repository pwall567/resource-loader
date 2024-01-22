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

    open val defaultExtension: String? = null

    open val defaultMIMEType: String? = null

    /**
     * Load the resource, that is, read the external representation of the resource from the `InputStream` in the
     * [ResourceDescriptor] and return the internal form.
     */
    abstract fun load(rd: ResourceDescriptor): T

    /**
     * Check and possibly veto an HTTP connection.  This function is called after the connection has been opened but
     * before the `connect()` has been performed, allowing the implementing class to add request headers (for example,
     * `Authorization`) and possibly to veto the connection depending on the response headers or other factors.
     */
    open fun checkHTTP(conn: HttpURLConnection): Boolean = true

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
            val contentLength = conn.contentLengthLong.takeIf { it >= 0 }
            val lastModified = conn.lastModified.takeIf { it != 0L }?.let { Instant.ofEpochMilli(it) }
            val charsetName: String?
            val mimeType: String?
            val eTag: String?
            if (conn is HttpURLConnection) {
                // TODO think about adding support for ifModifiedSince / ETag
                if (!checkHTTP(conn))
                    throw ResourceLoaderException("Connection vetoed - ${resource.resourceURL}")
                // TODO would this be better handled by the function to substitute URLs?
                if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND)
                    throw ResourceNotFoundException(resource.resourceURL)
                if (conn.responseCode != HttpURLConnection.HTTP_OK)
                    throw IOException("Error status - ${conn.responseCode} - ${resource.resourceURL}")
                val contentTypeHeader = conn.contentType?.let { HTTPHeader.create(it) }
                charsetName = contentTypeHeader?.element()?.parameter("charset")
                mimeType = contentTypeHeader?.firstElementText()
                eTag = conn.getHeaderField("etag")
            }
            else {
                charsetName = null
                mimeType = conn.contentType
                eTag = null
            }
            return ResourceDescriptor(
                inputStream = conn.getInputStream(),
                url = resource.resourceURL,
                charsetName = charsetName,
                size = contentLength,
                time = lastModified,
                mimeType = mimeType,
                eTag = eTag,
            )
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

    }

}
