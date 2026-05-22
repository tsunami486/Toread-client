package com.example.toread.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Paths

actual class DatabaseDriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver {
        val databasePath = Paths.get(System.getProperty("user.home"), ".toread", "toread.db")
        Files.createDirectories(databasePath.parent)

        val driver = JdbcSqliteDriver("jdbc:sqlite:${databasePath.toAbsolutePath()}")
        val needsSchema = !Files.exists(databasePath) || Files.size(databasePath) == 0L
        if (needsSchema) {
            ToreadDatabase.Schema.create(driver)
        }
        return driver
    }
}
