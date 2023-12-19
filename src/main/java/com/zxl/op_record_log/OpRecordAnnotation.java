package com.zxl.op_record_log;


import java.lang.annotation.*;

/**
 * 自定义注解:操作记录.
 *   注意： 该注解只能适用于controller层的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface OpRecordAnnotation {


    /**
     *  敏感信息的符号,默认为"****"
     */
    String symbol () default "****";

    /**
     *  reqDesensitizationLevel缩写为reqDtLevel
     *
     * 请求参数脱敏等级设置:
     */
    DesensitizationLevel reqDtLevel () default DesensitizationLevel.NO ;

    /**
     * 请求参数中需要进行脱敏的字段名集合
     *  支持的表达式: ["a","b.c","c[2]","d[3].d","f[1-3,<5,<=10, ..., *]"]
     * 含义解释:
     * 	"a" : 对字段a的值进行脱敏
     * 	"b.c" : 对字段b嵌套拥有的字段c进行脱敏，
     *
     */
    String[] reqDtFields () default {};


    /**
     *  respDesensitizationLevel缩写为respDtLevel
     *
     * 响应报文脱敏等级设置:
     */
    DesensitizationLevel respDtLevel () default DesensitizationLevel.NO ;


    /**
     *  响应报文脱敏字段设置
     *   支持的表达式: ["a","b.c","d[1,2,3-5,<10,>=11]","e[2].f"]
     * 含义解释:
     * 	"a" : 对字段a的值进行脱敏
     * 	"b.c" : 对字段b嵌套拥有的字段c进行脱敏，
     * 	"d[1,2,3-5,<10,>=11]" : 首先字段d是个数组、或者list , 然后对字段d的下标为1、2、3到5、小于10、大于等于11的元素进行脱敏处理
     * 	"e[2].f" :  对字段e的下标为2的元素的f字段进行脱敏
     */
    String[] respDtFields () default  {};


}
