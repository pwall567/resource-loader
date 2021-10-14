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
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.expect
import kotlin.test.fail

import java.io.File
import java.net.URL
import java.nio.file.FileSystems
import io.kjson.resource.ResourceLoader.Companion.strim

class ResourceLoaderTest {

    @Test fun `should add extension only when required`() {
        val defaultExtension = "html"
        expect("a.html") { ResourceLoader.addExtension("a", defaultExtension) }
        expect("a.xml") { ResourceLoader.addExtension("a.xml", defaultExtension) }
        expect("/b/a.html") { ResourceLoader.addExtension("/b/a", defaultExtension) }
        expect("/b/a.xml") { ResourceLoader.addExtension("/b/a.xml", defaultExtension) }
        expect("/b.c/a.html") { ResourceLoader.addExtension("/b.c/a", defaultExtension) }
        expect("/b.c/a.xml") { ResourceLoader.addExtension("/b.c/a.xml", defaultExtension) }
    }

    @Test fun `should trim strings correctly`() {
        expect("abc") { "abc".strim() }
        expect("abc") { " abc".strim() }
        expect("abc") { "  abc".strim() }
        expect("abc") { "abc ".strim() }
        expect("abc") { "abc  ".strim() }
        expect("abc") { "    abc     ".strim() }
        expect("") { "".strim() }
        expect("") { " ".strim() }
        expect("") { "    ".strim() }
        val a = "xyz"
        assertSame(a, a.strim())
    }

    @Test fun `should read explicitly specified file`() {
        val parser = XMLLoader()
        val result = parser.load("src/test/resources/xml/test1.xml")
        expect("Hello!") { result.getElementsByTagName("test")?.item(0)?.textContent }
    }

    @Test fun `should read second explicitly specified file`() {
        val parser = XMLLoader()
        val parser2 = parser.resolve("src/test/resources/xml/test1.xml")
        val result = parser2.load()
        expect("Hello!") { result.getElementsByTagName("test")?.item(0)?.textContent }
        val parser3 = parser2.resolve("test2")
        val result2 = parser3.load()
        expect("\u00A1Hol\u00E1!") { result2.getElementsByTagName("test2")?.item(0)?.textContent }
    }

    @Test fun `should read from classpath`() {
        val parser = XMLLoader(ResourceLoaderTest::class.java.getResource("/xml/") ?: fail("Can't find directory"))
        val result = parser.load("test1")
        val root = result.documentElement
        expect("test") { root.tagName }
    }

    @Test fun `should read second file from classpath`() {
        val parser = XMLLoader(ResourceLoaderTest::class.java.getResource("/xml/") ?: fail("Can't find directory"))
        val parser2 = parser.resolve("test1.xml")
        val result = parser2.load()
        expect("Hello!") { result.getElementsByTagName("test")?.item(0)?.textContent }
        val parser3 = parser2.resolve("test2.xml")
        val result2 = parser3.load()
        expect("\u00A1Hol\u00E1!") { result2.getElementsByTagName("test2")?.item(0)?.textContent }
    }

    @Test fun `should read from directory`() {
        val parser = XMLLoader(File("src/test/resources/xml/"))
        val result = parser.load("test1.xml")
        expect("Hello!") { result.getElementsByTagName("test")?.item(0)?.textContent }
    }

    @Test fun `should read second file from directory`() {
        val parser = XMLLoader(File("src/test/resources/xml/"))
        val parser2 = parser.resolve("test1.xml")
        val result = parser2.load()
        expect("Hello!") { result.getElementsByTagName("test")?.item(0)?.textContent }
        val parser3 = parser2.resolve("test2.xml")
        val result2 = parser3.load()
        expect("\u00A1Hol\u00E1!") { result2.getElementsByTagName("test2")?.item(0)?.textContent }
    }

    @Test fun `should read from directory as Path`() {
        val parser = XMLLoader(FileSystems.getDefault().getPath("src/test/resources/xml/"))
        val result = parser.load("test1.xml")
        val root = result.documentElement
        expect("test") { root.tagName }
    }

    @Test fun `should read from URL`() {
        val parser = XMLLoader(URL("http://kjson.io/xml/"))
        val result = parser.load("test1.xml")
        val root = result.documentElement
        expect("test1") { root.tagName }
    }

    @Test fun `should switch from local file to remote URL`() {
        val parser = XMLLoader(File("src/test/resources/xml/"))
        val result = parser.load("test1.xml")
        expect("Hello!") { result.getElementsByTagName("test")?.item(0)?.textContent }
        val result2 = parser.load("http://kjson.io/xml/test1.xml")
        expect("Testing") { result2.getElementsByTagName("test1")?.item(0)?.textContent }
    }

    @Test fun `should throw not found exception when resource not found`() {
        val parser = XMLLoader(File("src/test/resources/xml/"))
        with(assertFailsWith<ResourceNotFoundException> { parser.load("test9.xml") }.message) {
            assertNotNull(this)
            assertTrue { startsWith("Resource not found - ") }
            assertTrue { endsWith("/test9.xml") }
        }
    }

    @Test fun `should throw not found exception when remote URL not found`() {
        val parser = XMLLoader(URL("http://kjson.io/xml/"))
        with(assertFailsWith<ResourceNotFoundException> { parser.load("test9.xml") }.message) {
            assertNotNull(this)
            assertTrue { startsWith("Resource not found - ") }
            assertTrue { endsWith("/test9.xml") }
        }
    }

}
