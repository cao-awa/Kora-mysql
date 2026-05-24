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
