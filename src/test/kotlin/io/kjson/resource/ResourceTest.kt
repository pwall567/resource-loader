/*
 * @(#) ResourceLoaderTest.kt
 *
 * resource-loader  Resource loading mechanism
 * Copyright (c) 2021, 2024, 2025 Peter Wall
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
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.FileSystems
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

import io.kstuff.test.shouldBe
import io.kstuff.test.shouldBeNonNull
import io.kstuff.test.shouldBeOneOf
import io.kstuff.test.shouldEndWith
import io.kstuff.test.shouldStartWith
import io.kstuff.test.shouldThrow

import io.kjson.resource.Resource.Companion.hasProtocol
import io.kjson.resource.Resource.Companion.relevantHashCode
import io.kjson.resource.Resource.Companion.sameAs

class ResourceTest {

    init {
        createJAR() // create the JAR file used in some of the following tests
    }

    @Test fun `should create FileResource from URL for directory`() {
        val url = File("src/test/resources/xml").toURI().toURL()
        val fileResource = xmlLoader.resource(url)
        fileResource.toString() shouldBe pathOf("src", "test", "resources", "xml") +
                if (File.separatorChar == '/') "/" else ""
        fileResource.isDirectory shouldBe true
    }

    @Test fun `should create FileResource from URL for file`() {
        val url = File("src/test/resources/xml/test1.xml").toURI().toURL()
        val fileResource = xmlLoader.resource(url)
        fileResource.toString() shouldBe pathOf("src", "test", "resources", "xml", "test1.xml")
        fileResource.isDirectory shouldBe false
    }

    @Test fun `should create and load resource using File`() {
        val resource = xmlLoader.resource(File("src/test/resources/xml/test1.xml"))
        val document = resource.load()
        document.documentElement.tagName shouldBe "test1"
    }

    @Test fun `should create and load resource using Path`() {
        val resource = xmlLoader.resource(FileSystems.getDefault().getPath("src/test/resources/xml/test1.xml"))
        val document = resource.load()
        document.documentElement.tagName shouldBe "test1"
    }

    @Test fun `should create and load resource using file URL`() {
        val url = File("src/test/resources/xml/test1.xml").toURI().toURL()
        val resource = xmlLoader.resource(url)
        val document = resource.load()
        document.documentElement.tagName shouldBe "test1"
    }

    @Test fun `should create and load resource using classpath`() {
        val url = ResourceTest::class.java.getResource("/xml/test2.xml") ?: fail("Can't locate resource")
        val resource = xmlLoader.resource(url)
        val document = resource.load()
        document.documentElement.tagName shouldBe "test2"
    }

    @Test fun `should resolve and load sibling for File resource`() {
        val resource = xmlLoader.resource(File("src/test/resources/xml/test1.xml"))
        with(resource.load()) {
            documentElement.tagName shouldBe "test1"
        }
        with(resource.resolve("test2.xml").load()) {
            documentElement.tagName shouldBe "test2"
        }
    }

    @Test fun `should resolve and load child for File resource`() {
        val resource = xmlLoader.resource(File("src/test/resources/xml/"))
        with(resource.resolve("test1.xml").load()) {
            documentElement.tagName shouldBe "test1"
        }
        with(resource.resolve("test2.xml").load()) {
            documentElement.tagName shouldBe "test2"
        }
    }

    @Test fun `should apply default extension when reading from File resource`() {
        val resource = xmlLoader.resource(File("src/test/resources/xml/test1"))
        resource.load().documentElement.tagName shouldBe "test1"
    }

    @Test fun `should throw not-found exception when File resource not found`() {
        shouldThrow<ResourceNotFoundException> {
            xmlLoader.resource(File("src/test/resources/xml/test9.xml")).load()
        }.let {
            with(it.message.shouldBeNonNull()) {
                this shouldBe "Resource not found - " + pathOf("src", "test", "resources", "xml", "test9.xml")
            }
        }
    }

    @Test fun `should read from remote URL`() {
        val resource = xmlLoader.resource(URL("http://kjson.io/xml/"))
        val resolved = resource.resolve("test1.xml")
        resolved.toString() shouldBe "http://kjson.io/xml/test1.xml"
        resolved.load().documentElement.tagName shouldBe "test"
    }

    @Test fun `should apply default extension when reading from remote URL`() {
        val resource = xmlLoader.resource(URL("http://kjson.io/xml/test1"))
        resource.load().documentElement.tagName shouldBe "test"
    }

    @Test fun `should switch from local file to remote URL`() {
        val resource = xmlLoader.resource(File("src/test/resources/xml/"))
        resource.resolve("test1.xml").load().documentElement.tagName shouldBe "test1"
        resource.resolve("http://kjson.io/xml/test1.xml").load().documentElement.tagName shouldBe "test"
    }

    @Test fun `should throw not-found exception when remote URL not found`() {
        shouldThrow<ResourceNotFoundException> {
            xmlLoader.resource(URL("http://kjson.io/xml/test9.xml")).load()
        }.let {
            with(it.message.shouldBeNonNull()) {
                this shouldStartWith "Resource not found - "
                this shouldEndWith "xml/test9.xml"
            }
        }
    }

    @Test fun `should create resource for directory within JAR`() {
        val jarURL = URL("jar:${jarFile.absoluteFile.toURI()}!/xml/")
        val jarResource = xmlLoader.resource(jarURL)
        jarResource.toString() shouldBe jarURL.toString()
        jarResource.isDirectory shouldBe true
    }

    @Test fun `should create resource for file within JAR`() {
        val jarURL = URL("jar:${jarFile.absoluteFile.toURI()}!/xml/jar-file-1.xml")
        val jarResource = xmlLoader.resource(jarURL)
        jarResource.toString() shouldBe jarURL.toString()
        jarResource.isDirectory shouldBe false
        jarResource.load().documentElement.tagName shouldBe "jar-test-1"
    }

    @Test fun `should read from JAR file`() {
        val jarURL = URL("jar:${jarFile.absoluteFile.toURI()}!/xml/")
        val resource = xmlLoader.resource(jarURL)
        val resolved = resource.resolve("jar-file-1.xml")
        resolved.url shouldBe URL("jar:${jarFile.absoluteFile.toURI()}!/xml/jar-file-1.xml")
        resolved.load().documentElement.tagName shouldBe "jar-test-1"
        val sibling = resolved.resolve("jar-file-2.xml")
        sibling.url shouldBe URL("jar:${jarFile.absoluteFile.toURI()}!/xml/jar-file-2.xml")
        sibling.load().documentElement.tagName shouldBe "jar-test-2"
    }

    @Test fun `should apply default extension when reading from JAR file`() {
        val jarURL = URL("jar:${jarFile.absoluteFile.toURI()}!/xml/jar-file-1")
        val resource = xmlLoader.resource(jarURL)
        resource.load().documentElement.tagName shouldBe "jar-test-1"
    }

    @Test fun `should throw ResourceNotFoundException when JAR entry not found`() {
        val jarURL = URL("jar:${jarFile.absoluteFile.toURI()}!/xml/jar-file-999.xml")
        val resource = xmlLoader.resource(jarURL)
        shouldThrow<ResourceNotFoundException>("Resource not found - $jarURL") {
            resource.load()
        }
    }

    @Test fun `should resolve relative URL to file`() {
        val resource = xmlLoader.resource(URL("jar:${jarURLString}!/xml/"))
        resource.toString() shouldBe "jar:${jarURLString}!/xml/"
        with(resource.resolve("jar-file-1.xml")) {
            isDirectory shouldBe false
            toString() shouldBe "jar:${jarURLString}!/xml/jar-file-1.xml"
        }
        with(resource.resolve("jar-file-2.xml")) {
            isDirectory shouldBe false
            toString() shouldBe "jar:${jarURLString}!/xml/jar-file-2.xml"
        }
    }

    @Test fun `should resolve relative URL to subdirectory`() {
        val resource = xmlLoader.resource(URL("jar:${jarURLString}!/"))
        resource.toString() shouldBe "jar:${jarURLString}!/"
        with(resource.resolve("xml/")) {
            isDirectory shouldBe true
            toString() shouldBe "jar:${jarURLString}!/xml/"
        }
    }

    @Test fun `should resolve relative URL from file to file`() {
        val resource = xmlLoader.resource(URL("jar:$jarURLString!/xml/jar-file-1.xml"))
        resource.toString() shouldBe "jar:$jarURLString!/xml/jar-file-1.xml"
        with(resource.resolve("jar-file-2.xml")) {
            isDirectory shouldBe false
            toString() shouldBe "jar:$jarURLString!/xml/jar-file-2.xml"
        }
    }

    @Test fun `should display readable form of URL on toString`() {
        val resource1 = xmlLoader.resource(File("src/test/resources/xml/test1.xml"))
        resource1.toString() shouldBe "src/test/resources/xml/test1.xml".split('/').joinToString(File.separator)
        val resource2 = xmlLoader.resource(URL("http://kjson.io/xml/test9.xml"))
        resource2.toString() shouldBe "http://kjson.io/xml/test9.xml"
    }

    @Test fun `should get a classpath URL`() {
        val url = Resource.classPathURL("/xml/test2.xml") ?: fail("Can't locate resource")
        url.protocol shouldBeOneOf listOf("file", "jar")
        url.toString() shouldEndWith "/xml/test2.xml"
        val resource = xmlLoader.resource(url)
        val document = resource.load()
        document.documentElement.tagName shouldBe "test2"
    }

    @Test fun `should compare URLs correctly`() {
        val fileURL = File("src/test/resources/xml/test1.xml").toURI().toURL()
        val httpURL = URL("https://example.com/test.txt")
        val localhostURL = URL("http://localhost:8080/file.xml")
        val jarURL = URL("jar:$jarURLString!/xml/jar-file-1.xml")

        (fileURL sameAs File("src/test/resources/xml/test1.xml").toURI().toURL()) shouldBe true
        fileURL.relevantHashCode() shouldBe File("src/test/resources/xml/test1.xml").toURI().toURL().relevantHashCode()
        (fileURL sameAs File("src/test/resources/xml/test1.xml").absoluteFile.toURI().toURL()) shouldBe true
        fileURL.relevantHashCode() shouldBe
                File("src/test/resources/xml/test1.xml").absoluteFile.toURI().toURL().relevantHashCode()
        (fileURL sameAs File("src/test/resources/xml/test2.xml").toURI().toURL()) shouldBe false
        (fileURL sameAs httpURL) shouldBe false
        (fileURL sameAs localhostURL) shouldBe false
        (fileURL sameAs jarURL) shouldBe false

        (httpURL sameAs URL("https://example.com/test.txt")) shouldBe true
        httpURL.relevantHashCode() shouldBe URL("https://example.com/test.txt").relevantHashCode()
        (httpURL sameAs URL("https://example.COM/test.txt")) shouldBe true
        httpURL.relevantHashCode() shouldBe URL("https://example.COM/test.txt").relevantHashCode()
        (httpURL sameAs URL("https://example.com/TEST.txt")) shouldBe false
        (httpURL sameAs URL("https://example.com/test1.txt")) shouldBe false
        (httpURL sameAs URL("https://example.com:8080/test.txt")) shouldBe false
        (httpURL sameAs fileURL) shouldBe false
        (httpURL sameAs localhostURL) shouldBe false
        (httpURL sameAs jarURL) shouldBe false

        (localhostURL sameAs URL("http://localhost:8080/file.xml")) shouldBe true
        localhostURL.relevantHashCode() shouldBe URL("http://localhost:8080/file.xml").relevantHashCode()
        (localhostURL sameAs URL("http://LOCALHOST:8080/file.xml")) shouldBe true
        localhostURL.relevantHashCode() shouldBe URL("http://LOCALHOST:8080/file.xml").relevantHashCode()
        (localhostURL sameAs URL("http://127.0.0.1:8080/file.xml")) shouldBe true
        localhostURL.relevantHashCode() shouldBe URL("http://127.0.0.1:8080/file.xml").relevantHashCode()
        (localhostURL sameAs URL("http://localhost/file.xml")) shouldBe false
        (localhostURL sameAs URL("http://localhost:8080/file1.xml")) shouldBe false
        (localhostURL sameAs fileURL) shouldBe false
        (localhostURL sameAs httpURL) shouldBe false
        (localhostURL sameAs jarURL) shouldBe false

        (jarURL sameAs URL("jar:$jarURLString!/xml/jar-file-1.xml")) shouldBe true
        jarURL.relevantHashCode() shouldBe URL("jar:$jarURLString!/xml/jar-file-1.xml").relevantHashCode()
        (jarURL sameAs URL("jar:$jarURLString!/xml/jar-file-2.xml")) shouldBe false
        (jarURL sameAs fileURL) shouldBe false
        (jarURL sameAs httpURL) shouldBe false
        (jarURL sameAs localhostURL) shouldBe false
    }

    @Test fun `should test for presence of protocol correctly`() {
        "http://xyz.com".hasProtocol() shouldBe true
        File(".").toURI().toURL().toString().hasProtocol() shouldBe true
        "relative.txt".hasProtocol() shouldBe false
        "/test1.txt".hasProtocol() shouldBe false
        ":test1.txt".hasProtocol() shouldBe false
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
                jarOutputStream.addFile("xml/jar-file-1.xml", jarFile1.encodeToByteArray())
                jarOutputStream.addFile("xml/jar-file-2.xml", jarFile2.encodeToByteArray())
            }
        }

        private fun JarOutputStream.addFile(name: String, bytes: ByteArray) {
            putNextEntry(JarEntry(name).apply { time = System.currentTimeMillis() })
            write(bytes)
            closeEntry()
        }

        fun pathOf(vararg elements: String) = elements.joinToString(File.separator)

    }

}
