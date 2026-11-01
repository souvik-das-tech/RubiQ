// Move and pruning tables for the two-phase (Kociemba) search, ported from `cubejs`
// (lib/solve.js). Tables are flat: a move table entry is `table[coord * 18 + move]`, and a
// pruning table packs eight 4-bit distances per 32-bit word.
package com.bbcl.rubiq

import java.nio.ByteBuffer
import java.nio.ByteOrder

const val N_TWIST = 2187 // 3^7 corner orientations
const val N_FLIP = 2048 // 2^11 possible edge flips
const val N_FR_TO_BR = 11880 // 12!/(12-4)! permutations of FR..BR edges
const val N_SLICE1 = 495 // (12 choose 4) possible positions of FR..BR edges
const val N_SLICE2 = 24 // 4! permutations of FR..BR edges in phase 2
const val N_URF_TO_DLF = 20160 // 8!/(8-6)! permutations of URF..DLF corners
const val N_UR_TO_DF = 20160 // 8!/(8-6)! permutations of UR..DF edges in phase 2
const val N_UR_TO_UL = 1320 // 12!/(12-3)! permutations of UR..UL edges
const val N_UB_TO_DF = 1320 // 12!/(12-3)! permutations of UB..DF edges
const val N_MERGE = 336 // URtoUL / UBtoDF values that occur at the start of phase 2

/** Phase 2 moves: all U and D turns plus half turns of the other faces. */
val phase2Moves = intArrayOf(0, 1, 2, 4, 7, 9, 10, 11, 13, 16)

private val parityMove = intArrayOf(1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1)

/** Distance lookup in a packed pruning table (words are unsigned 32-bit values). */
fun prune(table: IntArray, index: Int): Int = (table[index shr 3] ushr ((index and 7) shl 2)) and 0xf

/** Corner permutation parity after `move` (the cubejs `parity` move table). */
fun parityAfter(parity: Int, move: Int): Int = if (parity == 0) parityMove[move] else 1 - parityMove[move]

private fun words(size: Int) = (size + 7) / 8

class KociembaTables private constructor(
    val twist: ShortArray,
    val flip: ShortArray,
    val frToBr: ShortArray,
    val urfToDlf: ShortArray,
    /** Int32: values outside phase 2 go beyond the Int16 range. */
    val urToDf: IntArray,
    val urToUl: ShortArray,
    val ubToDf: ShortArray,
    /** `mergeUrToDf[urToUl * 336 + ubToDf]`, -1 when the two edge sets collide. */
    val mergeUrToDf: ShortArray,
    val sliceTwist: IntArray,
    val sliceFlip: IntArray,
    val sliceUrfToDlfParity: IntArray,
    val sliceUrToDfParity: IntArray,
) {
    companion object {
        /** Builds every table from scratch. */
        fun compute(): KociembaTables {
            val twist = moveTable(true, N_TWIST, { c, i -> c.twist = i }, { it.twist }).toShorts()
            val flip = moveTable(false, N_FLIP, { c, i -> c.flip = i }, { it.flip }).toShorts()
            val frToBr = moveTable(false, N_FR_TO_BR, { c, i -> c.frToBr = i }, { it.frToBr }).toShorts()
            val urfToDlf = moveTable(true, N_URF_TO_DLF, { c, i -> c.urfToDlf = i }, { it.urfToDlf }).toShorts()
            val urToDf = moveTable(false, N_UR_TO_DF, { c, i -> c.urToDf = i }, { it.urToDf })
            val urToUl = moveTable(false, N_UR_TO_UL, { c, i -> c.urToUl = i }, { it.urToUl }).toShorts()
            val ubToDf = moveTable(false, N_UB_TO_DF, { c, i -> c.ubToDf = i }, { it.ubToDf }).toShorts()
            val merge = mergeTable()

            val sliceTwist = pruningTable(1, N_SLICE1 * N_TWIST) { index, move ->
                val slice = index % N_SLICE1
                val tw = index / N_SLICE1
                twist[tw * 18 + move] * N_SLICE1 + frToBr[slice * 24 * 18 + move] / 24
            }
            val sliceFlip = pruningTable(1, N_SLICE1 * N_FLIP) { index, move ->
                val slice = index % N_SLICE1
                val fl = index / N_SLICE1
                flip[fl * 18 + move] * N_SLICE1 + frToBr[slice * 24 * 18 + move] / 24
            }
            fun phase2Next(table: (Int) -> Int, index: Int, move: Int): Int {
                val parity = index % 2
                val slice = (index / 2) % N_SLICE2
                val coord = (index / 2) / N_SLICE2
                return (table(coord * 18 + move) * N_SLICE2 + frToBr[slice * 18 + move]) * 2 + parityAfter(parity, move)
            }

            val sliceUrfToDlfParity = pruningTable(2, N_SLICE2 * N_URF_TO_DLF * 2) { index, move ->
                phase2Next({ urfToDlf[it].toInt() }, index, move)
            }
            val sliceUrToDfParity = pruningTable(2, N_SLICE2 * N_UR_TO_DF * 2) { index, move ->
                phase2Next({ urToDf[it] }, index, move)
            }

            return KociembaTables(
                twist, flip, frToBr, urfToDlf, urToDf, urToUl, ubToDf, merge,
                sliceTwist, sliceFlip, sliceUrfToDlfParity, sliceUrToDfParity,
            )
        }

        /** Reads the bundled asset: all tables concatenated, little-endian, in a fixed order. */
        fun fromBytes(bytes: ByteArray): KociembaTables {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            fun i16(length: Int) = ShortArray(length).also { buf.asShortBuffer().get(it); buf.position(buf.position() + 2 * length) }
            fun i32(length: Int) = IntArray(length).also { buf.asIntBuffer().get(it); buf.position(buf.position() + 4 * length) }

            val t = KociembaTables(
                i16(N_TWIST * 18),
                i16(N_FLIP * 18),
                i16(N_FR_TO_BR * 18),
                i16(N_URF_TO_DLF * 18),
                i32(N_UR_TO_DF * 18),
                i16(N_UR_TO_UL * 18),
                i16(N_UB_TO_DF * 18),
                i16(N_MERGE * N_MERGE),
                i32(words(N_SLICE1 * N_TWIST)),
                i32(words(N_SLICE1 * N_FLIP)),
                i32(words(N_SLICE2 * N_URF_TO_DLF * 2)),
                i32(words(N_SLICE2 * N_UR_TO_DF * 2)),
            )
            if (buf.position() != bytes.size) throw IllegalArgumentException("Kociemba table data has the wrong size")
            return t
        }
    }
}

private fun IntArray.toShorts() = ShortArray(size) { this[it].toShort() }

/** For every coordinate value, applies each of the 18 moves and records the new value. */
private fun moveTable(corners: Boolean, size: Int, set: (CubieCube, Int) -> Unit, get: (CubieCube) -> Int): IntArray {
    val cube = CubieCube()
    val table = IntArray(size * 18)
    for (i in 0 until size) {
        set(cube, i)
        for (face in 0 until 6) {
            val move = faceMoves[face]
            for (power in 0 until 3) {
                if (corners) cube.cornerMultiply(move) else cube.edgeMultiply(move)
                table[i * 18 + face * 3 + power] = get(cube)
            }
            // A 4th face turn restores the cube.
            if (corners) cube.cornerMultiply(move) else cube.edgeMultiply(move)
        }
    }
    return table
}

/** Merges the URtoUL and UBtoDF coordinates into URtoDF at the start of phase 2. */
private fun mergeTable(): ShortArray {
    val a = CubieCube()
    val b = CubieCube()
    val table = ShortArray(N_MERGE * N_MERGE)
    for (x in 0 until N_MERGE) {
        for (y in 0 until N_MERGE) {
            a.urToUl = x
            b.ubToDf = y
            var collision = false
            for (i in 0 until 8) {
                if (a.ep[i] != -1) {
                    if (b.ep[i] != -1) {
                        collision = true
                        break
                    }
                    b.ep[i] = a.ep[i]
                }
            }
            table[x * N_MERGE + y] = (if (collision) -1 else b.urToDf).toShort()
        }
    }
    return table
}

/** Breadth-first fill of a pruning table: the distance of every index from index 0. */
private fun pruningTable(phase: Int, size: Int, next: (index: Int, move: Int) -> Int): IntArray {
    val table = IntArray(words(size)) { -1 }
    fun setValue(index: Int, value: Int) {
        val slot = index shr 3
        val shift = (index and 7) shl 2
        table[slot] = (table[slot] and (0xF shl shift).inv()) or (value shl shift)
    }

    val moves = if (phase == 1) IntArray(18) { it } else phase2Moves
    var depth = 0
    setValue(0, 0)
    var done = 1
    while (done != size) {
        for (index in 0 until size) {
            if (prune(table, index) != depth) continue
            for (move in moves) {
                val n = next(index, move)
                if (prune(table, n) == 0xF) {
                    setValue(n, depth + 1)
                    done++
                }
            }
        }
        depth++
    }
    return table
}
