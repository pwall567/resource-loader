/*
 * @(#) Cache.kt
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

/**
 * A general-purpose cache that stores a value the first time it is created.
 *
 * @author  Peter Wall
 * @param   K       the key type
 * @param   V       the value type
 */
class Cache<K, V>(
    /** The initialisation function */
    private val init: (K) -> V
) {

    private val cache = mutableMapOf<K, V>()

    operator fun get(key: K): V = cache[key] ?: init(key).also { cache[key] = it }

    fun remove(key: K): V? = cache.remove(key)

    fun clear() {
        cache.clear()
    }

}
