package com.github.cao.awa.kora.mysql.data.line

import com.github.cao.awa.kora.mysql.data.Column

object EmptyLine: Line() {
    override fun add(column: Column, lineData: String?) {
        throw UnsupportedOperationException("Cannot add data to empty line")
    }
}