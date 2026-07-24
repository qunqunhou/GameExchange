package com.game.listener;

import com.game.util.DBUtil;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class DatabaseLifecycleListener implements ServletContextListener {

    private boolean initialized;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // 让连接池与 Web 应用共享完整生命周期，并在数据库不可用时尽早失败。
        DBUtil.getDataSource();
        initialized = true;
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (initialized) {
            DBUtil.shutdown();
            initialized = false;
        }
    }
}
