package com.example.cli

import java.io.BufferedReader
import java.io.InputStreamReader

/** The server address: first argument, else RENT_A_CAR_URL, else the local default. */
fun main(args: Array<String>) {
    val url = args.firstOrNull() ?: System.getenv("RENT_A_CAR_URL") ?: "http://localhost:8080"
    val console = Console(BufferedReader(InputStreamReader(System.`in`)), System.out)
    runClient(Context(HttpApi(url), console))
}
