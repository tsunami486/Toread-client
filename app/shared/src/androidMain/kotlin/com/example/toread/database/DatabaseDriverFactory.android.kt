package com.example.toread.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

object AndroidAppContext {
    lateinit var applicationContext: Context
        private set

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }
}

actual class DatabaseDriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(ToreadDatabase.Schema, AndroidAppContext.applicationContext, "toread.db")
}
