package com.zxl.op_record_log;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class DesensitizationExp {

    private String exp;

    private Map<String,DesensitizationField> fieldMap;
}
