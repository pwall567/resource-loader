/*
 * @(#) HTTPHeader.kt
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
 * Class to assist with parsing HTTP(S) header values.
 *
 * @author  Peter Wall
 */
class HTTPHeader(val elements: List<Element>) {

    fun element(index: Int = 0): Element = elements[index]

    fun firstElementText(): String = element().elementText()

    class Element(val values: List<String>) {

        fun elementText(): String = values.first()

        fun parameter(name: String, from: Int = 1): String? {
            for (i in from until values.size) {
                val value = values[i]
                val equalsSign = value.indexOf('=')
                if (equalsSign >= 0 && value.substring(0, equalsSign).trim() == name)
                    return value.substring(equalsSign + 1).trim()
            }
            return null
        }

    }

    companion object {

        fun parse(string: String): HTTPHeader {
            return HTTPHeader(string.split(',').map { Element(it.trim().split(';')) })
        }

    }

}
