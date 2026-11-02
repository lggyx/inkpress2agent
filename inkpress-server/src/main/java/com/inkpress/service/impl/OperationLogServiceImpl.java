package com.inkpress.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inkpress.common.PageResult;
import com.inkpress.entity.OperationLog;
import com.inkpress.mapper.OperationLogMapper;
import com.inkpress.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    @Async("taskExecutor")
    @Override
    public void record(OperationLog record) {
        operationLogMapper.insert(record);
    }

    @Override
    public PageResult<OperationLog> page(long pageNum, long pageSize) {
        Page<OperationLog> page = operationLogMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OperationLog>().orderByDesc(OperationLog::getCreatedAt));
        return PageResult.of(page, record -> record);
    }
}
