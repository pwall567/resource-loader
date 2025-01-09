/*
 * @(#) SyntheticResource.kt
 *
 * resource-loader  Resource loading mechanism
 * Copyright (c) 2025 Peter Wall
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

import java.io.ByteArrayInputStream
import java.net.URL

class SyntheticResource<T>(
    private val name: String,
    private val value: T,
    resourceLoader: ResourceLoader<T>,
) : Resource<T>(arrayOf(name), false, resourceLoader) {

    override val resourceURL: URL
        get() = URL("http://localhost/$name")

    override fun open(): ResourceDescriptor {
        return ResourceDescriptor(
            inputStream = ByteArrayInputStream(ByteArray(0)),
            url = resourceURL,
        )
    }

    override fun load(): T = value

    override fun createResource(pathElements: Array<String>, isDirectory: Boolean): SyntheticResource<T> =
            SyntheticResource(pathElements.last(), value, resourceLoader)

    override fun toString() = name

}
