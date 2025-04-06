package dev.skystar1

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType.LaunchOptions
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Response
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
object PlaywrightHelper : AutoCloseable {
    private var ChromePath: Path? = null
    private lateinit var Browser: Browser
    private val PageMap: MutableMap<String, Page> = mutableMapOf()

    init {
        Runtime.getRuntime().addShutdownHook(Thread { close() })
    }

    internal fun init(chromePath: String, headless: Boolean = true) {
        if (chromePath.isNotBlank()) {
            ChromePath = Path(chromePath)
        }
        Browser = Playwright.create().chromium().launch(LaunchOptions().apply {
            if (ChromePath != null) {
                setExecutablePath(ChromePath)
            }
            setHeadless(headless)
        })
    }

    fun createNewTab(): String {
        val uuid = Uuid.random();
        PageMap[uuid.toString()] = Browser.newPage()
        return uuid.toString()
    }

    fun closeTab(uuid: String) {
        PageMap[uuid]?.close()
        PageMap.remove(uuid)
    }

    fun getContents(uuid: String): String {
        return requireNotNull(PageMap[uuid]) {
            "Tab named '$uuid' does not exist"
        }.content()
    }

    fun navigate(uuid: String, url: String): Response {
        return requireNotNull(PageMap[uuid]) {
            "Tab named '$uuid' does not exist"
        }.navigate(url)
    }

    fun executeJavaScript(uuid: String, script: String): Any? {
        return requireNotNull(PageMap[uuid]) {
            "Tab named '$uuid' does not exist"
        }.evaluate(script)
    }

    override fun close() {
        try {
            Browser.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}