package com.example.ui.viewmodel

object FinanceConstants {
    const val KEY_LINK_HABAYEB_DEBTS = "link_habayeb_debts"
    const val KEY_ONBOARDING_SHOWN = "has_shown_onboarding"
    const val PREFS_NAME = "mizan_prefs"
}

enum class HabayebTransactionType {
    OWED_BY_THEM,
    PAYMENT_BY_THEM,
    OWED_TO_THEM,
    PAYMENT_TO_THEM
}
