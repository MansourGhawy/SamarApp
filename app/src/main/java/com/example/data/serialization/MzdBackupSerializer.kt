package com.example.data.serialization

import com.example.data.local.*
import com.example.data.local.entities.*
import org.json.JSONArray
import org.json.JSONObject

object MzdBackupSerializer {

    fun exportBackupToJson(
        settings: AppSettings,
        commitments: List<FixedCommitment>,
        transactions: List<TransactionDb>,
        habayebCustomers: List<HabayebCustomer> = emptyList(),
        habayebTransactions: List<HabayebTransaction> = emptyList(),
        deletedItems: List<DeletedItemEntity> = emptyList()
    ): String {
        val root = JSONObject()

        val metadata = JSONObject()
        metadata.put("app_name", "Mizan Al-Dar")
        metadata.put("app_version", "1.1.0")
        metadata.put("backup_timestamp", System.currentTimeMillis() / 1000)
        metadata.put("security_hash", "security_" + (settings.hashCode() + transactions.size * 31).toString())
        root.put("metadata", metadata)

        val settingsObj = JSONObject()
        settingsObj.put("currency_symbol", settings.currencySymbol)
        settingsObj.put("school_expenses_enabled", settings.schoolExpensesEnabled)
        settingsObj.put("theme_mode", settings.themeMode)
        root.put("settings", settingsObj)

        val commitmentsArr = JSONArray()
        for (fc in commitments) {
            val fcObj = JSONObject()
            fcObj.put("name", fc.name)
            fcObj.put("target_amount", fc.targetAmount)
            fcObj.put("current_progress", fc.currentProgress)
            fcObj.put("order_index", fc.orderIndex)
            commitmentsArr.put(fcObj)
        }
        root.put("fixed_commitments", commitmentsArr)

        val transactionsArr = JSONArray()
        for (tx in transactions) {
            val txObj = JSONObject()
            txObj.put("id", tx.id)
            txObj.put("timestamp", tx.timestamp)
            txObj.put("type", tx.type)
            txObj.put("category", tx.category)
            txObj.put("amount", tx.amount)
            txObj.put("description", tx.description)
            transactionsArr.put(txObj)
        }
        root.put("transactions", transactionsArr)

        val habayebObj = JSONObject()
        val habayebCustomersArr = JSONArray()
        for (c in habayebCustomers) {
            val cObj = JSONObject()
            cObj.put("id", c.id)
            cObj.put("name", c.name)
            cObj.put("phone", c.phone)
            cObj.put("notes", c.notes)
            cObj.put("created_at", c.createdAt)
            habayebCustomersArr.put(cObj)
        }
        habayebObj.put("customers", habayebCustomersArr)

        val habayebTxsArr = JSONArray()
        for (t in habayebTransactions) {
            val tObj = JSONObject()
            tObj.put("id", t.id)
            tObj.put("customer_id", t.customerId)
            tObj.put("type", t.type)
            tObj.put("amount", t.amount)
            tObj.put("timestamp", t.timestamp)
            tObj.put("description", t.description)
            tObj.put("linked_main_tx_id", t.linkedMainTxId)
            habayebTxsArr.put(tObj)
        }
        habayebObj.put("debt_transactions", habayebTxsArr)
        root.put("habayeb_debts", habayebObj)

        val deletedItemsArr = JSONArray()
        for (di in deletedItems) {
            val diObj = JSONObject()
            diObj.put("id", di.id)
            diObj.put("sourceSystem", di.sourceSystem)
            diObj.put("originalTableName", di.originalTableName)
            diObj.put("jsonData", di.jsonData)
            diObj.put("deletedAt", di.deletedAt)
            deletedItemsArr.put(diObj)
        }
        root.put("deleted_items", deletedItemsArr)

        return root.toString(2)
    }

    fun importBackupFromJson(jsonString: String): Triple<AppSettings, List<FixedCommitment>, List<TransactionDb>> {
        val root = JSONObject(jsonString)
        val sourceObj = if (root.has("mizan_al_dar_db")) root.getJSONObject("mizan_al_dar_db") else root

        val settingsObj = sourceObj.optJSONObject("settings")
        val settings = if (settingsObj != null) {
            AppSettings(
                currencySymbol = settingsObj.optString("currency_symbol", "ر.ي"),
                schoolExpensesEnabled = settingsObj.optBoolean("school_expenses_enabled", true),
                themeMode = settingsObj.optInt("theme_mode", 0)
            )
        } else {
            AppSettings()
        }

        val commitmentsList = mutableListOf<FixedCommitment>()
        val commitmentsArr = sourceObj.optJSONArray("fixed_commitments")
        if (commitmentsArr != null) {
            for (i in 0 until commitmentsArr.length()) {
                val obj = commitmentsArr.getJSONObject(i)
                commitmentsList.add(
                    FixedCommitment(
                        name = obj.getString("name"),
                        targetAmount = obj.getDouble("target_amount"),
                        currentProgress = obj.getDouble("current_progress"),
                        orderIndex = obj.optInt("order_index", i)
                    )
                )
            }
        }

        val transactionsList = mutableListOf<TransactionDb>()
        val transactionsArr = sourceObj.optJSONArray("transactions")
        if (transactionsArr != null) {
            for (i in 0 until transactionsArr.length()) {
                val obj = transactionsArr.getJSONObject(i)
                transactionsList.add(
                    TransactionDb(
                        id = obj.getString("id"),
                        timestamp = obj.getLong("timestamp"),
                        type = obj.getString("type"),
                        category = obj.getString("category"),
                        amount = obj.getDouble("amount"),
                        description = obj.optString("description", "")
                    )
                )
            }
        }

        return Triple(settings, commitmentsList, transactionsList)
    }
}
