package com.fsap.monitor.cli.command;

import org.springframework.stereotype.Component;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
/**
 * 提供給使用者看的 Web 模式指令佔位說明。
 *
 * <p>真正的 Web 啟動邏輯放在 {@link com.fsap.monitor.FsapApplication}，
 * 因為 Spring Boot 必須先接管整個應用程式啟動流程，Picocli 才能接續運作。
 */
@Command(name = "serve", mixinStandardHelpOptions = true, description = "Start the Spring Boot web server")
public class ServeCommand implements Runnable {

    @Option(names = "--port", description = "HTTP port")
    Integer port;

    @Option(names = "--host", description = "Bind host / address")
    String host;

    @Option(names = "--readonly", description = "Start web UI in readonly mode")
    boolean readonly;

    @Option(names = "--writable", description = "Enable task actions from the web UI")
    boolean writable;

    @Override
    public void run() {
        System.out.println("Use `java -jar fsap-monitor-util.jar serve [--port 8080] [--host 127.0.0.1] [--readonly|--writable]` to launch web mode.");
    }
}
