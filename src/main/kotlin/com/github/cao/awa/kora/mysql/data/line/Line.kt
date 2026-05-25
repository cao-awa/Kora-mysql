package com.github.cao.awa.kora.mysql.data.line

import com.github.cao.awa.kora.mysql.data.Column

open class Line(val creator: MutableMap<Column, String?>.() -> Unit) {
    private val data: MutableMap<Column, String?> = mutableMapOf<Column, String?>().also {
        this.creator(it)
    }

    override fun toString(): String {
        val builder = StringBuilder()

        builder.append("[")
        var index = 0
        for ((column, data) in this.data) {
            builder.append(data)
            builder.append(" (")
            builder.append(column.name)
            builder.append(")")
            if (index++ < this.data.size - 1) {
                builder.append(", ")
            }
        }
        builder.append("]")

        return builder.toString()
    }
}