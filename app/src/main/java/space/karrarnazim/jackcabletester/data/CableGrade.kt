package space.karrarnazim.jackcabletester.data

/**
 * Turns the 0-100 cable quality score into a school-style letter grade,
 * so people who don't want to think about percentages get a quick answer:
 * "is this cable fine or not".
 */
object CableGrade {

    // Thresholds, highest first. First match wins.
    private val thresholds = listOf(
        98f to "A+",
        95f to "A",
        90f to "A-",
        85f to "B+",
        80f to "B",
        75f to "B-",
        70f to "C+",
        65f to "C",
        60f to "C-",
        55f to "D+",
        50f to "D",
        45f to "D-"
    )

    fun forScore(score: Float): String =
        thresholds.firstOrNull { score >= it.first }?.second ?: "F"

    /** Short, plain-language meaning of a grade — shown next to the letter. */
    fun description(grade: String): String = when {
        grade.startsWith("A") -> "Excellent — cable is fine"
        grade.startsWith("B") -> "Good — minor loss, still reliable"
        grade.startsWith("C") -> "Usable — noticeable loss"
        grade.startsWith("D") -> "Poor — significant loss"
        else -> "Failing — cable or connection is bad"
    }
}
