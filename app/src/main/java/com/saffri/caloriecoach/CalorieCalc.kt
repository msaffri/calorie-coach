package com.saffri.caloriecoach

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// ---------------- Profile & daily target ----------------

data class Profile(
    val currentWeightKg: Double,
    val targetWeightKg: Double,   // expected weight in 3 months (within ±15 kg)
    val heightCm: Double,
    val age: Int,
    val isMale: Boolean
)

const val PLAN_DAYS = 90
const val KCAL_PER_KG = 7700.0
const val MAX_CHANGE_KG = 15.0

object CalorieCalc {

    /** Mifflin–St Jeor basal metabolic rate. */
    fun bmr(p: Profile): Double =
        10 * p.currentWeightKg + 6.25 * p.heightCm - 5 * p.age + if (p.isMale) 5 else -161

    /** Resting/sedentary maintenance. Exercise is logged separately, so we use the 1.2 factor. */
    fun maintenance(p: Profile): Double = bmr(p) * 1.2

    /** kcal per day to add (gain) or remove (lose) to reach the 3-month goal. */
    fun dailyAdjustment(p: Profile): Double =
        (p.targetWeightKg - p.currentWeightKg) * KCAL_PER_KG / PLAN_DAYS

    /** Minimum safe intake. */
    fun floor(p: Profile): Double = if (p.isMale) 1500.0 else 1200.0

    /** Expected NET calories per day (food eaten minus exercise burned). */
    fun dailyTarget(p: Profile): Int =
        max(maintenance(p) + dailyAdjustment(p), floor(p)).roundToInt()

    /** Allowed band around the target that still counts as "good". */
    fun tolerance(target: Int): Int = max(150, (target * 0.10).roundToInt())

    fun status(net: Int, target: Int): NetStatus {
        val tol = tolerance(target)
        return when {
            net < target - tol -> NetStatus.LOW
            net > target + tol -> NetStatus.HIGH
            else -> NetStatus.GOOD
        }
    }

    fun validate(current: Double?, target: Double?, height: Double?, age: Int?): Map<String, String> {
        val e = mutableMapOf<String, String>()
        if (current == null || current !in 25.0..300.0) e["current"] = "Enter a weight between 25 and 300 kg"
        if (target == null || target !in 25.0..300.0) e["target"] = "Enter a weight between 25 and 300 kg"
        else if (current != null && abs(target - current) > MAX_CHANGE_KG)
            e["target"] = "Must be within 15 kg of your current weight"
        if (height == null || height !in 100.0..250.0) e["height"] = "Enter a height between 100 and 250 cm"
        if (age == null || age !in 13..100) e["age"] = "Enter an age between 13 and 100"
        return e
    }
}

enum class NetStatus { LOW, GOOD, HIGH }

// ---------------- Activities (MET values, Compendium of Physical Activities) ----------------

data class ActivityType(val name: String, val met: Double)

val ACTIVITIES = listOf(
    ActivityType("Walking (slow)", 2.8),
    ActivityType("Walking (brisk)", 4.3),
    ActivityType("Running / jogging", 9.8),
    ActivityType("Cycling", 7.5),
    ActivityType("Swimming", 7.0),
    ActivityType("Gym / weight training", 5.0),
    ActivityType("Yoga / stretching", 2.5),
    ActivityType("Football", 7.0),
    ActivityType("Cricket", 4.8),
    ActivityType("Badminton", 5.5),
    ActivityType("Tennis", 7.3),
    ActivityType("Dancing", 5.0),
    ActivityType("Hiking", 6.0),
    ActivityType("Jump rope", 11.0),
    ActivityType("Climbing stairs", 8.0),
    ActivityType("Housework / cleaning", 3.3),
    ActivityType("Gardening", 3.8),
    ActivityType("Other light activity", 3.0),
    ActivityType("Other vigorous activity", 7.0)
)

/** kcal = MET × 3.5 × weight(kg) / 200 × minutes */
fun burnedKcal(met: Double, weightKg: Double, minutes: Int): Int =
    (met * 3.5 * weightKg / 200.0 * minutes).roundToInt()

// ---------------- Food estimation from the meal note ----------------

/**
 * kcal per one [unit]. [grams] = weight of one unit, used when the note gives grams ("200g chicken").
 */
data class Food(val keys: List<String>, val kcal: Int, val unit: String, val grams: Int = 0)

val FOODS = listOf(
    // Grains & breads
    Food(listOf("rice", "white rice", "plain rice", "steamed rice"), 205, "cup", 158),
    Food(listOf("brown rice"), 215, "cup", 195),
    Food(listOf("fried rice", "egg fried rice"), 340, "cup", 200),
    Food(listOf("biryani", "chicken biryani", "mutton biryani"), 500, "plate", 350),
    Food(listOf("kabsa", "machboos", "majboos"), 600, "plate", 400),
    Food(listOf("mandi"), 650, "plate", 400),
    Food(listOf("chapati", "chapathi", "roti", "phulka"), 120, "piece", 40),
    Food(listOf("paratha", "parotta", "porotta"), 260, "piece", 80),
    Food(listOf("naan"), 260, "piece", 90),
    Food(listOf("khubz", "arabic bread", "pita", "pita bread"), 165, "piece", 60),
    Food(listOf("bread", "bread slice", "toast"), 80, "slice", 30),
    Food(listOf("oats", "oatmeal", "porridge"), 150, "bowl", 240),
    Food(listOf("cornflakes", "cereal"), 150, "bowl", 40),
    Food(listOf("pasta", "spaghetti", "macaroni"), 220, "cup", 140),
    Food(listOf("noodles", "instant noodles", "maggi"), 380, "pack", 80),
    Food(listOf("idli"), 39, "piece", 40),
    Food(listOf("dosa", "masala dosa"), 170, "piece", 100),
    Food(listOf("upma", "poha"), 250, "bowl", 200),
    // Proteins
    Food(listOf("egg", "boiled egg", "omelette", "omelet"), 78, "egg", 50),
    Food(listOf("chicken", "chicken breast", "grilled chicken"), 165, "100 g", 100),
    Food(listOf("chicken curry", "butter chicken"), 300, "bowl", 250),
    Food(listOf("fried chicken"), 290, "piece", 120),
    Food(listOf("shawarma", "chicken shawarma"), 450, "wrap", 250),
    Food(listOf("falafel"), 57, "piece", 17),
    Food(listOf("fish", "grilled fish", "salmon", "hammour"), 200, "100 g", 100),
    Food(listOf("fish curry"), 250, "bowl", 250),
    Food(listOf("mutton", "lamb", "beef", "meat"), 250, "100 g", 100),
    Food(listOf("mutton curry", "beef curry", "lamb curry"), 350, "bowl", 250),
    Food(listOf("kebab", "kabab", "seekh kebab"), 150, "piece", 60),
    Food(listOf("dal", "daal", "lentils", "lentil soup"), 180, "bowl", 240),
    Food(listOf("chickpeas", "chana", "chole"), 270, "bowl", 240),
    Food(listOf("paneer"), 265, "100 g", 100),
    Food(listOf("tuna"), 130, "100 g", 100),
    // Fast food
    Food(listOf("burger", "cheeseburger"), 350, "piece", 150),
    Food(listOf("pizza", "pizza slice"), 285, "slice", 110),
    Food(listOf("fries", "french fries", "chips"), 365, "portion", 115),
    Food(listOf("sandwich"), 300, "piece", 150),
    Food(listOf("samosa"), 260, "piece", 100),
    Food(listOf("puffs", "puff"), 280, "piece", 90),
    // Dairy & drinks
    Food(listOf("milk"), 150, "cup", 240),
    Food(listOf("yogurt", "yoghurt", "curd"), 150, "cup", 245),
    Food(listOf("laban", "buttermilk", "lassi"), 100, "glass", 240),
    Food(listOf("cheese", "cheese slice"), 113, "slice", 28),
    Food(listOf("tea", "chai", "karak", "karak chai"), 60, "cup", 150),
    Food(listOf("black tea", "green tea", "black coffee"), 2, "cup", 240),
    Food(listOf("coffee", "latte", "cappuccino"), 150, "cup", 240),
    Food(listOf("juice", "orange juice", "fresh juice"), 110, "glass", 240),
    Food(listOf("soda", "coke", "pepsi", "soft drink"), 140, "can", 330),
    Food(listOf("smoothie", "milkshake", "shake"), 300, "glass", 300),
    // Fruit, veg, snacks
    Food(listOf("banana"), 105, "piece", 118),
    Food(listOf("apple"), 95, "piece", 180),
    Food(listOf("orange"), 62, "piece", 130),
    Food(listOf("mango"), 200, "piece", 330),
    Food(listOf("grapes"), 104, "cup", 150),
    Food(listOf("watermelon", "melon"), 46, "cup", 150),
    Food(listOf("dates", "date"), 23, "piece", 8),
    Food(listOf("salad", "green salad", "fattoush", "tabbouleh"), 120, "bowl", 150),
    Food(listOf("vegetables", "veg curry", "sabzi", "mixed veg"), 150, "bowl", 200),
    Food(listOf("soup"), 100, "bowl", 250),
    Food(listOf("hummus"), 70, "2 tbsp", 30),
    Food(listOf("nuts", "almonds", "cashews", "peanuts"), 170, "handful", 30),
    Food(listOf("biscuit", "biscuits", "cookie", "cookies"), 50, "piece", 12),
    Food(listOf("chocolate", "chocolate bar"), 230, "bar", 45),
    Food(listOf("cake", "cake slice"), 350, "slice", 100),
    Food(listOf("ice cream"), 140, "scoop", 70),
    Food(listOf("kunafa", "knafeh", "baklava", "sweet", "sweets", "dessert"), 300, "piece", 80),
    Food(listOf("protein bar"), 200, "bar", 60),
    Food(listOf("protein shake", "whey"), 120, "scoop", 30)
)

data class FoodMatch(val text: String, val food: Food?, val qty: Double, val kcal: Int)

object FoodEstimator {
    private val numberWords = mapOf(
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "half" to 0.5, "two" to 2.0, "three" to 3.0,
        "four" to 4.0, "five" to 5.0, "six" to 6.0, "couple" to 2.0, "few" to 3.0
    )

    private val patterns: List<Pair<Regex, Food>> =
        FOODS.flatMap { f -> f.keys.map { k -> Regex("\\b" + Regex.escape(k) + "(e?s)?\\b") to f } }
            .sortedByDescending { it.first.pattern.length } // longest (most specific) key wins

    fun estimate(note: String): List<FoodMatch> =
        note.lowercase()
            .split(Regex(",|;|\\n|\\+|\\band\\b|\\bwith\\b"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { part -> matchPart(part) }

    fun total(matches: List<FoodMatch>): Int = matches.sumOf { it.kcal }

    private fun matchPart(part: String): FoodMatch {
        val food = patterns.firstOrNull { it.first.containsMatchIn(part) }?.second
            ?: return FoodMatch(part, null, 0.0, 0)

        val gramsMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*(g|gm|gms|gram|grams|ml)\\b").find(part)
        val qty: Double = when {
            gramsMatch != null && food.grams > 0 -> gramsMatch.groupValues[1].toDouble() / food.grams
            else -> leadingQuantity(part)
        }
        return FoodMatch(part, food, qty, (food.kcal * qty).roundToInt())
    }

    private fun leadingQuantity(part: String): Double {
        Regex("(\\d+)\\s*/\\s*(\\d+)").find(part)?.let {
            val d = it.groupValues[2].toDouble()
            if (d != 0.0) return it.groupValues[1].toDouble() / d
        }
        Regex("\\d+(?:\\.\\d+)?").find(part)?.let { return it.value.toDouble() }
        part.split(Regex("\\s+")).forEach { w -> numberWords[w]?.let { return it } }
        return 1.0
    }

    fun describe(m: FoodMatch): String =
        if (m.food == null) "• \"${m.text}\" — not recognised"
        else "• ${fmtQty(m.qty)} ${m.food.unit} ${m.food.keys.first()} — ${m.kcal} kcal"

    private fun fmtQty(q: Double): String =
        if (q == q.roundToInt().toDouble()) q.roundToInt().toString() else "%.1f".format(q)
}
