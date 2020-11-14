package com.zj.player.full

@Suppress("unused")
class Transaction(internal val formUser: Boolean, fullScreenTransactionTime: Int, startOnly: Boolean, val payloads: Map<String, Any?>? = null) {
    var transactionTime: Int = fullScreenTransactionTime
    var isStartOnly: Boolean = startOnly
}