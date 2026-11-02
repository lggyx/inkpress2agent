package com.inkpress.service;

import com.inkpress.common.PageResult;
import com.inkpress.entity.OperationLog;

public interface OperationLogService {

    /** 异步记录操作日志 */
    void record(OperationLog record);

    PageResult<OperationLog> page(long pageNum, long pageSize);
}
