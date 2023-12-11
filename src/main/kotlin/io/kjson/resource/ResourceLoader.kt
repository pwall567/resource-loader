/*
 * @(#) ResourceLoader.kt
 *
 * resource-loader  Resource loading mechanism
 * Copyright (c) 2023 Peter Wall
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
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path

/**
 * The base `ResourceLoader` class.
 *
 * @author  Peter Wall
 */
abstract class ResourceLoader<T> {

    open val defaultExtension: String? = null

    open val defaultMIMEType: String? = null

    abstract fun load(rd: ResourceDescriptor): T

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
    fun resource(resourceURL: URL): Resource<T> = Resource(Resource.derivePath(resourceURL), resourceURL, this)

    fun addExtension(s: String): String = when {
        defaultExtension != null && s.indexOf('.', s.lastIndexOf(File.separatorChar) + 1) < 0 -> "$s.$defaultExtension"
        else -> s
    }

}
