
package com.procurement.contracting.model.dto.createContract;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.procurement.contracting.databind.LocalDateTimeDeserializer;
import com.procurement.contracting.databind.LocalDateTimeSerializer;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

@Getter
@JsonPropertyOrder({
    "id",
    "date",
    "status",
    "description",
    "relatedLots",
    "value",
    "suppliers"
})
public class Award {
    @JsonProperty("id")
    @NotNull
    private final String id;

    @JsonProperty("date")
    @NotNull
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private final LocalDateTime date;

    @JsonProperty("status")
    @NotNull
    private final Status status;

    @JsonProperty("description")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String description;

    @JsonProperty("relatedLots")
    @NotEmpty
    private final List<String> relatedLots;

    @JsonProperty("value")
    @Valid
    @NotNull
    private final Value value;

    @JsonProperty("suppliers")
    @NotEmpty
    @JsonDeserialize(as = LinkedHashSet.class)
    @Valid
    private final Set<OrganizationReference> suppliers;

    public Award(@JsonProperty("id") @NotNull final String id,
                 @JsonProperty("date") @NotNull @JsonDeserialize(using = LocalDateTimeDeserializer.class) final LocalDateTime date,
                 @JsonProperty("status") @NotNull final Status status,
                 @JsonProperty("description") @JsonInclude(JsonInclude.Include.NON_NULL) final String description,
                 @JsonProperty("relatedLots") @NotEmpty final List<String> relatedLots,
                 @JsonProperty("value") @Valid @NotNull final Value value,
                 @JsonProperty("suppliers") @NotEmpty @Valid final Set<OrganizationReference> suppliers) {
        this.id = id;
        this.date = date;
        this.status = status;
        this.description = description;
        this.relatedLots = relatedLots;
        this.value = value;
        this.suppliers = suppliers;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(id)
                                    .append(description)
                                    .append(status)
                                    .append(date)
                                    .append(value)
                                    .append(suppliers)
                                    .append(relatedLots)
                                    .toHashCode();
    }

    @Override
    public boolean equals(final Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof Award)) {
            return false;
        }
        final Award rhs = (Award) other;
        return new EqualsBuilder().append(id, rhs.id)
                                  .append(description, rhs.description)
                                  .append(status, rhs.status)
                                  .append(date, rhs.date)
                                  .append(value, rhs.value)
                                  .append(suppliers, rhs.suppliers)
                                  .append(relatedLots, rhs.relatedLots)
                                  .isEquals();
    }

    public enum Status {
        PENDING("pending"),
        ACTIVE("active"),
        CANCELLED("cancelled"),
        UNSUCCESSFUL("unsuccessful");

        private final String value;
        private final static Map<String, Status> CONSTANTS = new HashMap<>();

        static {
            for (final Status c : values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Status(final String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static Status fromValue(final String value) {
            final Status constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            }
            return constant;
        }

    }
}
