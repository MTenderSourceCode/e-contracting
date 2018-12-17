package com.procurement.contracting.model.dto.ocds

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Can @JsonCreator constructor(

        val contract: CanContract
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CanContract @JsonCreator constructor(

        val id: String,

        var date: LocalDateTime?,

        val awardId: String,

        var status: ContractStatus,

        var statusDetails: ContractStatusDetails,

        var documents: List<DocumentContract>?,

        var amendment: Amendment
)
