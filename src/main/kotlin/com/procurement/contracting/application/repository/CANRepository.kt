package com.procurement.contracting.application.repository

import com.procurement.contracting.domain.entity.CANEntity
import com.procurement.contracting.domain.model.can.CANId
import com.procurement.contracting.domain.model.can.status.CANStatus
import com.procurement.contracting.domain.model.can.status.CANStatusDetails
import java.util.*

interface CANRepository {
    fun findBy(cpid: String, canId: CANId): CANEntity?
    fun findBy(cpid: String): List<CANEntity>

    fun saveNewCAN(cpid: String, entity: CANEntity)

    fun saveCancelledCANs(cpid: String, dataCancelledCAN: DataCancelCAN, dataRelatedCANs: List<DataRelatedCAN>)

    fun updateStatusesCANs(cpid: String, cans: List<DataStatusesCAN>)
}

data class DataCancelCAN(
    val id: CANId,
    val status: CANStatus,
    val statusDetails: CANStatusDetails,
    val jsonData: String
)

data class DataRelatedCAN(
    val id: CANId,
    val status: CANStatus,
    val statusDetails: CANStatusDetails,
    val jsonData: String
)

data class DataStatusesCAN(
    val id: CANId,
    val status: CANStatus,
    val statusDetails: CANStatusDetails,
    val jsonData: String
)