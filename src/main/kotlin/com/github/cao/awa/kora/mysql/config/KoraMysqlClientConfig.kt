package com.github.cao.awa.kora.mysql.config

import com.github.cao.awa.cason.obj.JSONObject
import com.github.cao.awa.kora.config.KoraConfig
import java.io.File

open class KoraMysqlClientConfig : KoraConfig() {
    companion object {
        fun createConfig(file: File): KoraMysqlClientConfig {
            return createConfig(file) {
                val config = KoraMysqlClientConfig()

                ifString("host") {
                    config.host(this)
                }

                ifInt("port") {
                    config.port = this
                }

                ifString("username") {
                    config.username = this
                }

                ifString("password") {
                    config.password = this
                }

                ifString("database") {
                    config.database = this
                }

                ifInt("reconnect_time") {
                    config.reconnectTime = this
                }

                config
            }
        }
    }

    private var host: String = "127.0.0.1"
    private var port: Int = 3306
    private var username: String = "root"
    private var password: String = ""
    private var database: String = ""
    private var reconnectTime: Int = 5000

    fun host(): String {
        return this.host
    }

    open fun host(host: String): KoraMysqlClientConfig {
        this.host = host
        return this
    }

    fun port(): Int {
        return this.port
    }

    open fun port(port: Int): KoraMysqlClientConfig {
        this.port = port
        return this
    }

    fun username(): String {
        return this.username
    }

    open fun username(username: String): KoraMysqlClientConfig {
        this.username = username
        return this
    }

    fun password(): String {
        return this.password
    }

    open fun password(password: String): KoraMysqlClientConfig {
        this.password = password
        return this
    }

    fun database(): String {
        return this.database
    }

    open fun database(database: String): KoraMysqlClientConfig {
        this.database = database
        return this
    }

    fun reconnectTime(): Int {
        return this.reconnectTime
    }

    open fun reconnectTime(reconnectTime: Int): KoraMysqlClientConfig {
        this.reconnectTime = reconnectTime
        return this
    }

    override fun toJSON(): JSONObject {
        return JSONObject {
            "host" set host
            "port" set port
            "username" set username
            "password" set password
            "database" set database
            "reconnect_time" set reconnectTime
        }
    }
}