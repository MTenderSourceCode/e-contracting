package com.procurement.contracting.model.dto.ocds

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.procurement.contracting.infrastructure.bind.MoneyDeserializer
import java.math.BigDecimal

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ValueTax @JsonCreator constructor(

        @JsonDeserialize(using = MoneyDeserializer::class)
        val amount: BigDecimal?,

        val currency: String?,

        @JsonDeserialize(using = MoneyDeserializer::class)
        val amountNet: BigDecimal? = null,

        val valueAddedTaxIncluded: Boolean? = null
)