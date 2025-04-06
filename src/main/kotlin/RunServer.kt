package dev.skystar1

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

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

    override fun run() {
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
                    tools = ServerCapabilities.Tools(listChanged = true)
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
                content = listOf(TextContent(PlaywrightHelper.createNewTab()))
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
                    )
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
                    )
                )
            val url = request.arguments["url"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(
                    content = listOf(
                        TextContent("The 'url' parameter is required.")
                    )
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
                    )
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
                    )
                )
            val script = request.arguments["script"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(
                    content = listOf(
                        TextContent("The 'script' parameter is required.")
                    )
                )
            CallToolResult(
                content = listOf(TextContent(PlaywrightHelper.executeJavaScript(uuid, script).toString()))
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
