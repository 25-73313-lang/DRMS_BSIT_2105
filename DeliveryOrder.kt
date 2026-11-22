package com.example.deliveryroutesystem

enum class OrderStatus {
    PENDING,
    IN_TRANSIT,
    DELIVERED
}

data class DeliveryOrder(
    val id: String,
    val customerName: String,
    val barangay: String,
    val addressDetails: String,
    val latitude: Double,
    val longitude: Double,
    var status: OrderStatus = OrderStatus.PENDING,
    var sequenceNumber: Int = 0,
    var riderId: Int = 0,
    var etaMinutes: Double = 0.0,
    val cashOnDelivery: Double = 0.0,
    val isUrgent: Boolean = false,
    val isLate: Boolean = false
)
