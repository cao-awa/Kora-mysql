package com.github.cao.awa.kora.mysql.data.row

data class RowDefinition(
    val values: List<String?>
) {
    operator fun get(index: Int): String? {
        return this.values[index]
    }
}