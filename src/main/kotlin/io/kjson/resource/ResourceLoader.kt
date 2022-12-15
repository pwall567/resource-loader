/*
 * @(#) ResourceLoader.kt
 *
 * resource-loader  Resource loading mechanism
 * Copyright (c) 2021, 2022 Peter Wall
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
 * Resource loader abstract class.  This class provides a means of loading resources by a single mechanism, regardless
 * of whether the resource comes from a file in the local file system, a resource on the classpath or a remote URL.
 *
 * @author  Peter Wall
 * @param   T       the target resource type
 * @param   R       the `ResourceLoader` derived class type
 */
abstract class ResourceLoader<T, R : ResourceLoader<T, R>> protected constructor(
    private val resourcePath: Path?,
    override val resourceURL: URL
) : Loader<T, R> {

    private var parent: ResourceLoader<T, R>? = null

    private val resourceCache = Cache<URL, T> { load(createResourceDescriptor()) }

    /** The default extension to be used (the implementing class may supply this if required). */
    open val defaultExtension: String? = null

    /** The default extension to assume for HTTP(S) connections (the implementing class may supply this if required). */
    open val defaultContentType: String? = null

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
     * Load the current resource (from cache if available).
     */
    override fun load(): T {
        return resourceCache[resourceURL]
    }

    private fun createResourceDescriptor(): ResourceDescriptor {
        resourcePath?.let { path ->
            if (!Files.exists(path) || Files.isDirectory(path))
                throw ResourceNotFoundException(resourceURL)
            return ResourceDescriptor(
                inputStream = Files.newInputStream(path),
                url = resourceURL,
                size = Files.size(path),
                time= Files.getLastModifiedTime(path).toInstant(),
            )
        }
        return resourceURL.openConnection().let { conn ->
            val contentLength = conn.contentLengthLong.takeIf { it >= 0 }
            val lastModified = conn.lastModified.takeIf { it != 0L }?.let { Instant.ofEpochMilli(it) }
            val charsetName: String?
            val mimeType: String?
            if (conn is HttpURLConnection) {
                // TODO think about adding support for ifModifiedSince
                if (!checkHTTP(conn))
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
     * Confirm use of HTTP(S) URL.  The implementing class may veto a connection at this point, and it may add headers
     * (_e.g._ authorization) if required.  The default implementation returns `true`, meaning allow the connection.
     */
    open fun checkHTTP(conn: HttpURLConnection): Boolean {
        return true
    }

    /**
     * Resolve a name against the current resource loader.
     */
    override fun resolve(name: String): R {
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
            if (resourceURL == parentRef.resourceURL)
                throw ResourceRecursionException(resourceURL)
            parentRef = parentRef.parent
        }
        return resolvedLoader(resourcePath, resourceURL).also { it.parent = this }
    }

    companion object {

        val currentDirectory = File(".")
        private val separator = File.separatorChar
        private val defaultFileSystem = FileSystems.getDefault()
        private val fileSystemCache = Cache<String, FileSystem> { FileSystems.newFileSystem(Paths.get(it), null) }

        fun classPathURL(name: String): URL? = ResourceLoader::class.java.getResource(name)

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
                    val fs = fileSystemCache[schemeSpecific.substringAfter(':').substringBefore('!')]
                    fs.getPath(schemeSpecific.substringAfter('!'))
                }
                "file" -> defaultFileSystem.getPath(uri.path)
                else -> null
            }
        }

    }

}
