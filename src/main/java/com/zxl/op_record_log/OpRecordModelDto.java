package com.zxl.op_record_log;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpRecordModelDto {

    private Long id;

    /**
     * 操作人账号
     */
    private String opAccount;

    /**
     * 请求的功能
     */
    private String reqFunc;

    /**
     * 请求的url
     */
    private String reqUrl;

    /**
     * 请求的方式. POST、GET、PUT...
     */
    private String reqMethod;

    /**
     * 请求的报文(脱敏), json格式的字符串
     */
    private String reqData;

    /**
     * 响应报文(脱敏), json格式的字符串
     */
    private String respData;

    /**
     * 响应状态，是否成功
     */
    private Boolean success;

    /**
     * 操作时间
     */
    private Date opTime;

    /**
     * 执行耗时(毫秒)
     */
    private long elapsedTime;
}
