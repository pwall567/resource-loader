/*
 * @(#) ResourceLoaderTest.kt
 *
 * resource-loader  Resource loading mechanism
 * Copyright (c) 2021 Peter Wall
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.expect
import kotlin.test.fail

import java.io.File
import java.net.URL
import java.nio.file.FileSystems

class ResourceTest {

    @Test fun `should create resource using File`() {
        val resource = XMLLoader.resource(File("src/test/resources/xml/test1.xml"))
        val document = resource.load()
        expect("test1") { document.documentElement.tagName }
    }

    @Test fun `should create ResourceLoader using Path`() {
        val resource = XMLLoader.resource(FileSystems.getDefault().getPath("src/test/resources/xml/test1.xml"))
        val document = resource.load()
        expect("test1") { document.documentElement.tagName }
    }

    @Test fun `should create ResourceLoader using classpath`() {
        val url = Resource.classPathURL("/xml/test1.xml") ?: fail("Resource not found")
        val resource = XMLLoader.resource(url)
        val document = resource.load()
        expect("test1") { document.documentElement.tagName }
    }

    @Test fun `should resolve sibling`() {
        val resource = XMLLoader.resource(File("src/test/resources/xml/test1.xml"))
        with(resource.load()) {
            expect("test1") { documentElement.tagName }
        }
        with(resource.resolve("test2.xml").load()) {
            expect("test2") { documentElement.tagName }
        }
    }

    @Test fun `should resolve child`() {
        val resource = XMLLoader.resource(File("src/test/resources/xml/"))
        with(resource.resolve("test1.xml").load()) {
            expect("test1") { documentElement.tagName }
        }
        with(resource.resolve("test2.xml").load()) {
            expect("test2") { documentElement.tagName }
        }
    }

    @Test fun `should read from remote URL`() {
        val resource = XMLLoader.resource(URL("http://kjson.io/xml/"))
        val resolved = resource.resolve("test1.xml")
        expect("http://kjson.io/xml/test1.xml") { resolved.resourceURL.toString() }
        expect("test") { resolved.load().documentElement.tagName }
    }

    @Test fun `should switch from local file to remote URL`() {
        val resource = XMLLoader.resource(File("src/test/resources/xml/"))
        expect("test1") { resource.resolve("test1.xml").load().documentElement.tagName }
        expect("test") { resource.resolve("http://kjson.io/xml/test1.xml").load().documentElement.tagName }
    }

    @Test fun `should throw not-found exception when resource not found`() {
        assertFailsWith<ResourceNotFoundException> {
            XMLLoader.resource(File("src/test/resources/xml/test9.xml")).load()
        }.let {
            with(it.message) {
                assertNotNull(this)
                assertTrue(startsWith("Resource not found - "))
                assertTrue(endsWith("src/test/resources/xml/test9.xml"))
            }
        }
    }

    @Test fun `should throw not-found exception when remote URL not found`() {
        assertFailsWith<ResourceNotFoundException> {
            XMLLoader.resource(URL("http://kjson.io/xml/test9.xml")).load()
        }.let {
            with(it.message) {
                assertNotNull(this)
                assertTrue(startsWith("Resource not found - "))
                assertTrue(endsWith("xml/test9.xml"))
            }
        }
    }

    @Test fun `should read from JAR file`() {
        val jarFile = File("src/test/resources/test.jar")
        val jarURL = URL("jar:file://${jarFile.absolutePath}!/xml/")
        val resource = XMLLoader.resource(jarURL)
        val resolved = resource.resolve("test1.xml")
        val result = resolved.load()
        val root = result.documentElement
        expect("test") { root.tagName }
        val sibling = resolved.resolve("test2.xml")
        expect("test2") { sibling.load().documentElement.tagName }
    }

}
