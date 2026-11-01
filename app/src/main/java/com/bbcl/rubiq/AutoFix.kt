// Repairs single "hand-made" physical defects (twisted corner, flipped edge/wing,
// swapped pieces) so an otherwise legal cube can be solved.
package com.bbcl.rubiq

private val fixable = setOf(
    ValidationError.CORNER_TWIST,
    ValidationError.EDGE_FLIP,
    ValidationError.WING_FLIP,
    ValidationError.PERMUTATION_PARITY,
)

class AutoFixResult(val state: String, val fixes: List<String>)

private fun CharArray.swap(a: Int, b: Int) {
    val t = this[a]
    this[a] = this[b]
    this[b] = t
}

fun autoFixCube(state: String, n: Int): AutoFixResult? {
    val slots = getPieceSlots(n)
    val fixes = mutableListOf<String>()
    var current = state

    repeat(6) {
        val v = validateCube(current, n)
        if (v.valid) return AutoFixResult(current, fixes)
        if (v.errorType == null || v.errorType !in fixable) return null
        val arr = current.toCharArray()

        if (v.errorType == ValidationError.CORNER_TWIST) {
            val corners = readCorners(current, slots)
            val sum = corners.sumOf { it.twist } % 3
            val target = corners.firstOrNull { it.twist == sum } ?: corners.first { it.twist != 0 }
            val f = slots.corners[target.slot]
            val (a, b, c) = f
            // Rotate the three stickers so this corner's twist drops by `sum`.
            if (sum == 1) {
                val t = arr[a]
                arr[a] = arr[b]
                arr[b] = arr[c]
                arr[c] = t
            } else {
                val t = arr[c]
                arr[c] = arr[b]
                arr[b] = arr[a]
                arr[a] = t
            }
            fixes.add("Twisted the ${pieceName(f, slots)} corner back into place")
        } else if (v.errorType == ValidationError.EDGE_FLIP || v.errorType == ValidationError.WING_FLIP) {
            val wing = v.errorType == ValidationError.WING_FLIP
            val pairs = if (wing || n == 3) slots.edges else slots.midges
            val e = readEdges(current, slots, pairs).first { it.flip == 1 }
            val f = pairs[e.slot]
            arr.swap(f[0], f[1])
            fixes.add("Flipped the ${pieceName(f, slots)} ${if (wing) "edge wing" else "edge"} back")
        } else {
            // Swap two edge pieces (orientation preserved) to balance permutation parity.
            val pairs = if (n == 3) slots.edges else slots.midges
            val a = pairs[0]
            val b = pairs[1]
            arr.swap(a[0], b[0])
            arr.swap(a[1], b[1])
            fixes.add("Swapped the ${pieceName(a, slots)} and ${pieceName(b, slots)} edges to fix permutation parity")
        }

        val next = String(arr)
        val after = validateCube(next, n)
        // Never make things worse: the same error must not come back on the same pieces.
        if (!after.valid && after.errorType == v.errorType && after.highlight == v.highlight) return null
        current = next
    }
    return null
}
