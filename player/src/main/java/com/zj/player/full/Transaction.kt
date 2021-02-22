package com.zj.player.full

@Suppress("unused")
class Transaction(val fromUser: Boolean, fullScreenTransactionTime: Int, startOnly: Boolean, val payloads: Map<String, Any?>? = null) {
    var transactionTime: Int = fullScreenTransactionTime
    var isStartOnly: Boolean = startOnly
}