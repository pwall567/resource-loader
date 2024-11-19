/*
 * @(#) CacheTest.kt
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.expect

class CacheTest {

    @Test fun `should store item in cache`() {
        var used = false
        val cache = Cache<String, Int> {
            check(!used) { "Called again" }
            used = true
            it.toInt()
        }
        expect(12345) { cache["12345"] }
        expect(12345) { cache["12345"] }
    }

    @Test fun `should store null in cache`() {
        var used = false
        val cache = Cache<String, Int?> {
            check(!used) { "Called again" }
            used = true
            null
        }
        assertNull(cache["12345"])
        assertNull(cache["12345"])
    }

    @Test fun `should remove item from cache`() {
        var used = false
        val cache = Cache<String, Int> {
            check(!used) { "Called again" }
            used = true
            it.toInt()
        }
        expect(12345) { cache["12345"] }
        expect(12345) { cache["12345"] }
        expect(12345) { cache.remove("12345") }
        assertFailsWith<IllegalStateException> { expect(12345) { cache["12345"] } }.let {
            expect("Called again") { it.message }
        }
    }

    @Test fun `should clear cache`() {
        var used = false
        val cache = Cache<String, Int> {
            check(!used) { "Called again" }
            used = true
            it.toInt()
        }
        expect(12345) { cache["12345"] }
        expect(12345) { cache["12345"] }
        cache.clear()
        assertFailsWith<IllegalStateException> { expect(12345) { cache["12345"] } }.let {
            expect("Called again") { it.message }
        }
    }

}
