package com.zxl.op_record_log;

import lombok.Data;

/**
 * 操作日志处理配置类
 */
@Data
public class OpRecordProcessConfig {

    /**
     * 是否开启持久化
     */
    private boolean persistenceFlg;



    /**
     * 是否打印日志
     */
    private boolean logFlg;


}
