/*
 * @(#) ResourceDescriptor.kt
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

import java.io.InputStream
import java.net.URL
import java.nio.charset.Charset
import java.time.Instant

import net.pwall.pipeline.codec.DynamicReader

/**
 * A Resource Descriptor.  This contains all the information known about the resource, to allow the loading function to
 * read the data and apply the correct charset and data conversion required.
 *
 * @author  Peter Wall
 */
data class ResourceDescriptor(
    // TODO add resourceURL (or reference to ResourceLoader)
    val inputStream: InputStream,
    val url: URL,
    val charsetName: String? = null,
    val size: Long? = null,
    val time: Instant? = null,
    val mimeType: String? = null
) {

    /**
     * Get a `Reader` to read the resource.  If the resource specifies an explicit charset name, the function will
     * attempt to locate a charset with that name, and if it is successful that charset will be used.  If the resource
     * does not specify an explicit charset name and the `defaultCharset` parameter is provided, that will be used.  If
     * neither is provided, the [DynamicReader] will choose the charset based on the content of the data (in most cases
     * this will be the best option).
     *
     * @param   defaultCharset  the charset to use if the resource does not specify an explicit charset
     * @return                  a [DynamicReader] (extends `Reader`)
     */
    fun getReader(defaultCharset: Charset? = null): DynamicReader {
        val cs = charsetName?.let { try { Charset.forName(it) } catch (_: Exception) { null } } ?: defaultCharset
        return DynamicReader(inputStream, cs)
    }

}
