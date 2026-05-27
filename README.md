# Kora-redis
A MySQL client plugin for Kora webserver.

## Usage
Add dependencies firsy:
```groovy
repositories {
    maven {
        url 'https://jitpack.io'
    }
}

dependencies {
    implementation 'com.github.cao-awa:Kora-mysql:{version}'
}
```

For the versions, see [JitPack](https://jitpack.io/#cao-awa/Kora-mysql).

And use redis client in your code:
```kotlin
import com.github.cao.awa.kora.mysql.client.KoraMysqlClient

object Test {
    @JvmStatic
    fun entry() {
        val mysqlClient = KoraMysqlClient.INSTANCE
        val result = mysqlClient.execute("SELECT User, Host FROM mysql.user;")
        for (column in result.columns) {
            println(": Column: ${column.name}-")
            for (data in result.getValues(column)) {
                println(data)
            }
        }

        val userLine = result.getLine("User", "mysql.sys")
        println(userLine)

        val hostLine = result.getLine("Host", "127.0.0.1")
        println(hostLine)

        val specialLine = result.getLine(1)
        println(specialLine)
    }
}
```

In produce environment, you need put the ``kora-mysql`` jar to ``libs/`` directory and declare entrypoint:
```json
{
    "entrypoint": [
        "kora-mysql-client",
        "com.yourservice.xxx.ServiceEntrypoint#entry"
    ]
}
```

For entrypoint, please see [Kora's document](https://github.com/cao-awa/Kora/tree/main/docs/entrypoint)/