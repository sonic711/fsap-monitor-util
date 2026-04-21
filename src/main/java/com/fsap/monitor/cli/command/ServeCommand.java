package com.fsap.monitor.cli.command;

import org.springframework.stereotype.Component;

import picocli.CommandLine.Command;

@Component
@Command(name = "serve", mixinStandardHelpOptions = true, description = "Start the Spring Boot web server")
public class ServeCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("Use `java -jar fsap-monitor-util.jar serve` to launch web mode.");
    }
}
