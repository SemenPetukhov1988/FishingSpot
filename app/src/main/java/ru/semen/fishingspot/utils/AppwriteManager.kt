package ru.semen.fishingspot.utils

import android.content.Context
import io.appwrite.Client
import io.appwrite.services.Account
import io.appwrite.services.Databases
import io.appwrite.services.Storage

object AppwriteManager {
    lateinit var client: Client
    lateinit var account: Account
    lateinit var databases: Databases
    lateinit var storage: Storage

    fun init(context: Context) {
        client = Client(context)
            .setEndpoint("http://153.76.168.237/v1")
            .setProject("6acfd94b00107a8657d8")
            .setSelfSigned(status = true)

        account = Account(client)
        databases = Databases(client)
        storage = Storage(client)
    }
}