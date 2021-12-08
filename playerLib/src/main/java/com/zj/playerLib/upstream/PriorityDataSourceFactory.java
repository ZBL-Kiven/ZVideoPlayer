package com.zj.playerLib.upstream;

import com.zj.playerLib.upstream.DataSource.Factory;
import com.zj.playerLib.util.PriorityTaskManager;

public final class PriorityDataSourceFactory implements Factory {
    private final Factory upstreamFactory;
    private final PriorityTaskManager priorityTaskManager;
    private final int priority;

    public PriorityDataSourceFactory(Factory upstreamFactory, PriorityTaskManager priorityTaskManager, int priority) {
        this.upstreamFactory = upstreamFactory;
        this.priorityTaskManager = priorityTaskManager;
        this.priority = priority;
    }

    public PriorityDataSource createDataSource() {
        return new PriorityDataSource(this.upstreamFactory.createDataSource(), this.priorityTaskManager, this.priority);
    }
}
