package com.github.cao.awa.kora.mysql.data.result

import com.github.cao.awa.kora.mysql.data.Column
import com.github.cao.awa.kora.mysql.data.line.EmptyLine
import com.github.cao.awa.kora.mysql.data.line.Line
import com.github.cao.awa.kora.mysql.data.row.Row

class ResultSet(val columns: List<Column>, val rows: Map<Column, Row>, val lines: Int) {
    companion object {
        val EMPTY_RESULT = ResultSet(emptyList(), emptyMap(), 0)
    }

    private val nameMap: Map<String, Column> = mutableMapOf<String, Column>().also {
        for (column in this.columns) {
            it[column.name] = column
        }
    }

    fun getRow(columnName: Column): Row? {
        return this.rows[columnName]
    }

    fun getRow(columnName: String): Row? {
        val column = this.nameMap[columnName]
        return if (column != null) {
            getRow(column)
        } else {
            Row.EMPTY_ROW
        }
    }

    fun getValues(column: Column): List<String?> {
        return this.rows[column]?.values ?: Row.EMPTY_ROW.values
    }

    fun getValues(columnName: String): List<String?> {
        val column = this.nameMap[columnName]
        if (column != null) {
            return getValues(column)
        } else {
            return Row.EMPTY_ROW.values
        }
    }

    fun getValue(column: Column, index: Int): String? {
        val row = this.rows[column] ?: return null
        if (index > row.size()) {
            return null
        }
        return row[index]
    }

    fun getValue(columnName: String, index: Int): String? {
        val column = this.nameMap[columnName]
        return if (column != null) {
            getValue(column, index)
        } else {
            null
        }
    }

    fun getLine(index: Int): Line {
        if (index >= this.lines || index < 0){
            return EmptyLine
        }
        val line = Line {
            for (column in columns) {
                put(column, getValue(column, index))
            }
        }
        return line
    }

    fun getLine(column: Column, name: String): Line {
        val index = getValues(column).indexOf(name)
        if (index == -1) {
            return EmptyLine
        }
        return getLine(index)
    }

    fun getLine(columnName: String, name: String): Line {
        val column = this.nameMap[columnName]
        return if (column != null) {
            getLine(column, name)
        } else {
            EmptyLine
        }
    }

    fun size() = this.rows.size
}
