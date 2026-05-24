package com.github.cao.awa.kora.mysql.client.handshake

data class HandshakeData(val scramble: ByteArray, val authPluginName: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (this.javaClass != other?.javaClass) {
            return false
        }

        other as HandshakeData

        if (!this.scramble.contentEquals(other.scramble)) {
            return false
        }
        if (this.authPluginName != other.authPluginName) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = this.scramble.contentHashCode()
        result = 31 * result + this.authPluginName.hashCode()
        return result
    }
}
