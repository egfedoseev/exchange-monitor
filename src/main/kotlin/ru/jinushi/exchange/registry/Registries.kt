package ru.jinushi.exchange.registry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import ru.jinushi.exchange.CurrencyPair
import ru.jinushi.exchange.Exchange
import ru.jinushi.exchange.analyzer.ArbitrageAnalyzer
import ru.jinushi.exchange.wallet.Wallet
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

class ExchangeRegistry {
    private val exchanges = ConcurrentHashMap<String, Exchange>()

    fun register(exchange: Exchange) {
        exchanges[exchange.name] = exchange
    }

    fun get(name: String): Exchange? = exchanges[name]

    fun getAll(): Map<String, Exchange> = exchanges
}

class WalletRegistry {
    private val wallets = ConcurrentHashMap<String, Wallet>()

    fun register(name: String, wallet: Wallet) {
        wallets[name] = wallet
    }

    fun get(name: String): Wallet? = wallets[name]

    fun getAll(): Map<String, Wallet> = wallets
}

class AnalyzerRegistry : Closeable {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val analyzers = ConcurrentHashMap<CurrencyPair, ArbitrageAnalyzer>()
    private val activeJobs = ConcurrentHashMap<CurrencyPair, Job>()

    fun register(pair: CurrencyPair, analyzer: ArbitrageAnalyzer, job: Job) {
        analyzers[pair] = analyzer
        activeJobs[pair] = job
    }

    fun get(pair: CurrencyPair): ArbitrageAnalyzer? = analyzers[pair]

    fun contains(pair: CurrencyPair): Boolean = analyzers.containsKey(pair)

    fun getAll(): Map<CurrencyPair, ArbitrageAnalyzer> = analyzers

    fun stop(pair: CurrencyPair): Boolean {
        val job = activeJobs.remove(pair) ?: return false
        job.cancel()
        analyzers.remove(pair)?.close()
        return true
    }

    override fun close() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        analyzers.values.forEach { it.close() }
        analyzers.clear()
    }
}
