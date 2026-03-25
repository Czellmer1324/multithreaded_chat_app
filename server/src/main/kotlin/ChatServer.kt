package com.czellmer1324

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class ChatServer(val port : Int) {
    private lateinit var socket : ServerSocket
    private val flow = MutableSharedFlow<String>()
    private val userNames = ArrayList<String>()
    private val mutex = Mutex()
    private var enabled = true

    // val out = PrintWriter(connection.getOutputStream(), true)
    // val dataIn = BufferedReader(InputStreamReader(connection.getInputStream()))

    fun start() {
        println(Thread.currentThread().name)
        socket = ServerSocket(port)
        //Listening for the connection will happen on the main thread.
        // each subsequent connection should all be under one coroutine scope.
        // That scope should be a supervisor scope so that an individual connection can fail.
        val scope = CoroutineScope(SupervisorJob())
        while (enabled) {
            val connection = socket.accept()
            scope.launch(Dispatchers.Default) {
                startChatting(connection)
                println("Child has closed")
            }
        }
    }

    // This scope holds the 2 coroutines that belong to this chatter
    suspend fun startChatting(connection: Socket) = coroutineScope {
        var name = ""

        val dataIn = BufferedReader(InputStreamReader(connection.getInputStream()))
        val out = PrintWriter(connection.getOutputStream(), true)
        val posName = dataIn.readLine()
        if (checkUserName(posName)) {
            out.println("username taken")
            connection.close()
            dataIn.close()
            out.close()
            this@coroutineScope.coroutineContext.cancel()
        } else {
            name = posName
            out.println("connected")
        }

        flow.emit("$name has joined the chat!")
        println("Connection accepted: $name is now chatting on the server")

        // Ok we are starting to chat
        // Coroutine to wait for input from the chatter
        this.launch(Dispatchers.IO) {
            while (!connection.isClosed) {
                val message = dataIn.readLine()
                if (message == null) {
                    connection.close()
                    dataIn.close()
                    out.close()
                    mutex.withLock {
                        userNames.remove(name)
                    }
                    flow.emit("$name has left the chat.")
                    this@coroutineScope.coroutineContext.cancel()
                } else {
                    flow.emit(message)
                }
            }
        }
        // Coroutine to see if any messages are beng sent by other chatters.
            // need to make sure the message was not sent by this current chatter so they dont see their own message

        this.launch(Dispatchers.IO) {
            val delimiter = ':'
            flow.takeWhile {!connection.isClosed}.collect { message ->
                val s = message.split(delimiter)
                val sendName = s[0]
                if (sendName != name) out.println(message)
            }
        }
    }

    suspend fun checkUserName(userName: String) : Boolean{
        val taken: Boolean
        mutex.withLock {
            taken = userNames.contains(userName)
            if (!taken) userNames.add(userName)
        }

        return taken
    }

    fun end() {
        enabled = false
    }
}