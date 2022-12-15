/*
 * @(#) HTTPHeaderTest.kt
 *
 * resource-loader  Resource loading mechanism
 * Copyright (c) 2022 Peter Wall
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
import kotlin.test.expect

class HTTPHeaderTest {

    @Test fun `should create simple HTTPHeader`() {
        val contentTypeHeader = HTTPHeader.create("application/json")
        expect("application/json") { contentTypeHeader.firstElementText() }
    }

    @Test fun `should create HTTPHeader with parameter`() {
        val contentTypeHeader = HTTPHeader.create("application/json;charset=UTF-8")
        expect("application/json") { contentTypeHeader.firstElementText() }
        expect("UTF-8") { contentTypeHeader.element().parameter("charset") }
    }

    @Test fun `should create HTTPHeader with multiple elements`() {
        val acceptHeader = HTTPHeader.create("text/html; q=1.0, text/*; q=0.8, image/gif; q=0.6, image/jpeg; q=0.6," +
                " image/*; q=0.5, */*; q=0.1")
        expect(6) { acceptHeader.elements.size }
        expect("text/html") { acceptHeader.elements[0].values[0] }
        expect("1.0") { acceptHeader.elements[0].parameter("q") }
        with(acceptHeader.elements[1]) {
            expect("text/*") { elementText() }
            expect("0.8") { parameter("q") }
        }
        with(acceptHeader.elements[2]) {
            expect("image/gif") { elementText() }
            expect("0.6") { parameter("q") }
        }
        with(acceptHeader.elements[3]) {
            expect("image/jpeg") { elementText() }
            expect("0.6") { parameter("q") }
        }
        with(acceptHeader.elements[4]) {
            expect("image/*") { elementText() }
            expect("0.5") { parameter("q") }
        }
        with(acceptHeader.elements[5]) {
            expect("*/*") { elementText() }
            expect("0.1") { parameter("q") }
        }
    }

}
