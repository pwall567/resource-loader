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
import kotlin.test.fail

import java.io.File
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import java.nio.file.FileSystems

import io.kstuff.test.shouldBe
import io.kstuff.test.shouldBeNonNull
import io.kstuff.test.shouldBeOneOf
import io.kstuff.test.shouldEndWith
import io.kstuff.test.shouldStartWith
import io.kstuff.test.shouldThrow

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

class ResourceTest {

    @Test fun `should create resource using File`() {
        val resource = XMLLoader().resource(File("src/test/resources/xml/test1.xml"))
        val document = resource.load()
        document.documentElement.tagName shouldBe "test1"
    }

    @Test fun `should create ResourceLoader using Path`() {
        val resource = XMLLoader().resource(FileSystems.getDefault().getPath("src/test/resources/xml/test1.xml"))
        val document = resource.load()
        document.documentElement.tagName shouldBe "test1"
    }

    @Test fun `should create ResourceLoader using classpath`() {
        val url = ResourceTest::class.java.getResource("/xml/test1.xml") ?: fail("Can't locate resource")
        val resource = XMLLoader().resource(url)
        val document = resource.load()
        document.documentElement.tagName shouldBe "test1"
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

    @Test fun `should read from JAR file`() {
        val jarFile = File("src/test/resources/test.jar")
        val jarURL = URL("jar:${jarFile.absoluteFile.toURI()}!/xml/")
        val resource = XMLLoader().resource(jarURL)
        val resolved = resource.resolve("test1.xml")
        resolved.resourceURL shouldBe URL("jar:${jarFile.absoluteFile.toURI()}!/xml/test1.xml")
        val result = resolved.load()
        val root = result.documentElement
        root.tagName shouldBe "test"
        val sibling = resolved.resolve("test2.xml")
        sibling.resourceURL shouldBe URL("jar:${jarFile.absoluteFile.toURI()}!/xml/test2.xml")
        sibling.load().documentElement.tagName shouldBe "test2"
    }

    @Test fun `should display readable form of URL on toString`() {
        val resource1 = XMLLoader().resource(File("src/test/resources/xml/test1.xml"))
        resource1.toString() shouldBe "src/test/resources/xml/test1.xml"
        val resource2 = XMLLoader().resource(URL("http://kjson.io/xml/test9.xml"))
        resource2.toString() shouldBe "http://kjson.io/xml/test9.xml"
    }

    @Test fun `should get a classpath URL`() {
        val url = Resource.classPathURL("/xml/test1.xml") ?: fail("Can't locate resource")
        url.protocol shouldBeOneOf listOf("file", "jar")
        url.toString() shouldEndWith "/xml/test1.xml"
        val resource = XMLLoader().resource(url)
        val document = resource.load()
        document.documentElement.tagName shouldBe "test1"
    }

    @Test fun `check assumptions about URI and URL`() {
        val uri1 = URI("http://example.com/path/abc.xyz")
        val uri2 = uri1.resolve("def.xyz")
        uri2 shouldBe URI("http://example.com/path/def.xyz")

        val url1 = URL("http://example.com/path/abc.xyz")
        val url2 = URL(url1, "def.xyz")
        url2 shouldBe URL("http://example.com/path/def.xyz")

        val uri3 = URI("nonstd://example.com/path/abc.xyz")
        val uri4 = uri3.resolve("def.xyz")
        uri4 shouldBe URI("nonstd://example.com/path/def.xyz")

        shouldThrow<MalformedURLException>("unknown protocol: nonstd") { URL("nonstd://example.com/path/abc.xyz") }
        shouldThrow<MalformedURLException>("unknown protocol: nonstd") { uri3.toURL() }
    }

    @Test fun `should add request headers in connection filter`() {
        val xmlLoader = XMLLoader()
        xmlLoader.addAuthorizationFilter("localhost", "X-Test", "hippopotamus")
        embeddedServer(Netty, port = 8080) {
            routing {
                get("/testhdr.xml") {
                    val header = call.request.headers["X-Test"]
                    call.respondText("<test1>$header</test1>")
                }
            }
        }.start()
        val resource = xmlLoader.resource(URL("http://localhost:8080/testhdr.xml"))
        val document = resource.load()
        document.documentElement.tagName shouldBe "test1"
        document.documentElement.textContent shouldBe "hippopotamus"
    }

    @Test fun `should redirect request in connection filter`() {
        val xmlLoader = XMLLoader()
        xmlLoader.addRedirectionFilter(fromHost = "example.com", toHost = "localhost", toPort = 8081)
        embeddedServer(Netty, port = 8081) {
            routing {
                get("/test.xml") {
                    call.respondText("<test1>Redirect</test1>")
                }
            }
        }.start()
        val resource = xmlLoader.resource(URL("http://example.com/test.xml"))
        val document = resource.load()
        document.documentElement.tagName shouldBe "test1"
        document.documentElement.textContent shouldBe "Redirect"
    }

    @Test fun `should redirect http request to local file`() {
        val xmlLoader = XMLLoader()
        xmlLoader.addConnectionFilter(
            ResourceLoader.PrefixRedirectionFilter(
                fromPrefix = "http://example.com/",
                toPrefix = File("src/test/resources/xml").absoluteFile.toURI().toString(),
            )
        )
        val resource = xmlLoader.resource(URL("http://example.com/test1.xml"))
        val document = resource.load()
        document.documentElement.tagName shouldBe "test1"
        document.documentElement.textContent shouldBe "Hello!"
    }

}
