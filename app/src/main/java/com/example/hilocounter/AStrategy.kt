package com.example.hilocounter

object Strategy {

    enum class Action(val label: String) {
        HIT("CHIAMA"),
        STAND("STAI"),
        DOUBLE("RADDOPPIA"),
        SPLIT("SPLITTA"),
        SURRENDER("RESA")
    }

    data class Recommendation(
        val action: Action,
        val detail: String,
        val isDeviation: Boolean
    )

    fun recommend(
        playerCards: List<String>,
        dealerUpcard: String,
        tc: Double
    ): Recommendation? {
        if (playerCards.size < 2) return null

        val dealerValue = valueOf(dealerUpcard)
        val isPair = playerCards.size == 2 && playerCards[0] == playerCards[1]
        val total = total(playerCards)
        val soft = isSoft(playerCards)

        if (isPair) {
            pairStrategy(playerCards[0], dealerValue, tc)?.let { return it }
        }
        if (soft) return softStrategy(total, dealerValue, tc)
        return hardStrategy(total, dealerValue, tc)
    }

    private fun valueOf(c: String): Int = when (c) {
        "A" -> 11
        "10", "J", "Q", "K" -> 10
        else -> c.toInt()
    }

    private fun total(cards: List<String>): Int {
        var t = 0; var aces = 0
        for (c in cards) {
            when (c) {
                "A" -> { t += 11; aces++ }
                "10", "J", "Q", "K" -> t += 10
                else -> t += c.toInt()
            }
        }
        while (t > 21 && aces > 0) { t -= 10; aces-- }
        return t
    }

    private fun isSoft(cards: List<String>): Boolean {
        var t = 0; var aces = 0
        for (c in cards) {
            when (c) {
                "A" -> { t += 11; aces++ }
                "10", "J", "Q", "K" -> t += 10
                else -> t += c.toInt()
            }
        }
        if (aces == 0) return false
        while (t > 21 && aces > 0) { t -= 10; aces-- }
        return aces > 0
    }

    private fun dealerName(v: Int) = if (v == 11) "A" else v.toString()

    private fun pairStrategy(rank: String, dealer: Int, tc: Double): Recommendation? {
        return when (rank) {
            "A" -> Recommendation(Action.SPLIT, "A,A — splitta sempre", false)
            "10", "J", "Q", "K" -> when {
                dealer == 5 && tc >= 5.0 ->
                    Recommendation(Action.SPLIT, "10,10 vs 5 — split (TC>=5)", true)
                dealer == 6 && tc >= 4.0 ->
                    Recommendation(Action.SPLIT, "10,10 vs 6 — split (TC>=4)", true)
                else -> Recommendation(Action.STAND, "10,10 — stai", false)
            }
            "9" -> when (dealer) {
                2,3,4,5,6,8,9 -> Recommendation(Action.SPLIT, "9,9 — splitta", false)
                else -> Recommendation(Action.STAND, "9,9 vs ${dealerName(dealer)} — stai", false)
            }
            "8" -> Recommendation(Action.SPLIT, "8,8 — splitta sempre", false)
            "7" -> when (dealer) {
                2,3,4,5,6,7 -> Recommendation(Action.SPLIT, "7,7 — splitta", false)
                else -> Recommendation(Action.HIT, "7,7 — chiama", false)
            }
            "6" -> when (dealer) {
                2 -> if (tc >= 1.0)
                    Recommendation(Action.SPLIT, "6,6 vs 2 — split (TC>=1)", true)
                else Recommendation(Action.HIT, "6,6 vs 2 — chiama", false)
                3,4,5,6 -> Recommendation(Action.SPLIT, "6,6 — splitta", false)
                else -> Recommendation(Action.HIT, "6,6 — chiama", false)
            }
            "5" -> null
            "4" -> when (dealer) {
                5,6 -> Recommendation(Action.SPLIT, "4,4 — splitta", false)
                else -> Recommendation(Action.HIT, "4,4 — chiama", false)
            }
            "3" -> when (dealer) {
                2,3,4,5,6,7 -> Recommendation(Action.SPLIT, "3,3 — splitta", false)
                else -> Recommendation(Action.HIT, "3,3 — chiama", false)
            }
            "2" -> when (dealer) {
                2,3,4,5,6,7 -> Recommendation(Action.SPLIT, "2,2 — splitta", false)
                else -> Recommendation(Action.HIT, "2,2 — chiama", false)
            }
            else -> null
        }
    }

    private fun softStrategy(total: Int, dealer: Int, tc: Double): Recommendation {
        return when (total) {
            13, 14 -> when (dealer) {
                5,6 -> Recommendation(Action.DOUBLE, "Soft $total — raddoppia", false)
                else -> Recommendation(Action.HIT, "Soft $total — chiama", false)
            }
            15, 16 -> when (dealer) {
                4,5,6 -> Recommendation(Action.DOUBLE, "Soft $total — raddoppia", false)
                else -> Recommendation(Action.HIT, "Soft $total — chiama", false)
            }
            17 -> when (dealer) {
                3,4,5,6 -> Recommendation(Action.DOUBLE, "Soft 17 — raddoppia", false)
                else -> Recommendation(Action.HIT, "Soft 17 — chiama", false)
            }
            18 -> when (dealer) {
                2,7,8 -> Recommendation(Action.STAND, "Soft 18 — stai", false)
                3,4,5,6 -> Recommendation(Action.DOUBLE, "Soft 18 — raddoppia", false)
                9,10,11 -> Recommendation(Action.HIT, "Soft 18 — chiama", false)
                else -> Recommendation(Action.STAND, "Soft 18 — stai", false)
            }
            19 -> if (dealer == 6 && tc >= 1.0)
                Recommendation(Action.DOUBLE, "Soft 19 vs 6 — raddoppia (TC>=1)", true)
            else Recommendation(Action.STAND, "Soft 19 — stai", false)
            else -> Recommendation(Action.STAND, "Soft $total — stai", false)
        }
    }

    private fun hardStrategy(total: Int, dealer: Int, tc: Double): Recommendation {
        if (total == 16 && dealer == 10 && tc >= 0.0)
            return Recommendation(Action.STAND, "16 vs 10 — stai (TC>=0)", true)
        if (total == 15 && dealer == 10 && tc >= 4.0)
            return Recommendation(Action.STAND, "15 vs 10 — stai (TC>=4)", true)
        if (total == 16 && dealer == 9 && tc >= 5.0)
            return Recommendation(Action.STAND, "16 vs 9 — stai (TC>=5)", true)
        if (total == 12 && dealer == 3 && tc >= 2.0)
            return Recommendation(Action.STAND, "12 vs 3 — stai (TC>=2)", true)
        if (total == 12 && dealer == 2 && tc >= 3.0)
            return Recommendation(Action.STAND, "12 vs 2 — stai (TC>=3)", true)
        if (total == 13 && dealer == 2 && tc >= -1.0)
            return Recommendation(Action.STAND, "13 vs 2 — stai (TC>=-1)", true)
        if (total == 10 && dealer == 10 && tc >= 4.0)
            return Recommendation(Action.DOUBLE, "10 vs 10 — raddoppia (TC>=4)", true)
        if (total == 10 && dealer == 11 && tc >= 4.0)
            return Recommendation(Action.DOUBLE, "10 vs A — raddoppia (TC>=4)", true)
        if (total == 11 && dealer == 11 && tc >= 1.0)
            return Recommendation(Action.DOUBLE, "11 vs A — raddoppia (TC>=1)", true)
        if (total == 9 && dealer == 2 && tc >= 1.0)
            return Recommendation(Action.DOUBLE, "9 vs 2 — raddoppia (TC>=1)", true)
        if (total == 9 && dealer == 7 && tc >= 3.0)
            return Recommendation(Action.DOUBLE, "9 vs 7 — raddoppia (TC>=3)", true)

        return when {
            total <= 8 -> Recommendation(Action.HIT, "Hard $total — chiama", false)
            total == 9 -> if (dealer in 3..6)
                Recommendation(Action.DOUBLE, "9 — raddoppia", false)
            else Recommendation(Action.HIT, "9 — chiama", false)
            total == 10 -> if (dealer in 2..9)
                Recommendation(Action.DOUBLE, "10 — raddoppia", false)
            else Recommendation(Action.HIT, "10 — chiama", false)
            total == 11 -> if (dealer in 2..10)
                Recommendation(Action.DOUBLE, "11 — raddoppia", false)
            else Recommendation(Action.HIT, "11 vs A — chiama", false)
            total == 12 -> if (dealer in 4..6)
                Recommendation(Action.STAND, "12 — stai", false)
            else Recommendation(Action.HIT, "12 — chiama", false)
            total in 13..14 -> if (dealer in 2..6)
                Recommendation(Action.STAND, "$total — stai", false)
            else Recommendation(Action.HIT, "$total — chiama", false)
            total == 15 -> if (dealer in 2..6)
                Recommendation(Action.STAND, "15 — stai", false)
            else Recommendation(Action.HIT, "15 — chiama", false)
            total == 16 -> if (dealer in 2..6)
                Recommendation(Action.STAND, "16 — stai", false)
            else Recommendation(Action.HIT, "16 — chiama", false)
            else -> Recommendation(Action.STAND, "$total — stai", false)
        }
    }
}
