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
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

/**
 * A resource, as described by a [URL] and loaded by a [ResourceLoader].
 *
 * @author  Peter Wall
 */
class Resource<T> internal constructor(
    internal val resourcePath: Path?,
    val resourceURL: URL,
    private val resourceLoader: ResourceLoader<T>,
) {

    /**
     * Load the resource.  This function is delegated to the [ResourceLoader], which will load a resource of the target
     * type.
     */
    fun load(): T = resourceLoader.load(this)

    /**
     * Resolve a relative URL against the current `Resource`, returning a new `Resource`.
     */
    fun resolve(relativeURL: String): Resource<T> {
        val extendedURL = resourceLoader.addExtension(relativeURL)
        if (URI(extendedURL).scheme == null && resourcePath != null) {
            // we're navigating entirely within the file system
            val resolved: Path = when {
                Files.isDirectory(resourcePath) -> resourcePath.resolve(extendedURL)
                else -> resourcePath.resolveSibling(extendedURL)
            }
            return resourceLoader.resource(resolved)
        }
        val resolvedURL = resourceURL.toURI().resolve(extendedURL).toURL()
        return resourceLoader.resource(resolvedURL)
    }

    override fun equals(other: Any?): Boolean = this === other || other is Resource<*> &&
            resourceLoader === other.resourceLoader && resourceURL.toString() == other.resourceURL.toString()

    override fun hashCode(): Int = resourceLoader.hashCode() xor resourceURL.toString().hashCode()

    /**
     * Create a string form of the URL for this `Resource` suitable for use in debugging and logging messages.
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

        val currentPath: String = File(".").absolutePath.let {
            when {
                it.endsWith("/.") -> it.dropLast(1)
                it.endsWith('/') -> it
                else -> "$it/"
            }
        }

        /**
         * Get a URL for a resource in the classpath (will be either a `file:` or a `jar:` URL).
         */
        fun classPathURL(name: String): URL? = Resource::class.java.getResource(name)

    }

}
