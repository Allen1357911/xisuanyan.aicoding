package org.example.dormrepairsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备类型报修次数统计
 *
 * 用于「按设备类型统计」接口的返回元素：设备类型 + 该类型的工单数量。
 */
@Data
@Schema(name = "DeviceTypeStatDTO", description = "设备类型报修统计")
public class DeviceTypeStatDTO {

    /** 设备类型：如水龙头/电灯/空调/马桶 */
    @Schema(description = "设备类型")
    private String deviceType;

    /** 该设备类型的工单数量（包含已取消的工单） */
    @Schema(description = "报修次数")
    private Long count;
}
