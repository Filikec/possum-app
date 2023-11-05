package com.acidgoose.possumkotlin

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*

/**
 * A singleton to provide utility for simple logging of text into a file
 */
object AppLog {
    private const val filename = "Log.txt"

    fun write(str : String, context : Context){
        val file = File(context.filesDir, filename)
        if (!file.exists()) file.createNewFile()
        val stream = FileOutputStream(file,true)
        stream.write((str+" "+ Date(System.currentTimeMillis()).toString()+'\n').encodeToByteArray())
        stream.close()
    }

    fun read(context : Context) : String {
        val file = File(context.filesDir, filename)
        if (!file.exists()) file.createNewFile()

        val length = file.length().toInt()
        val bytes = ByteArray(length)
        val `in` = FileInputStream(file)

        try {
            `in`.read(bytes)
        } finally {
            `in`.close()
        }

        return String(bytes)
    }
}