/*
 * @(#) HTTPResource.kt
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

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.time.Instant

import io.kjson.util.HTTPHeader

/**
 * An HTTP/HTTPS resource, as described by an `http(s):` [URL] and loaded by a [ResourceLoader].
 *
 * @author  Peter Wall
 */
class HTTPResource<T> internal constructor(
    private val baseURLString: String,
    pathElements: Array<String>,
    isDirectory: Boolean,
    resourceLoader: ResourceLoader<T>,
) : Resource<T>(pathElements, isDirectory, resourceLoader) {

    override val resourceURL: URL = URL(toString())

    override fun open(): ResourceDescriptor {
        if (isDirectory)
            throw ResourceLoaderException("Can't load directory resource $resourceURL")
        try {
            var conn: URLConnection = resourceURL.openConnection()
            for (filter in resourceLoader.connectionFilters)
                conn = filter(conn) ?: throw ResourceLoaderException("Connection vetoed - $resourceURL")
            return if (conn is HttpURLConnection) {
                // TODO think about adding support for ifModifiedSince / ETag
                if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND)
                    throw ResourceNotFoundException(resourceURL)
                if (conn.responseCode != HttpURLConnection.HTTP_OK)
                    throw IOException("Error status - ${conn.responseCode} - $resourceURL")
                val contentLength = conn.contentLengthLong.takeIf { it >= 0 }
                val lastModified = conn.lastModified.takeIf { it != 0L }?.let { Instant.ofEpochMilli(it) }
                val contentTypeHeader = conn.contentType?.let { HTTPHeader.parse(it) }
                val charsetName: String? = contentTypeHeader?.element()?.parameter("charset")
                val mimeType: String? = contentTypeHeader?.firstElementText()
                ResourceDescriptor(
                    inputStream = conn.inputStream,
                    url = resourceURL,
                    charsetName = charsetName,
                    size = contentLength,
                    time = lastModified,
                    mimeType = mimeType,
                    eTag = conn.getHeaderField("etag"),
                )
            } else {
                ResourceDescriptor(
                    inputStream = conn.inputStream,
                    url = resourceURL,
                )
            }
        }
        catch (rle: ResourceLoaderException) {
            throw rle
        }
        catch (e: Exception) {
            throw ResourceLoaderException("Error opening resource $resourceURL", e)
        }
    }

    override fun createResource(pathElements: Array<String>, isDirectory: Boolean): Resource<T> =
            HTTPResource(baseURLString, pathElements, isDirectory, resourceLoader)

    override fun equals(other: Any?): Boolean = this === other || other is HTTPResource<*> &&
            resourceLoader === other.resourceLoader && resourceURL.toString() == other.resourceURL.toString()

    override fun hashCode(): Int = resourceLoader.hashCode() xor resourceURL.toString().hashCode()

    /**
     * Create a string form of the URL for this `Resource` suitable for use in debugging and logging messages.
     */
    override fun toString(): String = buildString {
        append(baseURLString)
        for (pathElement in pathElements) {
            append('/')
            append(pathElement)
        }
        if (isDirectory)
            append('/')
    }

    companion object {

        fun <R> createHTTPResource(resourceURL: URL, resourceLoader: ResourceLoader<R>): HTTPResource<R> {
            val baseURLString = buildString {
                append(resourceURL.protocol)
                append(':')
                append('/')
                append('/')
                append(resourceURL.host)
                resourceURL.port.let {
                    if (it != -1) {
                        append(':')
                        append(it)
                    }
                }
            }
            val pathElements = resourceURL.path.split('/').toMutableList()
            require(pathElements[0].isEmpty()) { "FileResource absolute path does not begin with '/'" }
            pathElements.removeAt(0)
            val isDir = pathElements.last().isEmpty()
            if (isDir)
                pathElements.removeAt(pathElements.lastIndex)
            pathElements.dropDotElements()
            return HTTPResource(baseURLString, pathElements.toTypedArray(), isDir, resourceLoader)
        }

    }

}
