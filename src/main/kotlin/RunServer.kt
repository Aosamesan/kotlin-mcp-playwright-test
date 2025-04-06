@file:OptIn(ExperimentalEncodingApi::class)

package dev.skystar1

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.path.*

class MCPServer : CliktCommand() {
    private val executablePath by option(
        "-e",
        "--executable-path",
        help = "Path to Chrome executable"
    ).default("")
    private val noHeadless by option(
        "--no-headless",
        help = "Run without headless mode",
    ).flag(default = false)
    private val saveFilePathString by option(
        "-s",
        "--save-file",
        help = "Path to save the server state"
    ).default(System.getProperty("user.home"))

    override fun run() {
        val saveFilePath = Path(saveFilePathString)
        check(saveFilePath.exists() && saveFilePath.isDirectory()) {
            "The save file path must be a directory"
        }
        runServer(executablePath.trim(), !noHeadless)
    }

    private fun runServer(executablePath: String, headless: Boolean = true) {
        PlaywrightHelper.init(executablePath, headless)

        val server = Server(
            Implementation(
                name = "mcp-playwright-test",
                version = "1.0.0"
            ),
            ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                )
            )
        )

        val transport = StdioServerTransport(
            System.`in`.asInput(),
            System.out.asSink().buffered()
        )

        server.addTool(
            name = "open-new-tab",
            description = """
                    Open new tab and return its UUID.
                """.trimIndent(),
        ) {
            CallToolResult(
                content = listOf(TextContent(PlaywrightHelper.createNewTab())),
                isError = true
            )
        }

        server.addTool(
            name = "close-tab",
            description = """
                    Close tab by UUID.
                """.trimIndent(),
            inputSchema = Tool.Input(
                properties = JsonObject(mapOf("uuid" to JsonPrimitive("string"))),
                required = listOf("uuid")
            )
        ) { request ->
            val uuid = request.arguments["uuid"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(
                    content = listOf(
                        TextContent("The 'uuid' parameter is required.")
                    ),
                    isError = true
                )

            PlaywrightHelper.closeTab(uuid)

            CallToolResult(
                content = listOf(TextContent("Closed tab '$uuid'"))
            )
        }

        server.addTool(
            name = "navigate",
            description = """
                    Navigate to a specific URL in the tab identified by UUID.
                """.trimIndent(),
            inputSchema = Tool.Input(
                properties = JsonObject(
                    mapOf(
                        "uuid" to JsonPrimitive("string"),
                        "url" to JsonPrimitive("string")
                    )
                ),
                required = listOf("uuid", "url")
            )
        ) { request ->
            val uuid = request.arguments["uuid"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(
                    content = listOf(
                        TextContent("The 'uuid' parameter is required.")
                    ),
                    isError = true
                )
            val url = request.arguments["url"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(
                    content = listOf(
                        TextContent("The 'url' parameter is required.")
                    ),
                    isError = true
                )

            val response = PlaywrightHelper.navigate(uuid, url)

            CallToolResult(
                content = listOf(
                    TextContent(
                        "navigation result : ${response.statusText()}"
                    )
                )
            )
        }

        server.addTool(
            name = "get-contents",
            description = """
                    Get contents of the tab identified by UUID.
                """.trimIndent(),
            inputSchema = Tool.Input(
                properties = JsonObject(mapOf("uuid" to JsonPrimitive("string"))),
                required = listOf("uuid")
            )
        ) { request ->
            val uuid = request.arguments["uuid"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(
                    content = listOf(
                        TextContent("The 'uuid' parameter is required.")
                    ),
                    isError = true
                )
            CallToolResult(
                content = listOf(TextContent(PlaywrightHelper.getContents(uuid)))
            )
        }

        server.addTool(
            name = "execute-javascript",
            description = "Execute JavaScript in the tab identified by UUID.",
            inputSchema = Tool.Input(
                properties = JsonObject(mapOf("uuid" to JsonPrimitive("string"), "script" to JsonPrimitive("string"))),
                required = listOf("uuid", "script")
            )
        ) { request ->
            val uuid = request.arguments["uuid"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(
                    content = listOf(
                        TextContent("The 'uuid' parameter is required.")
                    ),
                    isError = true
                )
            val script = request.arguments["script"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(
                    content = listOf(
                        TextContent("The 'script' parameter is required.")
                    ),
                    isError = true
                )
            CallToolResult(
                content = listOf(TextContent(PlaywrightHelper.executeJavaScript(uuid, script).toString()))
            )
        }

        server.addTool(
            name = "make-directory",
            description = "Make directory in specified directory.",
            inputSchema = Tool.Input(
                properties = JsonObject(
                    mapOf(
                        "path" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("relative directory path from specific directory.")
                            )
                        )
                    )
                ),
                required = listOf("path")
            )
        ) { request ->
            val pathString = request.arguments["path"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(
                    content = listOf(
                        TextContent("The 'path' parameter is required.")
                    ),
                    isError = true
                )
            val path = Path(saveFilePathString, pathString)
            try {
                path.createDirectories()
                CallToolResult(
                    content = listOf(TextContent("Created directory '${path.fileName}'"))
                )
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("Failed to create directory '${path.fileName}', error: ${e.message}")),
                )
            }
        }

        server.addTool(
            name = "save-text-file",
            description = "Save text file in specified directory.",
            inputSchema = Tool.Input(
                properties = JsonObject(
                    mapOf(
                        "filename" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("relative file path including extension. It is not guaranteed that middle directories will be created")
                            )
                        ),
                        "content" to JsonPrimitive("string")
                    )
                ),
                required = listOf("filename", "content")
            )
        ) { request ->
            val filename = request.arguments["filename"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(
                    content = listOf(
                        TextContent("The 'filename' parameter is required.")
                    ),
                    isError = true
                )
            val content = request.arguments["content"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(
                    content = listOf(
                        TextContent("The 'content' parameter is required.")
                    ),
                    isError = true
                )

            val realFilePath = Path(saveFilePathString, filename)

            try {
                realFilePath.writeText(content)
                CallToolResult(
                    content = listOf(TextContent("Saved file '${realFilePath.fileName}'"))
                )
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("Failed to save file '${realFilePath.fileName}', error: ${e.message}")),
                    isError = true
                )
            }
        }

        server.addTool(
            name = "list-files",
            description = "List files in a directory.",
            inputSchema = Tool.Input(
                properties = JsonObject(
                    mapOf(
                        "path" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("relative directory path from specific directory. if not specifed, it will be the root directory")
                            )
                        )
                    )
                )
            )
        ) { request ->
            val path = request.arguments["path"]?.jsonPrimitive?.contentOrNull ?: "."
            val realPath = Path(saveFilePathString, path)

            if (!realPath.exists() || !realPath.isDirectory()) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("The path is invalid.")),
                    isError = true
                )
            }

            val entries = realPath.walk(PathWalkOption.INCLUDE_DIRECTORIES, PathWalkOption.BREADTH_FIRST).map { p ->
                JsonObject(mapOf(
                    "name" to JsonPrimitive(p.fileName.toString()),
                    "path" to JsonPrimitive(p.relativeTo(Path(saveFilePathString)).toString()),
                    "isDirectory" to JsonPrimitive(p.isDirectory())
                ))
            }.toList()
            val array = JsonArray(entries)

            CallToolResult(
                content = listOf(
                    TextContent(array.toString()),
                )
            )
        }

        server.addTool(
            name = "save-using-url",
            description = "Save file using URL",
            inputSchema = Tool.Input(
                properties = JsonObject(
                    mapOf(
                        "filename" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("relative file path including extension. It is not guaranteed that middle directories will be created")
                            )
                        ),
                        "url" to JsonPrimitive("string")
                    )
                ),
                required = listOf("filename", "url")
            )
        ) { request ->
            val filename = request.arguments["filename"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(
                    content = listOf(
                        TextContent("The 'filename' parameter is required.")
                    ),
                    isError = true
                )
            val url = request.arguments["url"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(
                    content = listOf(
                        TextContent("The 'url' parameter is required.")
                    ),
                    isError = true
                )
            val realFilePath = Path(saveFilePathString, filename)

            HttpClient(CIO).use { client ->
                try {
                    val response = client.get(url)
                    val byteArray = response.bodyAsBytes()
                    realFilePath.writeBytes(byteArray)
                    CallToolResult(
                        content = listOf(TextContent("Saved file '${realFilePath.fileName}'"))
                    )
                } catch (e: Exception) {
                    CallToolResult(
                        content = listOf(TextContent("Failed to save file '${realFilePath.fileName}', error: ${e.message}")),
                        isError = true
                    )
                }
            }
        }

        server.addTool(
            name = "read-text-file",
            description = "Read text file in specified directory.",
            inputSchema = Tool.Input(
                properties = JsonObject(
                    mapOf(
                        "filename" to JsonPrimitive("string")
                    )
                ),
                required = listOf("filename")
            )
        ) { request ->
            val filename = request.arguments["filename"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(
                    content = listOf(
                        TextContent("The 'filename' parameter is required.")
                    ),
                    isError = true
                )
            val realFilePath = Path(saveFilePathString, filename)

            if (!realFilePath.exists()) {
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent("$filename does not exist")
                    ),
                    isError = true
                )
            }

            if (realFilePath.isDirectory()) {
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent("$filename is a directory")
                    ),
                    isError = true
                )
            }

            val content = realFilePath.readText()

            CallToolResult(
                content = listOf(TextContent(content))
            )
        }

        server.addTool(
            name = "read-binary-file",
            description = "Read binary file in specified directory.",
            inputSchema = Tool.Input(
                properties = JsonObject(
                    mapOf(
                        "filename" to JsonPrimitive("string"),
                        "mimeType" to JsonPrimitive("string"),
                    )
                ),
                required = listOf("filename", "mimeType")
            )
        ) { request ->
            val filename = request.arguments["filename"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(
                    content = listOf(
                        TextContent("The 'filename' parameter is required.")
                    ),
                    isError = true
                )

            val mimeType = request.arguments["mimeType"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(
                    content = listOf(
                        TextContent("The 'mimeType' parameter is required.")
                    ),
                    isError = true
                )

            val realFilePath = Path(saveFilePathString, filename)

            if (!realFilePath.exists()) {
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent("$filename does not exist")
                    ),
                    isError = true
                )
            }

            if (realFilePath.isDirectory()) {
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent("$filename is a directory")
                    ),
                    isError = true
                )
            }

            val content = realFilePath.readBytes()

            CallToolResult(
                content = listOf(
                    ImageContent(Base64.encode(content), mimeType)
                )
            )
        }


        runBlocking {
            server.connect(transport)
            val done = Job()
            server.onClose {
                done.complete()
            }
            done.join()
        }
    }
}
