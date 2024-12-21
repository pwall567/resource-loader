/*
 * @(#) HTTPHeaderTest.kt
 *
 * resource-loader  Resource loading mechanism
 * Copyright (c) 2022, 2024 Peter Wall
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

package io.kjson.util

import kotlin.test.Test

import io.kstuff.test.shouldBe

class HTTPHeaderTest {

    @Test fun `should create simple HTTPHeader`() {
        val contentTypeHeader = HTTPHeader.parse("application/json")
        contentTypeHeader.firstElementText() shouldBe "application/json"
    }

    @Test fun `should create HTTPHeader with parameter`() {
        val contentTypeHeader = HTTPHeader.parse("application/json;charset=UTF-8")
        contentTypeHeader.firstElementText() shouldBe "application/json"
        contentTypeHeader.element().parameter("charset") shouldBe "UTF-8"
    }

    @Test fun `should create HTTPHeader with multiple elements`() {
        val acceptHeader = HTTPHeader.parse("text/html; q=1.0, text/*; q=0.8, image/gif; q=0.6, image/jpeg; q=0.6," +
                " image/*; q=0.5, */*; q=0.1")
        acceptHeader.elements.size shouldBe 6
        acceptHeader.elements[0].values[0] shouldBe "text/html"
        acceptHeader.elements[0].parameter("q") shouldBe "1.0"
        with(acceptHeader.elements[1]) {
            elementText() shouldBe "text/*"
            parameter("q") shouldBe "0.8"
        }
        with(acceptHeader.elements[2]) {
            elementText() shouldBe "image/gif"
            parameter("q") shouldBe "0.6"
        }
        with(acceptHeader.elements[3]) {
            elementText() shouldBe "image/jpeg"
            parameter("q") shouldBe "0.6"
        }
        with(acceptHeader.elements[4]) {
            elementText() shouldBe "image/*"
            parameter("q") shouldBe "0.5"
        }
        with(acceptHeader.elements[5]) {
            elementText() shouldBe "*/*"
            parameter("q") shouldBe "0.1"
        }
    }

}
