// Cubie-level 3x3 model and the two-phase coordinates, ported from the `cubejs` library
// (lib/cube.js and the coordinate part of lib/solve.js) that the web app used.
package com.bbcl.rubiq

// Corners
private const val URF = 0
private const val DLF = 5
private const val DRB = 7

// Edges
private const val UR = 0
private const val UL = 2
private const val UB = 3
private const val DF = 5
private const val FR = 8
private const val BR = 11

private fun u(x: Int) = x - 1
private fun r(x: Int) = u(9) + x
private fun f(x: Int) = r(9) + x
private fun d(x: Int) = f(9) + x
private fun l(x: Int) = d(9) + x
private fun b(x: Int) = l(9) + x

val cornerFacelet = listOf(
    intArrayOf(u(9), r(1), f(3)),
    intArrayOf(u(7), f(1), l(3)),
    intArrayOf(u(1), l(1), b(3)),
    intArrayOf(u(3), b(1), r(3)),
    intArrayOf(d(3), f(9), r(7)),
    intArrayOf(d(1), l(9), f(7)),
    intArrayOf(d(7), b(9), l(7)),
    intArrayOf(d(9), r(9), b(7)),
)

val edgeFacelet = listOf(
    intArrayOf(u(6), r(2)),
    intArrayOf(u(8), f(2)),
    intArrayOf(u(4), l(2)),
    intArrayOf(u(2), b(2)),
    intArrayOf(d(6), r(8)),
    intArrayOf(d(2), f(8)),
    intArrayOf(d(4), l(8)),
    intArrayOf(d(8), b(8)),
    intArrayOf(f(6), r(4)),
    intArrayOf(f(4), l(6)),
    intArrayOf(b(6), l(4)),
    intArrayOf(b(4), r(6)),
)

val cornerColor = listOf("URF", "UFL", "ULB", "UBR", "DFR", "DLF", "DBL", "DRB")

val edgeColor = listOf("UR", "UF", "UL", "UB", "DR", "DF", "DL", "DB", "FR", "FL", "BL", "BR")

/** A 3x3 cube as corner/edge permutation and orientation (centres fixed). */
class CubieCube(
    var cp: IntArray = IntArray(8) { it },
    var co: IntArray = IntArray(8),
    var ep: IntArray = IntArray(12) { it },
    var eo: IntArray = IntArray(12),
) {
    private var newCp = IntArray(8)
    private var newCo = IntArray(8)
    private var newEp = IntArray(12)
    private var newEo = IntArray(12)

    fun cornerMultiply(other: CubieCube) {
        for (to in 0 until 8) {
            val from = other.cp[to]
            newCp[to] = cp[from]
            newCo[to] = (co[from] + other.co[to]) % 3
        }
        val tp = cp
        val tor = co
        cp = newCp
        co = newCo
        newCp = tp
        newCo = tor
    }

    fun edgeMultiply(other: CubieCube) {
        for (to in 0 until 12) {
            val from = other.ep[to]
            newEp[to] = ep[from]
            newEo[to] = (eo[from] + other.eo[to]) % 2
        }
        val tp = ep
        val tor = eo
        ep = newEp
        eo = newEo
        newEp = tp
        newEo = tor
    }

    // ---- Coordinates ----

    /** The twist of the 8 corners, 0 <= twist < 3^7. */
    var twist: Int
        get() {
            var v = 0
            for (i in 0 until 7) v = 3 * v + co[i]
            return v
        }
        set(value) {
            var t = value
            var parity = 0
            for (i in 6 downTo 0) {
                val ori = t % 3
                t /= 3
                co[i] = ori
                parity += ori
            }
            co[7] = (3 - parity % 3) % 3
        }

    /** The flip of the 12 edges, 0 <= flip < 2^11. */
    var flip: Int
        get() {
            var v = 0
            for (i in 0 until 11) v = 2 * v + eo[i]
            return v
        }
        set(value) {
            var f = value
            var parity = 0
            for (i in 10 downTo 0) {
                val ori = f % 2
                f /= 2
                eo[i] = ori
                parity += ori
            }
            eo[11] = (2 - parity % 2) % 2
        }

    /** Parity of the corner permutation. */
    val cornerParity: Int
        get() {
            var s = 0
            for (i in DRB downTo URF + 1) {
                for (j in i - 1 downTo URF) if (cp[j] > cp[i]) s++
            }
            return s % 2
        }

    var urfToDlf: Int
        get() = urfToDlfIndex.get(this)
        set(i) = urfToDlfIndex.set(this, i)
    var urToUl: Int
        get() = urToUlIndex.get(this)
        set(i) = urToUlIndex.set(this, i)
    var ubToDf: Int
        get() = ubToDfIndex.get(this)
        set(i) = ubToDfIndex.set(this, i)
    var urToDf: Int
        get() = urToDfIndex.get(this)
        set(i) = urToDfIndex.set(this, i)
    var frToBr: Int
        get() = frToBrIndex.get(this)
        set(i) = frToBrIndex.set(this, i)

    companion object {
        /** Reads a facelet string with standard centres (as validated by `validateCube`). */
        fun fromString(str: String): CubieCube {
            val cube = CubieCube()
            for (i in 0 until 8) {
                var ori = 0
                while (ori < 3) {
                    val ref = str[cornerFacelet[i][ori]]
                    if (ref == 'U' || ref == 'D') break
                    ori++
                }
                val col1 = str[cornerFacelet[i][(ori + 1) % 3]]
                val col2 = str[cornerFacelet[i][(ori + 2) % 3]]
                for (j in 0 until 8) {
                    if (col1 == cornerColor[j][1] && col2 == cornerColor[j][2]) {
                        cube.cp[i] = j
                        cube.co[i] = ori % 3
                    }
                }
            }
            for (i in 0 until 12) {
                for (j in 0 until 12) {
                    val a = str[edgeFacelet[i][0]]
                    val b = str[edgeFacelet[i][1]]
                    if (a == edgeColor[j][0] && b == edgeColor[j][1]) {
                        cube.ep[i] = j
                        cube.eo[i] = 0
                        break
                    }
                    if (a == edgeColor[j][1] && b == edgeColor[j][0]) {
                        cube.ep[i] = j
                        cube.eo[i] = 1
                        break
                    }
                }
            }
            return cube
        }
    }
}

fun cnk(n: Int, k0: Int): Int {
    if (n < k0) return 0
    var k = k0
    if (k > n / 2.0) k = n - k
    var s = 1
    var i = n
    var j = 1
    while (i != n - k) {
        s = s * i / j
        i--
        j++
    }
    return s
}

private fun factorial(n: Int): Int {
    var f = 1
    for (i in 2..n) f *= i
    return f
}

private fun rotateLeft(a: IntArray, l: Int, r: Int) {
    val tmp = a[l]
    for (i in l until r) a[i] = a[i + 1]
    a[r] = tmp
}

private fun rotateRight(a: IntArray, l: Int, r: Int) {
    val tmp = a[r]
    for (i in r downTo l + 1) a[i] = a[i - 1]
    a[l] = tmp
}

/**
 * A coordinate encoding the positions (combination A) and order (permutation B) of the
 * cubies start..end; the index is A * maxB + B. Not thread-safe (shared scratch buffer):
 * only the solver thread uses it.
 */
private class PermutationIndex(val corners: Boolean, val start: Int, val end: Int, val fromEnd: Boolean = false) {
    val maxOur = end - start
    val maxB = factorial(end - start + 1)
    val maxAll = if (corners) 7 else 11
    private val our = IntArray(end - start + 1)

    fun set(cube: CubieCube, index: Int) {
        for (i in 0..maxOur) our[i] = i + start
        var b = index % maxB // permutation
        var a = index / maxB // combination
        val perm = if (corners) cube.cp else cube.ep
        for (i in 0..maxAll) perm[i] = -1
        for (j in 1..maxOur) {
            var k = b % (j + 1)
            b /= (j + 1)
            while (k > 0) {
                rotateRight(our, 0, j)
                k--
            }
        }
        var x = maxOur
        if (fromEnd) {
            for (j in 0..maxAll) {
                val c = cnk(maxAll - j, x + 1)
                if (a - c >= 0) {
                    perm[j] = our[maxOur - x]
                    a -= c
                    x--
                }
            }
        } else {
            for (j in maxAll downTo 0) {
                val c = cnk(j, x + 1)
                if (a - c >= 0) {
                    perm[j] = our[x]
                    a -= c
                    x--
                }
            }
        }
    }

    fun get(cube: CubieCube): Int {
        val perm = if (corners) cube.cp else cube.ep
        for (i in 0..maxOur) our[i] = -1
        var a = 0
        var b = 0
        var x = 0
        if (fromEnd) {
            for (j in maxAll downTo 0) {
                val p = perm[j]
                if (p in start..end) {
                    a += cnk(maxAll - j, x + 1)
                    our[maxOur - x] = p
                    x++
                }
            }
        } else {
            for (j in 0..maxAll) {
                val p = perm[j]
                if (p in start..end) {
                    a += cnk(j, x + 1)
                    our[x] = p
                    x++
                }
            }
        }
        for (j in maxOur downTo 0) {
            var k = 0
            while (our[j] != start + j) {
                rotateLeft(our, 0, j)
                k++
            }
            b = (j + 1) * b + k
        }
        return a * maxB + b
    }
}

private val urfToDlfIndex = PermutationIndex(true, URF, DLF)
private val urToUlIndex = PermutationIndex(false, UR, UL)
private val ubToDfIndex = PermutationIndex(false, UB, DF)
private val urToDfIndex = PermutationIndex(false, UR, DF)
private val frToBrIndex = PermutationIndex(false, FR, BR, fromEnd = true)

/** The six face turns U, R, F, D, L, B as cubie permutations. */
val faceMoves = listOf(
    // U
    CubieCube(
        intArrayOf(3, 0, 1, 2, 4, 5, 6, 7), intArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(3, 0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11), intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    ),
    // R
    CubieCube(
        intArrayOf(4, 1, 2, 0, 7, 5, 6, 3), intArrayOf(2, 0, 0, 1, 1, 0, 0, 2),
        intArrayOf(8, 1, 2, 3, 11, 5, 6, 7, 4, 9, 10, 0), intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    ),
    // F
    CubieCube(
        intArrayOf(1, 5, 2, 3, 0, 4, 6, 7), intArrayOf(1, 2, 0, 0, 2, 1, 0, 0),
        intArrayOf(0, 9, 2, 3, 4, 8, 6, 7, 1, 5, 10, 11), intArrayOf(0, 1, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0),
    ),
    // D
    CubieCube(
        intArrayOf(0, 1, 2, 3, 5, 6, 7, 4), intArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 1, 2, 3, 5, 6, 7, 4, 8, 9, 10, 11), intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    ),
    // L
    CubieCube(
        intArrayOf(0, 2, 6, 3, 4, 1, 5, 7), intArrayOf(0, 1, 2, 0, 0, 2, 1, 0),
        intArrayOf(0, 1, 10, 3, 4, 5, 9, 7, 8, 2, 6, 11), intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    ),
    // B
    CubieCube(
        intArrayOf(0, 1, 3, 7, 4, 5, 2, 6), intArrayOf(0, 0, 1, 2, 0, 0, 2, 1),
        intArrayOf(0, 1, 2, 11, 4, 5, 6, 10, 8, 9, 3, 7), intArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 1),
    ),
)
