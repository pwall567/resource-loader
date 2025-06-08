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

/**
 * A `SyntheticResource` may be used to represent an internally-generated [Resource].
 *
 * @author  Peter Wall
 */
class SyntheticResource<T>(
    private val name: String,
    private val value: T,
    resourceLoader: ResourceLoader<T>,
) : Resource<T>(URL("http://localhost/synthetic/$name"), false, resourceLoader) {

    override fun open(): ResourceDescriptor {
        // this should never be called, but if it is, it will return a zero-length stream
        return ResourceDescriptor(
            inputStream = ByteArrayInputStream(ByteArray(0)),
            url = url,
        )
    }

    override fun resolve(relativeURL: String): Resource<T> {
        if (!relativeURL.hasProtocol())
            throw ResourceLoaderException("Can't resolve relative URL from synthetic resource")
        val resolvedURL = URL(relativeURL)
        return Resource(resolvedURL, resolvedURL.toString().endsWith('/'), resourceLoader)
    }

    override fun load(): T = value

    override fun toString() = name

}
