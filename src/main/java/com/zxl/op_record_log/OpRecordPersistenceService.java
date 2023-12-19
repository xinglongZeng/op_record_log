package com.zxl.op_record_log;


/**
 * 系统操作日志持久化服务接口
 */
public interface OpRecordPersistenceService {

    /**
     * 持久化日志数据
     */
    void persistence(OpRecordModelDto data);
}
