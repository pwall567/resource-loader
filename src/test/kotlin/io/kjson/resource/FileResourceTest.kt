/*
 * @(#) FileResourceTest.kt
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
import kotlin.test.fail

import java.io.File
import java.nio.file.FileSystems

import io.kstuff.test.shouldBe
import io.kstuff.test.shouldBeNonNull
import io.kstuff.test.shouldEndWith
import io.kstuff.test.shouldStartWith
import io.kstuff.test.shouldThrow

class FileResourceTest {

    @Test fun `should create resource using File`() {
        val resource = XMLLoader().resource(File("src/test/resources/xml/test1.xml"))
        val document = resource.load()
        document.documentElement.tagName shouldBe "test1"
    }

    @Test fun `should create resource using Path`() {
        val resource = XMLLoader().resource(FileSystems.getDefault().getPath("src/test/resources/xml/test1.xml"))
        val document = resource.load()
        document.documentElement.tagName shouldBe "test1"
    }

    @Test fun `should create resource using URL`() {
        val url = File("src/test/resources/xml/test1.xml").toURI().toURL()
        val resource = XMLLoader().resource(url)
        val document = resource.load()
        document.documentElement.tagName shouldBe "test1"
    }

    @Test fun `should create resource using classpath`() {
        val url = ResourceTest::class.java.getResource("/xml/test2.xml") ?: fail("Can't locate resource")
        val resource = XMLLoader().resource(url)
        val document = resource.load()
        document.documentElement.tagName shouldBe "test2"
    }

    @Test fun `should resolve sibling`() {
        val resource = XMLLoader().resource(File("src/test/resources/xml/test1.xml"))
        with(resource.load()) {
            documentElement.tagName shouldBe "test1"
        }
        with(resource.resolve("test2.xml").load()) {
            documentElement.tagName shouldBe "test2"
        }
    }

    @Test fun `should resolve child`() {
        val resource = XMLLoader().resource(File("src/test/resources/xml/"))
        with(resource.resolve("test1.xml").load()) {
            documentElement.tagName shouldBe "test1"
        }
        with(resource.resolve("test2.xml").load()) {
            documentElement.tagName shouldBe "test2"
        }
    }

    @Test fun `should throw not-found exception when resource not found`() {
        shouldThrow<ResourceNotFoundException> {
            XMLLoader().resource(File("src/test/resources/xml/test9.xml")).load()
        }.let {
            with(it.message.shouldBeNonNull()) {
                this shouldStartWith "Resource not found - "
                this shouldEndWith "src/test/resources/xml/test9.xml"
            }
        }
    }

}
