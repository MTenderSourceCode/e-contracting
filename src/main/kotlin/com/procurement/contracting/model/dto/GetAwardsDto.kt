package com.procurement.contracting.model.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.procurement.contracting.domain.model.award.AwardId

data class GetAwardsRq @JsonCreator constructor(

    val contracts: List<ContractGetAwards>
)

data class ContractGetAwards @JsonCreator constructor(

    var id: String
)

data class GetAwardsRs @JsonCreator constructor(

    val cans: List<CanGetAwards>
)

data class CanGetAwards @JsonCreator constructor(

    val id: String,

    val awardId: AwardId
)
