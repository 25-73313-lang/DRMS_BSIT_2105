package com.example.deliveryroutesystem

class NearestFirstSequencer : StopSequencer() {

    override fun sequence(
        cost: Array<DoubleArray>,
        urgentFlags: List<Boolean>,
        startIndex: Int,
        endIndex: Int
    ): List<Int> {
        val allIndices = validIndices(cost.size, startIndex, endIndex)
        val (urgentList, normalList) = partitionByUrgent(allIndices, urgentFlags)

        val urgentIndices = urgentList.toMutableList()
        val normalIndices = normalList.toMutableList()

        val result = mutableListOf<Int>()
        var current = startIndex

        while (urgentIndices.isNotEmpty()) {
            val next = urgentIndices.minByOrNull { cost[current][it] }!!
            result.add(next)
            urgentIndices.remove(next)
            current = next
        }

        while (normalIndices.isNotEmpty()) {
            val next = normalIndices.minByOrNull { cost[current][it] }!!
            result.add(next)
            normalIndices.remove(next)
            current = next
        }

        return result
    }
}
