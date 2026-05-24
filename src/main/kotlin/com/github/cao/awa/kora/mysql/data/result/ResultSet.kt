package com.github.cao.awa.kora.mysql.data.result

import com.github.cao.awa.kora.mysql.data.ColumnDefinition
import com.github.cao.awa.kora.mysql.data.row.RowDefinition

class ResultSet(val columns: List<ColumnDefinition>, val rows: List<RowDefinition>) {
    fun getString(row: Int, col: Int): String? {
        return this.rows[row][col]
    }

    fun getInt(row: Int, col: Int): Int? {
        val str = getString(row, col) ?: return null
        return str.toIntOrNull()
    }

    fun size() = this.rows.size
}
