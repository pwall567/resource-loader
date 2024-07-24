/*
 * @(#) ResourceDescriptorTest.kt
 *
 * resource-loader  Resource loading mechanism
 * Copyright (c) 2024 Peter Wall
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
import kotlin.test.expect

import java.io.ByteArrayInputStream
import java.net.URL

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

class ResourceDescriptorTest {

    private val byteArray = byteArrayOf(0xE2.toByte(), 0x82.toByte(), 0xAC.toByte(), 50, 48, 48, 46, 48, 48)
    private val dummyURL = URL("http://example.com")

    @Test fun `should read using DynamicReader`() {
        val rd = ResourceDescriptor(
            inputStream = ByteArrayInputStream(byteArray),
            url = dummyURL,
        )
        val text = rd.getReader().readText()
        expect("\u20AC200.00") { text }
    }

    @Test fun `should use DynamicReader in ktor environment`() = testApplication {
        // For reasons not yet understood, the creation of a DynamicReader object (more specifically the creation of the
        // DynamicDecoder used by that class) would fail with a java.lang.VerifyError: Cannot inherit from final class.
        // This only happened when running under ktor, and it is possible that that system adds some additional (and in
        // this case erroneous) class verification.  This test attempts to replicate that issue.
        application {
            routing {
                get("/test") {
                    val rd = ResourceDescriptor(
                        inputStream = ByteArrayInputStream(byteArray),
                        url = dummyURL,
                    )
                    val text = rd.getReader().readText()
                    call.respond(text)
                }
            }
        }
        val response = client.get("/test")
        expect(HttpStatusCode.OK) { response.status }
        expect("\u20AC200.00") { response.bodyAsText() }
    }

}
