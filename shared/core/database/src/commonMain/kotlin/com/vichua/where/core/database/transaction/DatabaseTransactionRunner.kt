package com.vichua.where.core.database.transaction

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.vichua.where.core.database.WhereDatabase

/**
 * 为跨 DAO 写操作提供统一的 Room KMP 事务边界。
 *
 * 使用立即事务可以在 WAL 模式下尽早获得写锁，同时继续允许其他连接读取一致数据。
 */
class DatabaseTransactionRunner(
    private val database: WhereDatabase,
) {
    /**
     * 在单个立即写事务中执行操作。
     *
     * 任意异常都会回滚当前块内的全部 DAO 写入，调用方不得在事务块中执行文件移动或网络请求。
     *
     * @param block 只包含数据库读写的挂起操作。
     * @return 事务块返回值。
     */
    suspend fun <Result> write(
        block: suspend WhereDatabase.() -> Result,
    ): Result = database.useWriterConnection { transactor ->
        transactor.immediateTransaction {
            database.block()
        }
    }
}
