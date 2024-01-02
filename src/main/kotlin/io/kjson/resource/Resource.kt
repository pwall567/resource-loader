/*
 * @(#) Resource.kt
 *
 * resource-loader  Resource loading mechanism
 * Copyright (c) 2021, 2022, 2023, 2024 Peter Wall
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
import java.net.URI
import java.net.URL
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

import io.kjson.util.Cache
import io.kjson.util.HTTPHeader

/**
 * A resource, as described by a [URL] and loaded by a [ResourceLoader].
 *
 * @author  Peter Wall
 */
class Resource<T> internal constructor(
    private val resourcePath: Path?,
    val resourceURL: URL,
    private val resourceLoader: ResourceLoader<T>,
) {

    fun load(): T = resourceLoader.load(createResourceDescriptor())

    private fun createResourceDescriptor(): ResourceDescriptor {
        resourcePath?.let { path ->
            if (!Files.exists(path) || Files.isDirectory(path))
                throw ResourceNotFoundException(resourceURL)
            return ResourceDescriptor(
                inputStream = Files.newInputStream(path),
                url = resourceURL,
                size = Files.size(path),
                time = Files.getLastModifiedTime(path).toInstant(),
            )
        }
        return resourceURL.openConnection().let { conn ->
            val contentLength = conn.contentLengthLong.takeIf { it >= 0 }
            val lastModified = conn.lastModified.takeIf { it != 0L }?.let { Instant.ofEpochMilli(it) }
            val charsetName: String?
            val mimeType: String?
            if (conn is HttpURLConnection) {
                // TODO think about adding support for ifModifiedSince
                if (!resourceLoader.checkHTTP(conn))
                    throw ResourceLoaderException("Connection vetoed - $resourceURL")
                if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND)
                    throw ResourceNotFoundException(resourceURL)
                if (conn.responseCode != HttpURLConnection.HTTP_OK)
                    throw IOException("Error status - ${conn.responseCode} - $resourceURL")
                val contentTypeHeader = conn.contentType?.let { HTTPHeader.create(it) }
                charsetName = contentTypeHeader?.element()?.parameter("charset")
                mimeType = contentTypeHeader?.firstElementText()
            }
            else {
                charsetName = null
                mimeType = conn.contentType
            }
            ResourceDescriptor(
                inputStream = conn.getInputStream(),
                url = resourceURL,
                charsetName = charsetName,
                size = contentLength,
                time = lastModified,
                mimeType = mimeType,
            )
        }
    }

    /**
     * Resolve a name against the current resource loader.
     */
    fun resolve(name: String): Resource<T> {
        // TODO consider separate functions for resolve(URI) and resolve(Path)
        val extendedName = resourceLoader.addExtension(name)
        if (URI(extendedName).scheme == null && resourcePath != null) {
            val resolved = when {
                Files.isDirectory(resourcePath) -> resourcePath.resolve(extendedName)
                else -> resourcePath.resolveSibling(extendedName)
            }
            return Resource(resolved, resolved.toUri().toURL(), resourceLoader)
        }
        val resolvedURL = resourceURL.toURI().resolve(extendedName).toURL()
        return Resource(derivePath(resolvedURL), resolvedURL, resourceLoader)
    }

    override fun equals(other: Any?): Boolean = this === other || other is Resource<*> &&
            resourceLoader === other.resourceLoader && resourceURL.toString() == other.resourceURL.toString()

    override fun hashCode(): Int = resourceLoader.hashCode() xor resourceURL.toString().hashCode()

    /**
     * Create a form of the URL for this `Resource` suitable for use in debugging and logging messages.
     */
    override fun toString(): String = if (resourceURL.protocol != "file") resourceURL.toString() else {
        val path = resourceURL.path.let { if (it.startsWith("///")) it.drop(2) else it }
        // some run-time libraries may create URL as file:///path, while others may use file:/path
        if (path.startsWith(currentPath))
            path.drop(currentPath.length)
        else
            path
    }

    companion object {

        private val defaultFileSystem = FileSystems.getDefault()
        private val fileSystemCache = Cache<String, FileSystem> {
            FileSystems.newFileSystem(Paths.get(it), null as ClassLoader?)
        }

        val currentPath: String = File(".").absolutePath.let {
            when {
                it.endsWith("/.") -> it.dropLast(1)
                it.endsWith('/') -> it
                else -> "$it/"
            }
        }

        fun classPathURL(name: String): URL? = Resource::class.java.getResource(name)

        fun derivePath(url: URL): Path? {
            val uri = url.toURI()
            return when (uri.scheme) {
                // TODO - consider adding "classpath" scheme (similar to Spring)
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
