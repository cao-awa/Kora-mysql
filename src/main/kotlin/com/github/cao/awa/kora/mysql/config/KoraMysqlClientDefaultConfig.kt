package com.github.cao.awa.kora.mysql.config

object KoraMysqlClientDefaultConfig: KoraMysqlClientConfig() {
    private fun throwWhenSet(): Nothing {
        error("Cannot set config in default server config instance")
    }

    override fun host(host: String): KoraMysqlClientConfig {
        throwWhenSet()
    }

    override fun port(port: Int): KoraMysqlClientConfig {
        throwWhenSet()
    }

    override fun username(username: String): KoraMysqlClientConfig {
        throwWhenSet()
    }

    override fun password(password: String): KoraMysqlClientConfig {
        throwWhenSet()
    }

    override fun database(database: String): KoraMysqlClientConfig {
        throwWhenSet()
    }
}