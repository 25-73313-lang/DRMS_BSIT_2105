package com.example.deliveryroutesystem

abstract class StopSequencer {

    protected fun validIndices(size: Int, startIndex: Int, endIndex: Int): List<Int> {
        return (0 until size).filter { it != startIndex && it != endIndex }
    }

    protected fun partitionByUrgent(
        allIndices: List<Int>,
        urgentFlags: List<Boolean>
    ): Pair<List<Int>, List<Int>> {
        val urgent = allIndices.filter { urgentFlags.getOrElse(it) { false } }
        val normal = allIndices.filter { !urgentFlags.getOrElse(it) { false } }
        return Pair(urgent, normal)
    }

    abstract fun sequence(
        cost: Array<DoubleArray>,
        urgentFlags: List<Boolean>,
        startIndex: Int,
        endIndex: Int
    ): List<Int>
}
