/*
 * @(#) HTTPResourceTest.kt
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

import kotlin.test.Test

import java.io.File
import java.net.URL

import io.kstuff.test.shouldBe
import io.kstuff.test.shouldBeNonNull
import io.kstuff.test.shouldEndWith
import io.kstuff.test.shouldStartWith
import io.kstuff.test.shouldThrow

class HTTPResourceTest {

    @Test fun `should read from remote URL`() {
        val resource = XMLLoader().resource(URL("http://kjson.io/xml/"))
        val resolved = resource.resolve("test1.xml")
        resolved.resourceURL.toString() shouldBe "http://kjson.io/xml/test1.xml"
        resolved.load().documentElement.tagName shouldBe "test"
    }

    @Test fun `should switch from local file to remote URL`() {
        val resource = XMLLoader().resource(File("src/test/resources/xml/"))
        resource.resolve("test1.xml").load().documentElement.tagName shouldBe "test1"
        resource.resolve("http://kjson.io/xml/test1.xml").load().documentElement.tagName shouldBe "test"
    }

    @Test fun `should throw not-found exception when remote URL not found`() {
        shouldThrow<ResourceNotFoundException> {
            XMLLoader().resource(URL("http://kjson.io/xml/test9.xml")).load()
        }.let {
            with(it.message.shouldBeNonNull()) {
                this shouldStartWith "Resource not found - "
                this shouldEndWith "xml/test9.xml"
            }
        }
    }

}
