package com.joker;


public class SnowflakeIdWorker {
    // 开始时间戳 2022-01-01
    private final long startEpoch = 1640966400000L;

    // 机器id所占位数
    private final long workerIdBits = 5L;

    // 数据标识id所占位数
    private final long datacenterIdBits = 5L;

    // 支持的最大机器id 2^5 - 1
    private final long maxWorkerId = ~(-1L << workerIdBits);

    // 支持的最大数据标识id 2^5 - 1
    private final long maxDatacenterId = ~(-1 << datacenterIdBits);

    // 序列在id中所占位数
    private final long sequenceBits = 12L;

    // 机器id向左移动12位
    private final long workerIdShift = sequenceBits;

    // 数据标识id向左移动17（5+12）位
    private final long datacenterIdShift = sequenceBits + workerIdBits;

    // 时间戳向左移动22（5+5+12）位
    private final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;

    // 生成序列的掩码 2^{12} - 1 = 4095
    private final long sequenceMask = ~(-1 << sequenceBits);

    // 工作机器id
    private long workerId;

    // 数据中心id
    private long datacenterId;

    // 每毫秒中的序列 (0 - 4095)
    private long sequence = 0L;

    // 上次生成id的时间戳
    private long lastTimestamp = -1L;

    public SnowflakeIdWorker(long workerId, long datacenterId) {
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException(String.format("worker Id can't be greater than %d or less than 0", maxWorkerId));
        }
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("datacenter Id can't be greater than %d or less than 0", maxDatacenterId));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    // 获取下一个id
    public synchronized long nextId() {
        long timestamp = getTimestamp();
        // 如果当前时间小于上一次ID生成的时间戳，说明系统时钟回退过，
        // 抛异常
        if (timestamp < lastTimestamp) {
            throw new RuntimeException(
                    String.format("Clock moved backwards.  Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
        }
        // 如果是同一ms生成的，返回当前ms的下一序列
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & sequenceMask;
            // 溢出
            if (sequence == 0) {
                // 阻塞到下一ms
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else { // 时间戳变了，重置序列
            sequence = 0L;
        }
        // 更新上一次的时间戳
        lastTimestamp = timestamp;

        // 拼接为64位的id
        return ((timestamp - startEpoch) << timestampLeftShift)
                | (datacenterId << datacenterIdShift)
                | (workerId << workerIdShift)
                | sequence;
    }

    // 获取当前时间戳(ms)
    private long getTimestamp() {
        return System.currentTimeMillis();
    }

    // 阻塞到下一ms，知道获取新的时间戳
    private long tilNextMillis(long lastTimeStamp) {
        long timestamp = getTimestamp();
        while (timestamp <= lastTimeStamp) {
            timestamp = getTimestamp();
        }
        return timestamp;
    }

    public static void main(String[] args) {
        SnowflakeIdWorker idWorker = new SnowflakeIdWorker(0, 0);
        for (int i = 0; i < (int) 1e5; i++) {
            long id = idWorker.nextId();
            System.out.println(Long.toBinaryString(id) + " -> " + id);
        }
    }
}
