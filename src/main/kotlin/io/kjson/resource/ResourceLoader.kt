/*
 * @(#) ResourceLoader.kt
 *
 * resource-loader  Resource loading mechanism
 * Copyright (c) 2021 Peter Wall
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

/**
 * Resource loader abstract class.  This class provides a means of loading resources by a single mechanism, regardless
 * of whether the resource comes from a file in the local file system, a resource on the classpath or a remote URL.
 *
 * @author  Peter Wall
 */
abstract class ResourceLoader<T, R : ResourceLoader<T, R>> protected constructor(
    private val resourcePath: Path?,
    val resourceURL: URL
) {

    private var parent: ResourceLoader<T, R>? = null

    /** The default extension to be used (the implementing class may supply this if required). */
    open val defaultExtension: String? = null

    /**
     * Load the resource.  The [ResourceDescriptor] provides all the information available to access the data.
     * Implementing classes must override this function to perform the actual load of the resource.
     */
    abstract fun load(rd: ResourceDescriptor): T

    /**
     * Get a new copy of the derived class, the result of resolving a name against this [ResourceLoader].
     * Implementing classes must override this function to return an instance of the derived type.
     */
    abstract fun resolvedLoader(resourcePath: Path?, resourceURL: URL) : R

    /**
     * Load the named resource.  This function first resolves the name against the current resource, and then loads the
     * resource under that name.
     */
    fun load(name: String): T {
        return resolve(name).load()
    }

    /**
     * Load the current resource.
     */
    fun load(): T {
        // TODO - consider caching
        resourcePath?.let { path ->
            if (!Files.exists(path) || Files.isDirectory(path))
                throw ResourceNotFoundException(resourceURL)
            val size = Files.size(path)
            val time = Files.getLastModifiedTime(path).toInstant()
            return load(ResourceDescriptor(Files.newInputStream(path), size = size, time= time))
        }
        return resourceURL.openConnection().let { conn ->
            val contentLength = conn.contentLengthLong.takeIf { it >= 0 }
            val lastModified = conn.lastModified.takeIf { it != 0L }?.let { Instant.ofEpochMilli(it) }
            when (conn) {
                is HttpURLConnection -> {
                    // TODO think about adding support for ifModifiedSince
                    if (!checkHTTP(conn))
                        throw ResourceLoaderException("Connection vetoed - $resourceURL")
                    if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND)
                        throw ResourceNotFoundException(resourceURL)
                    if (conn.responseCode != HttpURLConnection.HTTP_OK)
                        throw IOException("Error status - ${conn.responseCode} - $resourceURL")
                    val contentType = conn.contentType?.split(';')?.map { it.strim() }
                    val charsetName = contentType?.findStartingFrom(1) { it.startsWith("charset=") }?.drop(8)?.strim()
                    load(ResourceDescriptor(conn.getInputStream(), charsetName, contentLength, lastModified,
                            contentType?.get(0)))
                }
                else -> load(ResourceDescriptor(conn.getInputStream(), null, contentLength, lastModified,
                        conn.contentType))
            }
        }
    }

    /**
     * Confirm use of HTTP(S) URL.  The implementing class may veto a connection at this point, and it may add headers
     * (_e.g._ authorization) if required.  The default implementation returns `true`, meaning allow the connection.
     */
    open fun checkHTTP(conn: HttpURLConnection): Boolean {
        return true
    }

    /**
     * Resolve a name against the current resource loader.
     */
    open fun resolve(name: String): R {
        // TODO consider separate functions for resolve(URI) and resolve(Path)
        val extendedName = addExtension(name, defaultExtension)
        if (URI(extendedName).scheme == null && resourcePath != null) {
            val resolved = when {
                Files.isDirectory(resourcePath) -> resourcePath.resolve(extendedName)
                else -> resourcePath.resolveSibling(extendedName)
            }
            return checkRecursion(resolved, resolved.toUri().toURL())
        }
        val resolvedURL = resourceURL.toURI().resolve(extendedName).toURL()
        return checkRecursion(derivePath(resolvedURL), resolvedURL)
    }

    private fun checkRecursion(resourcePath: Path?, resourceURL: URL): R {
        var parentRef = parent
        while (parentRef != null) {
            if (this == parentRef)
                throw ResourceRecursionException(resourceURL)
            parentRef = parentRef.parent
        }
        return resolvedLoader(resourcePath, resourceURL).also { it.parent = this }
    }

    override fun equals(other: Any?): Boolean = this === other ||
            other is ResourceLoader<*, *> && resourcePath == other.resourcePath && resourceURL == other.resourceURL

    override fun hashCode(): Int = resourcePath.hashCode() xor resourceURL.hashCode()

    companion object {

        val currentDirectory = File(".")

        private val separator = File.separatorChar

        private val fileSystemCache = mutableMapOf<String, FileSystem>()

        fun addExtension(s: String, defaultExtension: String?): String = when {
            defaultExtension != null && s.indexOf('.', s.lastIndexOf(separator) + 1) < 0 -> "$s.$defaultExtension"
            else -> s
        }

        fun derivePath(url: URL): Path? {
            val uri = url.toURI()
            return when (uri.scheme) {
                // TODO - consider adding "classpath" scheme (similar to Spring)
                "jar" -> {
                    val schemeSpecific = uri.schemeSpecificPart
                    val jarName = schemeSpecific.substringAfter(':').substringBefore('!')
                    val fs = fileSystemCache[jarName] ?:
                            FileSystems.newFileSystem(Paths.get(jarName), null).also { fileSystemCache[jarName] = it }
                    fs.getPath(schemeSpecific.substringAfter('!'))
                }
                "file" -> FileSystems.getDefault().getPath(uri.path)
                else -> null
            }
        }

        inline fun <T> List<T>.findStartingFrom(index: Int = 0, predicate: (T) -> Boolean): T? {
            for (i in index until this.size)
                this[i].let { if (predicate(it)) return it }
            return null
        }

        /**
         * Smart trim - avoid allocating a new string if possible.
         */
        fun String.strim(): String {
            var end = length
            if (end == 0)
                return this
            var start = 0
            while (true) {
                if (start >= end)
                    return ""
                if (get(start) != ' ')
                    break
                start++
            }
            while (get(end - 1) == ' ')
                end--
            return if (start == 0 && end == length) this else substring(start, end)
        }

    }

}
