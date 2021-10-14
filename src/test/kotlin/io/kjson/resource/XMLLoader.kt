/*
 * @(#) XMLLoader.kt
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

import java.io.File
import java.net.URL
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

import org.w3c.dom.Document
import org.xml.sax.InputSource

class XMLLoader private constructor(resourcePath: Path?, resourceURL: URL) :
        ResourceLoader<Document, XMLLoader>(resourcePath, resourceURL) {

    constructor(resourceFile: File = currentDirectory) : this(resourceFile.toPath(), resourceFile.toURI().toURL())

    constructor(resourcePath: Path) : this(resourcePath, resourcePath.toUri().toURL())

    constructor(resourceURL: URL) : this(derivePath(resourceURL), resourceURL)

    override val defaultExtension = "xml"

    override fun load(rd: ResourceDescriptor): Document {
        val inputSource = InputSource(rd.inputStream)
        inputSource.systemId = resourceURL.toString()
        rd.charsetName?.let { inputSource.encoding = it }
        return getDocumentBuilder().parse(inputSource)
    }

    override fun resolvedLoader(resourcePath: Path?, resourceURL: URL) = XMLLoader(resourcePath, resourceURL)

    companion object {

        private val documentBuilderFactory: DocumentBuilderFactory by lazy {
            DocumentBuilderFactory.newInstance()
        }

        private fun getDocumentBuilder(): DocumentBuilder = documentBuilderFactory.newDocumentBuilder()

    }

}
