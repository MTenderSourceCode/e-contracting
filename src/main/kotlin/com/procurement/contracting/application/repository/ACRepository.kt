package com.procurement.contracting.application.repository

import com.procurement.contracting.domain.entity.ACEntity
import com.procurement.contracting.domain.model.contract.status.ContractStatus
import com.procurement.contracting.domain.model.contract.status.ContractStatusDetails

interface ACRepository {
    fun findBy(cpid: String, contractId: String): ACEntity?

    fun saveNew(entity: ACEntity)

    fun saveCancelledAC(
        cpid: String,
        id: String,
        status: ContractStatus,
        statusDetails: ContractStatusDetails,
        jsonData: String
    )

    fun updateStatusesAC(
        cpid: String,
        id: String,
        status: ContractStatus,
        statusDetails: ContractStatusDetails,
        jsonData: String
    )
}
