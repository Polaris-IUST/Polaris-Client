package com.example.polaris_client.utils

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class DataEntry(
    val latitude: Double,
    val longitude: Double,
    val timestamp: String,
    val distanceWalked: Float, // check
    val technology: String,
    val nodeId: String,
    val plmnId: String,
    val lac: String,
    val rac: String?,
    val tac: String,
    val cellId: String,
    val band: String,
    val arfcan: Int?,
    val signalStrength: Int,
    val scanTech: String,
    val signalQuality: Int,
    val scanServingSigPow: Int
)

data class NetworkTestEntry(
    val id: Long,
    val testType: String,  // HTTP, PING, DNS, WEB, SMS
    val result: String,    // Throughput for HTTP, response time for others
    val additionalInfo: String, // Duration, count, hostname, URL, or phone number
    val timestamp: String,
    val latitude: Double,
    val longitude: Double
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "cellular_data.db"
        private const val DATABASE_VERSION = 3
        private const val TABLE_NAME = "cellular_info"
        private const val NETWORK_TEST_TABLE = "network_tests"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                latitude REAL,
                longitude REAL,
                timestamp TEXT,
                technology TEXT,
                plmnId TEXT,
                lac TEXT,
                rac TEXT,
                tac TEXT,
                cellId TEXT,
                signalStrength INTEGER,
                signalQuality INTEGER,
                arfcan INTEGER,
                distanceWalked REAL,
                nodeId TEXT,
                scanTech TEXT,
                band TEXT,
                scanServingSigPow INTEGER
            )
        """.trimIndent()
        db?.execSQL(createTableQuery)
        
        val createNetworkTestTable = """
            CREATE TABLE $NETWORK_TEST_TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                testType TEXT,
                result TEXT,
                additionalInfo TEXT,
                timestamp TEXT,
                latitude REAL,
                longitude REAL
            )
        """.trimIndent()
        db?.execSQL(createNetworkTestTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            // Add the network test table if upgrading from version < 3
            val createNetworkTestTable = """
                CREATE TABLE IF NOT EXISTS $NETWORK_TEST_TABLE (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    testType TEXT,
                    result TEXT,
                    additionalInfo TEXT,
                    timestamp TEXT,
                    latitude REAL,
                    longitude REAL
                )
            """.trimIndent()
            db?.execSQL(createNetworkTestTable)
        } else {
            db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            db?.execSQL("DROP TABLE IF EXISTS $NETWORK_TEST_TABLE")
            onCreate(db)
        }
    }

    fun insertData(
        latitude: Double,
        longitude: Double,
        timestamp: String,
        technology: String,
        plmnId: String,
        lac: String,
        rac: String?,
        tac: String,
        cellId: String,
        signalStrength: Int,
        signalQuality: Int,
        distanceWalked: Float,
        nodeId: String,
        band: String,
        arfcan: Int?,
        scanTech: String,
        scanServingSigPow: Int
    ) {
        val db = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put("latitude", latitude)
        contentValues.put("longitude", longitude)
        contentValues.put("timestamp", timestamp)
        contentValues.put("technology", technology)
        contentValues.put("plmnId", plmnId)
        contentValues.put("lac", lac)
        contentValues.put("rac", rac)
        contentValues.put("tac", tac)
        contentValues.put("cellId", cellId)
        contentValues.put("signalStrength", signalStrength)
        contentValues.put("signalQuality", signalQuality)
        contentValues.put("scanTech", scanTech)
        contentValues.put("scanServingSigPow", scanServingSigPow)
        contentValues.put("distanceWalked", distanceWalked)
        contentValues.put("nodeId", nodeId)
        contentValues.put("band", band)
        contentValues.put("arfcan", arfcan)

        db.insert(TABLE_NAME, null, contentValues)
        db.close()
    }
    
    fun insertNetworkTestData(
        testType: String,
        result: String,
        additionalInfo: String,
        latitude: Double,
        longitude: Double
    ) {
        val db = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put("testType", testType)
        contentValues.put("result", result)
        contentValues.put("additionalInfo", additionalInfo)
        contentValues.put("timestamp", System.currentTimeMillis().toString())
        contentValues.put("latitude", latitude)
        contentValues.put("longitude", longitude)
        
        db.insert(NETWORK_TEST_TABLE, null, contentValues)
        db.close()
    }

    fun getAllData(): List<DataEntry> {
        val dataList = mutableListOf<DataEntry>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME", null)

        if (cursor.moveToFirst()) {
            do {
                val dataEntry = DataEntry(
                    latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")),
                    longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")),
                    timestamp = cursor.getString(cursor.getColumnIndexOrThrow("timestamp")),
                    technology = cursor.getString(cursor.getColumnIndexOrThrow("technology")),
                    plmnId = cursor.getString(cursor.getColumnIndexOrThrow("plmnId")),
                    lac = cursor.getString(cursor.getColumnIndexOrThrow("lac")),
                    rac = cursor.getString(cursor.getColumnIndexOrThrow("rac")),
                    tac = cursor.getString(cursor.getColumnIndexOrThrow("tac")),
                    cellId = cursor.getString(cursor.getColumnIndexOrThrow("cellId")),
                    signalStrength = cursor.getInt(cursor.getColumnIndexOrThrow("signalStrength")),
                    signalQuality = cursor.getInt(cursor.getColumnIndexOrThrow("signalQuality")),
                    distanceWalked = cursor.getFloat(cursor.getColumnIndexOrThrow("distanceWalked")),
                    nodeId = cursor.getString(cursor.getColumnIndexOrThrow("nodeId")),
                    band = cursor.getString(cursor.getColumnIndexOrThrow("band")),
                    arfcan = cursor.getInt(cursor.getColumnIndexOrThrow("arfcan")),
                    scanTech = cursor.getString(cursor.getColumnIndexOrThrow("scanTech")),
                    scanServingSigPow = cursor.getInt(cursor.getColumnIndexOrThrow("scanServingSigPow"))
                )
                dataList.add(dataEntry)
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()
        return dataList
    }
    
    fun getNetworkTestResults(testType: String? = null): List<NetworkTestEntry> {
        val resultList = mutableListOf<NetworkTestEntry>()
        val db = this.readableDatabase
        
        val query = if (testType != null) {
            "SELECT * FROM $NETWORK_TEST_TABLE WHERE testType = '$testType' ORDER BY timestamp DESC"
        } else {
            "SELECT * FROM $NETWORK_TEST_TABLE ORDER BY timestamp DESC"
        }
        
        val cursor = db.rawQuery(query, null)
        
        if (cursor.moveToFirst()) {
            do {
                val entry = NetworkTestEntry(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    testType = cursor.getString(cursor.getColumnIndexOrThrow("testType")),
                    result = cursor.getString(cursor.getColumnIndexOrThrow("result")),
                    additionalInfo = cursor.getString(cursor.getColumnIndexOrThrow("additionalInfo")),
                    timestamp = cursor.getString(cursor.getColumnIndexOrThrow("timestamp")),
                    latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")),
                    longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"))
                )
                resultList.add(entry)
            } while (cursor.moveToNext())
        }
        
        cursor.close()
        db.close()
        return resultList
    }

    fun clearAllData() {
        val db = this.writableDatabase
        db.delete(TABLE_NAME, null, null)
        db.delete(NETWORK_TEST_TABLE, null, null)
        db.close()
    }
}
