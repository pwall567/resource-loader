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

import java.net.URL

/**
 * A resource, as described by a [URL] and loaded by a [ResourceLoader].
 *
 * @author  Peter Wall
 */
sealed class Resource<T>(
    val pathElements: Array<String>,
    val isDirectory: Boolean,
    val resourceLoader: ResourceLoader<T>,
) {

    abstract val resourceURL: URL

    /**
     * Load the resource.  This function is delegated to the [ResourceLoader], which will load a resource of the target
     * type.
     */
    open fun load(): T = resourceLoader.load(this)

    /**
     * Resolve a relative URL against the current `Resource`, returning a new `Resource`.
     */
    fun resolve(relativeURL: String): Resource<T> {
        if (relativeURL.contains(':'))
            return resourceLoader.resource(URL(relativeURL))
        if (relativeURL.isEmpty()) {
            return if (isDirectory)
                this
            else
                createResource(pathElements.dropLast(1).toTypedArray(), true)
        }
        val relativeURLElements = relativeURL.split('/').toMutableList()
        val newPath: MutableList<String>
        if (relativeURLElements[0].isEmpty()) { // starts with slash - absolute path
            if (relativeURLElements.size == 1)
                return createResource(emptyArray(), true)
            newPath = mutableListOf()
            relativeURLElements.removeAt(0)
        }
        else {
            newPath = pathElements.toMutableList()
            if (!isDirectory)
                newPath.removeLast()
        }
        var isDir = true
        for (i in relativeURLElements.indices) {
            when (relativeURLElements[i]) {
                "." -> isDir = true
                ".." -> {
                    if (newPath.isEmpty())
                        throw IllegalArgumentException("Illegal use of \"..\" in URL")
                    newPath.removeLast()
                    isDir = true
                }
                "" -> {
                    if (i == relativeURLElements.lastIndex) {
                        isDir = true
                        break
                    }
                    throw IllegalArgumentException("Illegal use of \"//\" in URL")
                }
                else -> {
                    newPath.add(relativeURLElements[i])
                    isDir = false
                }
            }
        }
        return createResource(newPath.toTypedArray(), isDir)
    }

    protected abstract fun createResource(
        pathElements: Array<String>,
        isDirectory: Boolean,
    ): Resource<T>

    /**
     * Open a [Resource] for reading.  The result of this function is a [ResourceDescriptor], which contains an open
     * `InputStream` and all the metadata known about the resource.
     */
    abstract fun open(): ResourceDescriptor

    companion object {

        /**
         * Get a URL for a resource in the classpath (will be either a `file:` or a `jar:` URL).
         */
        fun classPathURL(name: String): URL? = Resource::class.java.getResource(name)

        fun MutableList<String>.dropDotElements(): MutableList<String> {
            var i = 0
            while (i < size) {
                when (this[i]) {
                    "." -> removeAt(i)
                    ".." -> {
                        if (i == 0)
                            throw IllegalArgumentException("Illegal use of \"..\" in URL")
                        removeAt(i)
                        removeAt(--i)
                    }
                    "" -> throw IllegalArgumentException("Illegal empty path element in URL")
                    else -> i++
                }
            }
            return this
        }

    }

}
