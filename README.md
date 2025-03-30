## Build
### Linux/macOS
```shell
./gradlew -x test shadowJar
```
### Windows
```shell
./gradlew.bat -x test shadowJar
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