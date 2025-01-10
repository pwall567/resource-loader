/*
 * @(#) ResourceLoaderTest.kt
 *
 * resource-loader  Resource loading mechanism
 * Copyright (c) 2021, 2024, 2025 Peter Wall
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
import kotlin.test.fail

import java.io.File
import java.net.URL

import io.kstuff.test.shouldBe
import io.kstuff.test.shouldBeOneOf
import io.kstuff.test.shouldEndWith

class ResourceTest {

    @Test fun `should display readable form of URL on toString`() {
        val resource1 = XMLLoader().resource(File("src/test/resources/xml/test.xml"))
        resource1.toString() shouldBe "src/test/resources/xml/test.xml".split('/').joinToString(File.separator)
        val resource2 = XMLLoader().resource(URL("http://kjson.io/xml/test9.xml"))
        resource2.toString() shouldBe "http://kjson.io/xml/test9.xml"
    }

    @Test fun `should get a classpath URL`() {
        val url = Resource.classPathURL("/xml/test2.xml") ?: fail("Can't locate resource")
        url.protocol shouldBeOneOf listOf("file", "jar")
        url.toString() shouldEndWith "/xml/test2.xml"
        val resource = XMLLoader().resource(url)
        val document = resource.load()
        document.documentElement.tagName shouldBe "test2"
    }

}
