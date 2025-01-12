/*
 * @(#) Resource.kt
 *
 * resource-loader  Resource loading mechanism
 * Copyright (c) 2021, 2022, 2023, 2024, 2025 Peter Wall
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
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.JarURLConnection
import java.net.URL
import java.net.URLConnection
import java.nio.file.Paths
import java.time.Instant

import io.kjson.util.HTTPHeader

/**
 * A resource, as described by a [URL] and loaded by a [ResourceLoader].
 *
 * @author  Peter Wall
 */
open class Resource<T>(
    val url: URL,
    val isDirectory: Boolean,
    private val resourceLoader: ResourceLoader<T>,
) {

    /**
     * Load the resource.  This function is delegated to the [ResourceLoader], which will load a resource of the target
     * type.
     */
    open fun load(): T = resourceLoader.load(this)

    /**
     * Resolve a relative URL against the current `Resource`, returning a new `Resource`.
     */
    open fun resolve(relativeURL: String): Resource<T> {
        val resolvedURL = URL(url, relativeURL)
        return Resource(resolvedURL, resolvedURL.toString().endsWith('/'), resourceLoader)
    }

    /**
     * Open a [Resource] for reading.  The result of this function is a [ResourceDescriptor], which contains an open
     * `InputStream` and all the metadata known about the resource.
     */
    open fun open(): ResourceDescriptor {
        if (isDirectory)
            throw ResourceLoaderException("Can't load directory resource $url")
        try {
            var conn: URLConnection = url.openConnection()
            for (filter in resourceLoader.connectionFilters)
                conn = filter(conn) ?: throw ResourceVetoedException(toString())
            return when (conn) {
                is HttpURLConnection -> {
                    // TODO think about adding support for ifModifiedSince / ETag
                    if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND)
                        throw ResourceNotFoundException(toString())
                    if (conn.responseCode != HttpURLConnection.HTTP_OK)
                        throw IOException("Error status - ${conn.responseCode} - $url")
                    val contentLength = conn.contentLengthLong.takeIf { it >= 0 }
                    val lastModified = conn.lastModified.takeIf { it != 0L }?.let { Instant.ofEpochMilli(it) }
                    val contentTypeHeader = conn.contentType?.let { HTTPHeader.parse(it) }
                    val charsetName: String? = contentTypeHeader?.element()?.parameter("charset")
                    val mimeType: String? = contentTypeHeader?.firstElementText()
                    ResourceDescriptor(
                        inputStream = conn.inputStream,
                        url = url,
                        charsetName = charsetName,
                        size = contentLength,
                        time = lastModified,
                        mimeType = mimeType,
                        eTag = conn.getHeaderField("etag"),
                    )
                }
                is JarURLConnection -> {
                    val jarEntry = conn.jarEntry
                    ResourceDescriptor(
                        inputStream = conn.inputStream,
                        url = url,
                        size = jarEntry.size,
                        time = Instant.ofEpochMilli(jarEntry.time),
                    )
                }
                else -> {
                    val contentLength = conn.contentLengthLong.takeIf { it >= 0 }
                    val lastModified = conn.lastModified.takeIf { it != 0L }?.let { Instant.ofEpochMilli(it) }
                    val contentTypeHeader = conn.contentType?.let { HTTPHeader.parse(it) }
                    val mimeType: String? = contentTypeHeader?.firstElementText()
                    ResourceDescriptor(
                        inputStream = conn.inputStream,
                        url = url,
                        size = contentLength,
                        time = lastModified,
                        mimeType = mimeType,
                    )
                }
            }
        }
        catch (rle: ResourceLoaderException) {
            throw rle
        }
        catch (fnfe: FileNotFoundException) {
            throw ResourceNotFoundException(toString())
        }
        catch (e: Exception) {
            throw ResourceLoaderException("Error opening resource $url", e)
        }
    }

    override fun toString(): String {
        if (url.protocol != "file")
            return url.toString()
        val filePath = if (File.separatorChar == '/') url.path else Paths.get(url.toURI()).toString()
        return when {
            !filePath.startsWith(currentPath) -> filePath
            filePath.length == currentPath.length -> "."
            else -> filePath.substring(currentPath.length)
        }
    }

    companion object {

        val currentPath = File(".").canonicalPath + File.separatorChar

        /**
         * Get a URL for a resource in the classpath (will be either a `file:` or a `jar:` URL).
         */
        fun classPathURL(name: String): URL? = Resource::class.java.getResource(name)

    }

}
