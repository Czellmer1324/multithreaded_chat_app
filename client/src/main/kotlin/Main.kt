package com.czellmer1324

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import kotlin.system.exitProcess

suspend fun main() {
    withContext(Dispatchers.IO) {
        var socket : Socket
        try {
            socket = Socket("localhost", 12345)
        } catch (e : Exception) {
            println("Something went wrong. Server may be down try again later")
            exitProcess(0)
        }

        var out = PrintWriter(socket.getOutputStream(), true)
        var dataIn = BufferedReader(InputStreamReader(socket.getInputStream()))
        var connected = false

        print("Enter the username you would like to known by: ")
        var name = readln()

        while(!connected) {
            out.println(name)
            val connectResponse = dataIn.readLine()
            if (connectResponse == "connected") {
                connected = true
            } else {
                print("That username is already taken. Please enter an new one: ")
                name = readln()
                socket = Socket("localhost", 12345)
                out = PrintWriter(socket.getOutputStream(), true)
                dataIn = BufferedReader(InputStreamReader(socket.getInputStream()))
            }
        }

        this.launch {
            this.launch(Dispatchers.IO) {
                while (true) {
                    val message = "$name: ${readln()}"

                    out.println(message)
                }
            }

            this.launch(Dispatchers.IO) {
                while (true) {
                    val message = dataIn.readLine()
                    if (message == null) {
                        socket.close()
                        dataIn.close()
                        out.close()
                        println("The server has been disconnected. Try rejoining latter")
                        exitProcess(0)
                    }
                    println(message)
                }
            }
        }
    }
}