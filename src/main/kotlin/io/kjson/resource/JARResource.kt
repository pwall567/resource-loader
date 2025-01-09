/*
 * @(#) JARResource.kt
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

import java.net.JarURLConnection
import java.net.URL
import java.time.Instant
import java.util.jar.JarFile

import io.kjson.util.Cache

/**
 * A resource in a JAR file, as described by a `jar:` [URL] and loaded by a [ResourceLoader].
 *
 * @author  Peter Wall
 */
class JARResource<T> internal constructor(
    private val jarURLString: String,
    pathElements: Array<String>,
    isDirectory: Boolean,
    resourceLoader: ResourceLoader<T>,
) : Resource<T>(pathElements, isDirectory, resourceLoader) {

    override val resourceURL: URL = URL(
        buildString {
            append("jar:")
            append(jarURLString)
            append('!')
            append('/')
            val n = pathElements.size
            if (n > 0) {
                var i = 0
                while (true) {
                    append(pathElements[i++])
                    if (i >= n)
                        break
                    append('/')
                }
                if (isDirectory)
                    append('/')
            }
        }
    )

    override fun open(): ResourceDescriptor {
        if (isDirectory)
            throw ResourceLoaderException("Can't load directory resource $resourceURL")
        val path = buildString {
            for (pathElement in pathElements) {
                append('/')
                append(pathElement)
            }
        }
        val extendedPath = resourceLoader.addExtension(path)
        try {
            val jarFile = jarCache[jarURLString]
            val jarEntry = jarFile.getJarEntry(extendedPath) ?: throw ResourceNotFoundException(resourceURL)
            return ResourceDescriptor(
                inputStream = jarFile.getInputStream(jarEntry),
                url = resourceURL,
                size = jarEntry.size,
                time = Instant.ofEpochMilli(jarEntry.time),
            )
        }
        catch (rle: ResourceLoaderException) {
            throw rle
        }
        catch (e: Exception) {
            throw ResourceLoaderException("Error opening JAR resource $resourceURL", e)
        }
    }

    override fun createResource(pathElements: Array<String>, isDirectory: Boolean): JARResource<T> =
            JARResource(jarURLString, pathElements, isDirectory, resourceLoader)

    override fun equals(other: Any?): Boolean = this === other || other is JARResource<*> &&
            resourceLoader === other.resourceLoader && resourceURL.toString() == other.resourceURL.toString()

    override fun hashCode(): Int = resourceLoader.hashCode() xor resourceURL.toString().hashCode()

    /**
     * Create a string form of the URL for this `Resource` suitable for use in debugging and logging messages.
     */
    override fun toString(): String = resourceURL.toString()

    companion object {

        val jarCache = Cache<String, JarFile> {
            (URL("jar:$it!/").openConnection() as JarURLConnection).jarFile
        }

        fun <R> createJARResource(url: URL, resourceLoader: ResourceLoader<R>): JARResource<R> {
            val urlString = url.toString()
            require(urlString.startsWith("jar:")) { "URl must start with \"jar:\"" }
            val innerURLEnd = urlString.indexOf("!/")
            require(innerURLEnd > 0) { "Url must contain \"!/\"" }
            if (innerURLEnd + 2 == urlString.length)
                return JARResource(urlString.substring(4, innerURLEnd), emptyArray(), true, resourceLoader)
            val pathElements = urlString.drop(innerURLEnd + 2).split('/').toMutableList()
            val isDir = pathElements.last().isEmpty()
            if (isDir)
                pathElements.removeAt(pathElements.lastIndex)
            pathElements.dropDotElements()
            return JARResource(urlString.substring(4, innerURLEnd), pathElements.toTypedArray(), isDir, resourceLoader)
        }

    }

}
