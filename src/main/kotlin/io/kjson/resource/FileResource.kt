/*
 * @(#) FileResource.kt
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
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

import io.kjson.util.startsWith

/**
 * A file resource, as described by a `file:` [URL] and loaded by a [ResourceLoader].
 *
 * @author  Peter Wall
 */
class FileResource<T> internal constructor(
    pathElements: Array<String>,
    isDirectory: Boolean,
    resourceLoader: ResourceLoader<T>,
) : Resource<T>(pathElements, isDirectory, resourceLoader) {

    override val resourceURL: URL = URL(
        buildString {
            append("file:")
            for (pathElement in pathElements) {
                append('/')
                append(pathElement)
            }
            if (isDirectory)
                append('/')
        }
    )

    override fun open(): ResourceDescriptor {
        if (isDirectory)
            throw ResourceLoaderException("Can't load directory resource $resourceURL")
        val extendedPath = resourceLoader.addExtension(toString())
        val file = File(extendedPath)
        if (!file.exists() || file.isDirectory)
            throw ResourceNotFoundException(resourceURL)
        try {
            return ResourceDescriptor(
                inputStream = file.inputStream(),
                url = resourceURL,
                size = file.length(),
                time = Instant.ofEpochMilli(file.lastModified()),
            )
        }
        catch (e: Exception) {
            throw ResourceLoaderException("Error opening resource $resourceURL", e)
        }
    }

    override fun createResource(pathElements: Array<String>, isDirectory: Boolean): Resource<T> =
            FileResource(pathElements, isDirectory, resourceLoader)

    override fun equals(other: Any?): Boolean = this === other || other is FileResource<*> &&
            pathElements.contentEquals(other.pathElements) && isDirectory == other.isDirectory &&
            resourceLoader === other.resourceLoader

    override fun hashCode(): Int = pathElements.contentHashCode() xor isDirectory.hashCode() xor
            resourceLoader.hashCode()

    /**
     * Create a string form of the URL for this `Resource` suitable for use in debugging and logging messages.
     */
    override fun toString(): String {
        if (currentPathElements.isNotEmpty() && pathElements.startsWith(currentPathElements)) {
            val i = currentPathElements.size
            if (i == pathElements.size)
                return "."
            return buildString {
                appendPathElements(i)
            }
        }
        return buildString {
            if (separator == '/')
                append('/')
            appendPathElements(0)
        }
    }

    private fun Appendable.appendPathElements(fromIndex: Int) {
        var i = fromIndex
        while (true) {
            append(pathElements[i++])
            if (i >= pathElements.size)
                break
            append(separator)
        }
        if (isDirectory)
            append(separator)
    }

    companion object {

        val separator: Char = File.separatorChar

        val currentPathElements = File(".").absolutePath.split(separator).toMutableList().apply {
            if (first().isEmpty())
                removeAt(0)
        }.dropDotElements().toTypedArray()

        fun <R> createFileResource(resourceURL: URL, resourceLoader: ResourceLoader<R>): FileResource<R> {
            require(resourceURL.protocol == "file") { "Must be \"file:\" URL" }
            val pathElements = resourceURL.path.split('/').toMutableList()
            if (pathElements[0].isEmpty())
                pathElements.removeAt(0)
            val isDir = pathElements.last().isEmpty()
            if (isDir)
                pathElements.removeAt(pathElements.lastIndex)
            pathElements.dropDotElements()
            return FileResource(pathElements.toTypedArray(), isDir, resourceLoader)
        }

        fun <R> createFileResource(file: File, resourceLoader: ResourceLoader<R>): FileResource<R> {
            val pathElements = file.canonicalPath.split(separator).toMutableList()
            if (pathElements[0].isEmpty())
                pathElements.removeAt(0)
            if (pathElements.last().isEmpty())
                pathElements.removeAt(pathElements.lastIndex)
            pathElements.dropDotElements()
            return FileResource(pathElements.toTypedArray(), file.isDirectory, resourceLoader)
        }

        fun <R> createFileResource(path: Path, resourceLoader: ResourceLoader<R>): FileResource<R> {
            val pathElements = path.toAbsolutePath().toString().split(separator).toMutableList()
            if (pathElements[0].isEmpty())
                pathElements.removeAt(0)
            if (pathElements.last().isEmpty())
                pathElements.removeAt(pathElements.lastIndex)
            pathElements.dropDotElements()
            val isDirectory = Files.isDirectory(path)
            return FileResource(pathElements.toTypedArray(), isDirectory, resourceLoader)
        }

    }

}
