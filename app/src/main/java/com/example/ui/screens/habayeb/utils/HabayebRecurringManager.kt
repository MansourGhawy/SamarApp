package com.example.ui.screens.habayeb.utils

import android.content.Context
import android.widget.Toast
import com.example.data.local.entities.HabayebTransaction
import com.example.ui.viewmodel.FinanceViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

data class RecurringConfig(
    val id: String,
    val originalTxId: String,
    val customerId: String,
    val customerName: String,
    val amount: Double,
    val type: String,
    val description: String,
    val frequency: String, // "DAILY", "WEEKLY", "MONTHLY"
    val daysOfWeek: List<Int>, // 1 (Sunday) to 7 (Saturday)
    val daysOfMonth: List<Int>, // 1 to 31
    val timeHour: Int,
    val timeMinute: Int,
    val startDateMillis: Long,
    val endDateMillis: Long,
    val lastExecutedTimestamp: Long, // in seconds
    val isActive: Boolean = true
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("originalTxId", originalTxId)
        obj.put("customerId", customerId)
        obj.put("customerName", customerName)
        obj.put("amount", amount)
        obj.put("type", type)
        obj.put("description", description)
        obj.put("frequency", frequency)
        
        val dowArray = JSONArray()
        daysOfWeek.forEach { dowArray.put(it) }
        obj.put("daysOfWeek", dowArray)

        val domArray = JSONArray()
        daysOfMonth.forEach { domArray.put(it) }
        obj.put("daysOfMonth", domArray)

        obj.put("timeHour", timeHour)
        obj.put("timeMinute", timeMinute)
        obj.put("startDateMillis", startDateMillis)
        obj.put("endDateMillis", endDateMillis)
        obj.put("lastExecutedTimestamp", lastExecutedTimestamp)
        obj.put("isActive", isActive)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): RecurringConfig {
            val dowList = mutableListOf<Int>()
            val dowArray = obj.optJSONArray("daysOfWeek")
            if (dowArray != null) {
                for (i in 0 until dowArray.length()) {
                    dowList.add(dowArray.getInt(i))
                }
            }

            val domList = mutableListOf<Int>()
            val domArray = obj.optJSONArray("daysOfMonth")
            if (domArray != null) {
                for (i in 0 until domArray.length()) {
                    domList.add(domArray.getInt(i))
                }
            }

            return RecurringConfig(
                id = obj.getString("id"),
                originalTxId = obj.optString("originalTxId", ""),
                customerId = obj.getString("customerId"),
                customerName = obj.optString("customerName", "عميل"),
                amount = obj.getDouble("amount"),
                type = obj.getString("type"),
                description = obj.optString("description", ""),
                frequency = obj.getString("frequency"),
                daysOfWeek = dowList,
                daysOfMonth = domList,
                timeHour = obj.getInt("timeHour"),
                timeMinute = obj.getInt("timeMinute"),
                startDateMillis = obj.getLong("startDateMillis"),
                endDateMillis = obj.getLong("endDateMillis"),
                lastExecutedTimestamp = obj.getLong("lastExecutedTimestamp"),
                isActive = obj.optBoolean("isActive", true)
            )
        }
    }
}

object HabayebRecurringManager {
    private const val PREFS_NAME = "mizan_recurring_prefs"
    private const val KEY_CONFIGS = "recurring_configs"

    fun getAllConfigs(context: Context): List<RecurringConfig> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CONFIGS, "[]") ?: "[]"
        val list = mutableListOf<RecurringConfig>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                list.add(RecurringConfig.fromJsonObject(array.getJSONObject(i)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveConfig(context: Context, config: RecurringConfig) {
        val configs = getAllConfigs(context).toMutableList()
        configs.removeAll { it.id == config.id || (config.originalTxId.isNotEmpty() && it.originalTxId == config.originalTxId) }
        configs.add(config)
        saveConfigsList(context, configs)
    }

    fun deleteConfig(context: Context, configId: String) {
        val configs = getAllConfigs(context).toMutableList()
        configs.removeAll { it.id == configId }
        saveConfigsList(context, configs)
    }
    
    fun deleteConfigForTransaction(context: Context, txId: String) {
        val configs = getAllConfigs(context).toMutableList()
        configs.removeAll { it.originalTxId == txId }
        saveConfigsList(context, configs)
    }

    private fun saveConfigsList(context: Context, configs: List<RecurringConfig>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        configs.forEach { array.put(it.toJsonObject()) }
        prefs.edit().putString(KEY_CONFIGS, array.toString()).apply()
    }

    fun checkAndExecuteRecurring(context: Context, viewModel: FinanceViewModel, onExecuted: (Int) -> Unit = {}) {
        val configs = getAllConfigs(context)
        if (configs.isEmpty()) return

        val updatedConfigs = mutableListOf<RecurringConfig>()
        var executedCount = 0
        val now = System.currentTimeMillis()

        for (config in configs) {
            if (!config.isActive) {
                updatedConfigs.add(config)
                continue
            }

            val startCheckMillis = if (config.lastExecutedTimestamp > 0L) {
                config.lastExecutedTimestamp * 1000 + 1000
            } else {
                config.startDateMillis
            }

            val endCheckMillis = Math.min(now, config.endDateMillis)
            if (startCheckMillis >= endCheckMillis) {
                updatedConfigs.add(config)
                continue
            }

            // Step day-by-day
            val cal = Calendar.getInstance()
            cal.timeInMillis = startCheckMillis
            
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            val matchesTimestamps = mutableListOf<Long>()
            val maxDaysToSweep = 365
            var sweepCount = 0

            while (cal.timeInMillis <= endCheckMillis && sweepCount < maxDaysToSweep) {
                sweepCount++
                
                val occurrenceCal = Calendar.getInstance()
                occurrenceCal.timeInMillis = cal.timeInMillis
                occurrenceCal.set(Calendar.HOUR_OF_DAY, config.timeHour)
                occurrenceCal.set(Calendar.MINUTE, config.timeMinute)
                occurrenceCal.set(Calendar.SECOND, 0)
                occurrenceCal.set(Calendar.MILLISECOND, 0)

                val occurrenceTime = occurrenceCal.timeInMillis
                if (occurrenceTime in startCheckMillis..endCheckMillis) {
                    var isMatched = false
                    when (config.frequency) {
                        "DAILY" -> {
                            isMatched = true
                        }
                        "WEEKLY" -> {
                            val dow = occurrenceCal.get(Calendar.DAY_OF_WEEK)
                            if (config.daysOfWeek.contains(dow)) {
                                isMatched = true
                            }
                        }
                        "MONTHLY" -> {
                            val dom = occurrenceCal.get(Calendar.DAY_OF_MONTH)
                            if (config.daysOfMonth.contains(dom)) {
                                isMatched = true
                            }
                        }
                    }

                    if (isMatched) {
                        matchesTimestamps.add(occurrenceTime / 1000)
                    }
                }
                
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }

            if (matchesTimestamps.isNotEmpty()) {
                matchesTimestamps.sort()
                val finalToExecute = matchesTimestamps.take(50)
                for (ts in finalToExecute) {
                    viewModel.addHabayebTransaction(
                        customerId = config.customerId,
                        type = config.type,
                        amount = config.amount,
                        desc = config.description,
                        timestamp = ts,
                        linkedMainTxId = config.originalTxId
                    )
                    executedCount++
                }

                val lastTs = finalToExecute.last()
                updatedConfigs.add(config.copy(lastExecutedTimestamp = lastTs))
            } else {
                updatedConfigs.add(config)
            }
        }

        if (executedCount > 0) {
            saveConfigsList(context, updatedConfigs)
            onExecuted(executedCount)
        }
    }
}
