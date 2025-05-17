package it.dii.unipi.myapplication.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CompensationDatabaseHelper(context: Context?) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "compensation.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "compensation"
        private const val COLUMN_ID = "id"
        private const val COLUMN_VALUE = "value"
    }

    init {
        writableDatabase // Ensure the database is initialized when the helper is created
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = "CREATE TABLE $TABLE_NAME ($COLUMN_ID INTEGER PRIMARY KEY, $COLUMN_VALUE REAL)"
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun saveCompensationValue(value: Float?) {
        val db = writableDatabase
        db.execSQL("DELETE FROM $TABLE_NAME") // Ensure only one value is stored
        val insertQuery = "INSERT INTO $TABLE_NAME ($COLUMN_VALUE) VALUES (?)"
        db.execSQL(insertQuery, arrayOf(value))
        db.close()
    }

    fun getCompensationValue(): Float? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT $COLUMN_VALUE FROM $TABLE_NAME LIMIT 1", null)
        return if (cursor.moveToFirst()) {
            val value = cursor.getFloat(0)
            cursor.close()
            db.close()
            value
        } else {
            cursor.close()
            db.close()
            null
        }
    }

    fun deleteCompensationValue() {
        val db = writableDatabase
        db.execSQL("DELETE FROM $TABLE_NAME")
        db.close()
    }
}

