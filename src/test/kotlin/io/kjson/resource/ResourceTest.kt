/*
 * @(#) ResourceLoaderTest.kt
 *
 * resource-loader  Resource loading mechanism
 * Copyright (c) 2021, 2024 Peter Wall
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

import java.io.File
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import java.nio.file.FileSystems

import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

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
        val url = ResourceTest::class.java.getResource("/xml/test1.xml")
        assertNotNull(url)
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
        expect(URL("jar:file://${jarFile.absolutePath}!/xml/test1.xml")) { resolved.resourceURL }
        val result = resolved.load()
        val root = result.documentElement
        expect("test") { root.tagName }
        val sibling = resolved.resolve("test2.xml")
        expect(URL("jar:file://${jarFile.absolutePath}!/xml/test2.xml")) { sibling.resourceURL }
        expect("test2") { sibling.load().documentElement.tagName }
    }

    @Test fun `should display readable form of URL on toString`() {
        val resource1 = XMLLoader.resource(File("src/test/resources/xml/test1.xml"))
        expect("src/test/resources/xml/test1.xml") { resource1.toString() }
        val resource2 = XMLLoader.resource(URL("http://kjson.io/xml/test9.xml"))
        expect("http://kjson.io/xml/test9.xml") { resource2.toString() }
    }

    @Test fun `should get a classpath URL`() {
        val url = Resource.classPathURL("/xml/test1.xml")
        assertNotNull(url)
        assertTrue(url.protocol == "file" || url.protocol == "jar")
        assertTrue(url.toString().endsWith("/xml/test1.xml"))
        val resource = XMLLoader.resource(url)
        val document = resource.load()
        expect("test1") { document.documentElement.tagName }
    }

    @Test fun `check assumptions about URI and URL`() {
        val uri1 = URI("http://example.com/path/abc.xyz")
        val uri2 = uri1.resolve("def.xyz")
        expect(URI("http://example.com/path/def.xyz")) { uri2 }

        val url1 = URL("http://example.com/path/abc.xyz")
        val url2 = URL(url1, "def.xyz")
        expect(URL("http://example.com/path/def.xyz")) { url2 }

        val uri3 = URI("nonstd://example.com/path/abc.xyz")
        val uri4 = uri3.resolve("def.xyz")
        expect(URI("nonstd://example.com/path/def.xyz")) { uri4 }

        assertFailsWith<MalformedURLException> { URL("nonstd://example.com/path/abc.xyz") }.let {
            expect("unknown protocol: nonstd") { it.message }
        }
        assertFailsWith<MalformedURLException> { uri3.toURL() }.let {
            expect("unknown protocol: nonstd") { it.message }
        }
    }

    @Test fun `should add request headers in connection filter`() {
        XMLLoader.addConnectionFilter(ResourceLoader.AuthorizationFilter("localhost", "X-Test", "hippopotamus"))
        embeddedServer(Netty, port = 8080) {
            routing {
                get("/testhdr.xml") {
                    val header = call.request.headers["X-Test"]
                    call.respondText("<test1>$header</test1>")
                }
            }
        }.start()
        val resource = XMLLoader.resource(URL("http://localhost:8080/testhdr.xml"))
        val document = resource.load()
        expect("test1") { document.documentElement.tagName }
        expect("hippopotamus") { document.documentElement.textContent }
    }

}
