package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "status",
        visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = JudgedScanReportItemDTO.class, name = "JUDGED"),
    @JsonSubTypes.Type(value = ReusedScanReportItemDTO.class, name = "REUSED"),
    @JsonSubTypes.Type(value = WithheldScanReportItemDTO.class, name = "WITHHELD"),
    @JsonSubTypes.Type(value = ListedScanReportItemDTO.class, name = "LISTED"),
    @JsonSubTypes.Type(value = NotReviewedScanReportItemDTO.class, name = "NOT_REVIEWED")
})
@Schema(
        oneOf = {
            JudgedScanReportItemDTO.class,
            ReusedScanReportItemDTO.class,
            WithheldScanReportItemDTO.class,
            ListedScanReportItemDTO.class,
            NotReviewedScanReportItemDTO.class
        },
        discriminatorProperty = "status",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "JUDGED", schema = JudgedScanReportItemDTO.class),
            @DiscriminatorMapping(value = "REUSED", schema = ReusedScanReportItemDTO.class),
            @DiscriminatorMapping(value = "WITHHELD", schema = WithheldScanReportItemDTO.class),
            @DiscriminatorMapping(value = "LISTED", schema = ListedScanReportItemDTO.class),
            @DiscriminatorMapping(value = "NOT_REVIEWED", schema = NotReviewedScanReportItemDTO.class)
        })
public sealed interface ScanReportItemDTO
        permits JudgedScanReportItemDTO,
                ReusedScanReportItemDTO,
                WithheldScanReportItemDTO,
                ListedScanReportItemDTO,
                NotReviewedScanReportItemDTO {}
