package com.procurement.contracting.domain.model.related.process

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.procurement.contracting.domain.model.EnumElementProvider

enum class RelatedProcessType(@JsonValue override val key: String) : EnumElementProvider.Key {
    FRAMEWORK("framework"),
    PLANNING("planning"),
    PARENT("parent"),
    PRIOR("prior"),
    UNSUCCESSFUL_PROCESS("unsuccessfulProcess"),
    SUB_CONTRACT("subContract"),
    REPLACEMENT_PROCESS("replacementProcess"),
    RENEWAL_PROCESS("renewalProcess");

    override fun toString(): String = key

    companion object : EnumElementProvider<RelatedProcessType>(info = info()) {
        @JvmStatic
        @JsonCreator
        fun creator(name: String) = RelatedProcessType.orThrow(name)
    }
}