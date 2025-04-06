## Build
### Linux/macOS
```shell
./gradlew -x test buildFatJar
```
### Windows
```shell
./gradlew.bat -x test buildFatJar
```

### Config (QuickStart)
```json
{
    "mcpServers": {
        "mcp-playwright-test": {
            "command": "${YOUR_JAVA_BIN_PATH}",
            "args": [
                "-jar",
                "${BUILD_PATH}/mcp-test-1.0-SNAPSHOT-all.jar"
            ]
        }
    }
}
```