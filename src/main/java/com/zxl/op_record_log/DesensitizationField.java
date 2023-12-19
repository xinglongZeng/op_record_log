package com.zxl.op_record_log;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 需要进行脱敏的字段
 */
@Data
@NoArgsConstructor
public class DesensitizationField {

    /**
     * 字段名
     */
    private String keyName;

    /**
     * 嵌套的字段
     */
    private Map<String,DesensitizationField> fieldMap;

    /**
     * 类型 ,默认值为SIMPLE
     */
    private DesensitizationFieldEnum type = DesensitizationFieldEnum.SIMPLE;

    /**
     * type字段为ARRAY时才有值，表示"["与"]"之间的那些表达式
     */
    private List<ArrayExpValue> arrayExps;


    public static DesensitizationField getArrayEntity(String keyName,String[] arrayExpStr){
        DesensitizationField field = new DesensitizationField();
        field.setKeyName(keyName);
        field.setType(DesensitizationFieldEnum.ARRAY);
        List<ArrayExpValue> expValues= Arrays.stream(arrayExpStr)
                .map(ArrayExpValue::initByExpStr)
                .collect(Collectors.toList());
        field.setArrayExps(expValues);
        return field;
    }




    public enum DesensitizationFieldEnum{
        // 简单
        SIMPLE,

        // 数组或List
        ARRAY,

    }

}
