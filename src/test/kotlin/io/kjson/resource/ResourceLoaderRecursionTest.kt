/*
 * @(#) ResourceLoaderRecursionTest.kt
 *
 * resource-loader  Resource loading mechanism
 * Copyright (c) 2022 Peter Wall
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

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.fail

import java.net.URL
import java.nio.file.Path

class ResourceLoaderRecursionTest {

    data class Result(val value: String)

    class ResultLoader private constructor(resourcePath: Path?, resourceURL: URL) :
            ResourceLoader<Result, ResultLoader>(resourcePath, resourceURL) {

        constructor(resourceURL: URL) : this(derivePath(resourceURL), resourceURL)

        override fun load(rd: ResourceDescriptor): Result {
            load("test1.xml")
            fail("Shouldn't reach here")
        }

        override fun resolvedLoader(resourcePath: Path?, resourceURL: URL) = ResultLoader(resourcePath, resourceURL)

    }

    @Test fun `should recognise recursion`() {
        val loader = ResultLoader(ResourceLoader::class.java.getResource("/xml/") ?: fail("Can't find directory"))
        assertFailsWith<ResourceRecursionException> { loader.load("test1.xml") }
    }

}
