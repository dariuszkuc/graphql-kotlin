package com.expediagroup.graphql.plugin

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.util.KtorExperimentalAPI
import java.io.File

@KtorExperimentalAPI
suspend fun downloadSchema(endpoint: String, outputFile: File) {
    HttpClient(CIO).use { client ->
        val sdl = client.get<String>(urlString = endpoint)
        outputFile.writeText(sdl)
    }
}
