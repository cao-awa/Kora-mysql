package com.github.cao.awa.kora.mysql.entrypoint

import com.github.cao.awa.kora.mysql.client.KoraMysqlClient
import com.github.cao.awa.kora.mysql.config.KoraMysqlClientConfig
import com.github.cao.awa.kora.plugin.markPluginLoaded
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File

object MysqlPluginBootstrap {
    private val LOGGER: Logger = LogManager.getLogger("RedisPluginBootstrap")
    val NAME: String = "kora-mysq-clientl"

    @JvmStatic
    fun init() {
        LOGGER.info("Initializing mysql client")
        val configFile = File("configs/mysql_client.json")

        KoraMysqlClient.init(KoraMysqlClientConfig.createConfig(configFile))

        markPluginLoaded(NAME)
    }
}


fun main() {
    MysqlPluginBootstrap.init()
}