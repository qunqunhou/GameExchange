package com.game.simulator;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebListener
public class SimulatorListener implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(SimulatorListener.class.getName());
    private static final String SYSTEM_PROPERTY_NAME = "simulator.enabled";
    private static final String ENVIRONMENT_VARIABLE_NAME = "SIMULATOR_ENABLED";
    private GameSimulator simulator;

    // Tomcat启动时自动调用
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        if (!isSimulatorEnabled()) {
            LOGGER.info("游戏模拟器未启用，跳过初始化");
            return;
        }

        LOGGER.info("Tomcat 已启动，正在初始化游戏模拟器");
        simulator = new GameSimulator();
        simulator.start();
        // 把simulator存入application作用域，方便后续扩展
        sce.getServletContext().setAttribute("simulator", simulator);
    }

    // Tomcat关闭时自动调用
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (simulator == null) return;

        LOGGER.info("Tomcat 正在关闭，准备停止游戏模拟器");
        simulator.stop();
        sce.getServletContext().removeAttribute("simulator");
        simulator = null;
    }

    private boolean isSimulatorEnabled() {
        String configuredValue = System.getProperty(SYSTEM_PROPERTY_NAME);
        if (configuredValue == null || configuredValue.isBlank()) {
            configuredValue = System.getenv(ENVIRONMENT_VARIABLE_NAME);
        }

        if (configuredValue == null || configuredValue.isBlank()) return false;
        if ("true".equalsIgnoreCase(configuredValue.trim())) return true;
        if ("false".equalsIgnoreCase(configuredValue.trim())) return false;

        LOGGER.log(Level.WARNING,
                "模拟器开关配置无效，仅接受 true 或 false，已按 false 处理");
        return false;
    }
}
