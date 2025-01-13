/*
 * @(#) ResourceLoaderTest.kt
 *
 * resource-loader  Resource loading mechanism
 * Copyright (c) 2024, 2025 Peter Wall
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
import io.kstuff.test.shouldEndWith
import io.kstuff.test.shouldStartWith

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

import io.kjson.resource.ResourceTest.Companion.pathOf
import net.pwall.text.Wildcard

class ResourceLoaderTest {

    @Test fun `should create ResourceLoader with default base URL`() {
        val xmlLoader = XMLLoader()
        xmlLoader.baseURL.toString() shouldStartWith "file:"
        with(xmlLoader.load("src/test/resources/xml/test1.xml")) {
            documentElement.tagName shouldBe "test1"
        }
    }

    @Test fun `should create ResourceLoader with specified base URL`() {
        val xmlLoader = XMLLoader(Resource.classPathURL("/xml/") ?: fail("Can't locate directory"))
        with(xmlLoader.load("test1.xml")) {
            documentElement.tagName shouldBe "test1"
        }
        val url = File("src/test/resources/xml/test2.xml").toURI().toURL()
        with(xmlLoader.load(url)) {
            documentElement.tagName shouldBe "test2"
        }
    }

    @Test fun `should set isDirectory correctly for file URL`() {
        val dirResource = XMLLoader().resource(File("src/test/resources/xml"))
        dirResource.isDirectory shouldBe true
        dirResource.resolve("test1.xml").toString() shouldEndWith pathOf("src", "test", "resources", "xml", "test1.xml")
        val fileResource = XMLLoader().resource(File("src/test/resources/xml/test1.xml"))
        fileResource.isDirectory shouldBe false
    }

    @Test fun `should create AuthorizationFilter`() {
        val filter = ResourceLoader.AuthorizationFilter(Wildcard("test*"), "Test", "me")
        filter.hostWildcard.matches("test1") shouldBe true
        filter.headerName shouldBe "Test"
        filter.headerValue shouldBe "me"
    }

    @Test fun `should create RedirectionFilter`() {
        val filter = ResourceLoader.RedirectionFilter("example.com", 80, "localhost", 8080)
        filter.fromHost shouldBe "example.com"
        filter.fromPort shouldBe 80
        filter.toHost shouldBe "localhost"
        filter.toPort shouldBe 8080
    }

    @Test fun `should create PrefixRedirectionFilter`() {
        val filter = ResourceLoader.PrefixRedirectionFilter("example.com","localhost")
        filter.fromPrefix shouldBe "example.com"
        filter.toPrefix shouldBe "localhost"
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
