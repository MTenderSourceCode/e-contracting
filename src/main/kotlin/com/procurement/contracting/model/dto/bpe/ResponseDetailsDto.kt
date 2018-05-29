package com.procurement.contracting.model.dto.bpe

import com.fasterxml.jackson.annotation.JsonProperty

data class ResponseDetailsDto(

        @JsonProperty("code")
        val code: String,

        @JsonProperty("message")
        val message: String
)
