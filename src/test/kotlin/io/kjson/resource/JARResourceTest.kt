/*
 * @(#) JARResourceTest.kt
 *
 * resource-loader  Resource loading mechanism
 * Copyright (c) 2025 Peter Wall
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

import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

import io.kstuff.test.shouldBe
import io.kstuff.test.shouldBeType

class JARResourceTest {

    init {
        createJAR() // create the JAR file used in some of the following tests
    }

    @Test fun `should read from JAR file`() {
        println(jarFile.absoluteFile.toURI())
        val jarURL = URL("jar:${jarFile.absoluteFile.toURI()}!/xml/")
        val resource = XMLLoader().resource(jarURL)
        val resolved = resource.resolve("jar-file-1.xml")
        resolved.resourceURL shouldBe URL("jar:${jarFile.absoluteFile.toURI()}!/xml/jar-file-1.xml")
        val result = resolved.load()
        val root = result.documentElement
        root.tagName shouldBe "jar-test-1"
        val sibling = resolved.resolve("jar-file-2.xml")
        sibling.resourceURL shouldBe URL("jar:${jarFile.absoluteFile.toURI()}!/xml/jar-file-2.xml")
        sibling.load().documentElement.tagName shouldBe "jar-test-2"
    }

    @Test fun `should resolve relative URL to file`() {
        val resource = JARResource(jarURLString, emptyArray(), true, xmlLoader)
        resource.resourceURL.toString() shouldBe "jar:$jarURLString!/"
        with(resource.resolve("file1.xml")) {
            shouldBeType<JARResource<*>>()
            isDirectory shouldBe false
            resourceURL.toString() shouldBe "jar:$jarURLString!/file1.xml"
        }
        with(resource.resolve("file2.xml")) {
            shouldBeType<JARResource<*>>()
            isDirectory shouldBe false
            resourceURL.toString() shouldBe "jar:$jarURLString!/file2.xml"
        }
    }

    @Test fun `should resolve relative URL to subdirectory`() {
        val resource = JARResource(jarURLString, emptyArray(), true, xmlLoader)
        resource.resourceURL.toString() shouldBe "jar:$jarURLString!/"
        with(resource.resolve("abc/")) {
            shouldBeType<JARResource<*>>()
            isDirectory shouldBe true
            resourceURL.toString() shouldBe "jar:$jarURLString!/abc/"
        }
        with(resource.resolve("/xyz/g/")) {
            shouldBeType<JARResource<*>>()
            isDirectory shouldBe true
            resourceURL.toString() shouldBe "jar:$jarURLString!/xyz/g/"
        }
    }

    @Test fun `should resolve relative URL from file to file`() {
        val resource = JARResource(jarURLString, arrayOf("dir1", "file1.xml"), false, xmlLoader)
        resource.resourceURL.toString() shouldBe "jar:$jarURLString!/dir1/file1.xml"
        with(resource.resolve("file2.xml")) {
            shouldBeType<JARResource<*>>()
            isDirectory shouldBe false
            resourceURL.toString() shouldBe "jar:$jarURLString!/dir1/file2.xml"
        }
        with(resource.resolve("/file3.xml")) {
            shouldBeType<JARResource<*>>()
            isDirectory shouldBe false
            resourceURL.toString() shouldBe "jar:$jarURLString!/file3.xml"
        }
    }

    @Test fun `should resolve relative URL from subdirectory to file`() {
        val resource = JARResource(jarURLString, arrayOf("dir1", "dir2"), true, xmlLoader)
        resource.resourceURL.toString() shouldBe "jar:$jarURLString!/dir1/dir2/"
        with(resource.resolve("file1.xml")) {
            shouldBeType<JARResource<*>>()
            isDirectory shouldBe false
            resourceURL.toString() shouldBe "jar:$jarURLString!/dir1/dir2/file1.xml"
        }
        with(resource.resolve("/file3.xml")) {
            shouldBeType<JARResource<*>>()
            isDirectory shouldBe false
            resourceURL.toString() shouldBe "jar:$jarURLString!/file3.xml"
        }
    }

    @Suppress("ConstPropertyName")
    companion object {

        val xmlLoader = XMLLoader()
        private val jarDirectory = File("target/test")
        val jarFile = File(jarDirectory, "test.jar")

        private const val jarFile1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<jar-test-1>Hello jar</jar-test-1>\n"
        private const val jarFile2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<jar-test-2>Kia ora</jar-test-2>\n"

        val jarURLString = "file:${jarFile.absolutePath}"

        fun createJAR() {
            val manifest = Manifest()
            manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            jarDirectory.mkdirs()
            JarOutputStream(FileOutputStream(jarFile), manifest).use { jarOutputStream ->
                jarOutputStream.addFile("/xml/jar-file-1.xml", jarFile1.encodeToByteArray())
                jarOutputStream.addFile("/xml/jar-file-2.xml", jarFile2.encodeToByteArray())
            }
        }

        private fun JarOutputStream.addFile(name: String, bytes: ByteArray) {
            putNextEntry(JarEntry(name).apply { time = System.currentTimeMillis() })
            write(bytes)
            closeEntry()
        }

    }

}
