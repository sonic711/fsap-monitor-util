package com.fsap.monitor.cli.command;

import org.springframework.stereotype.Component;

import com.fsap.monitor.core.service.EnvironmentCheckService;
import com.fsap.monitor.core.service.EnvironmentCheckService.CheckResult;

import picocli.CommandLine.Command;

@Component
@Command(name = "doctor", mixinStandardHelpOptions = true, description = "Run environment health checks")
public class DoctorCommand implements Runnable {

    private final EnvironmentCheckService environmentCheckService;

    public DoctorCommand(EnvironmentCheckService environmentCheckService) {
        this.environmentCheckService = environmentCheckService;
    }

    @Override
    public void run() {
        var report = environmentCheckService.runChecks();
        for (CheckResult check : report.checks()) {
            System.out.printf("[%s] %s -> %s (%s)%n",
                    check.ok() ? "OK" : "FAIL",
                    check.name(),
                    check.target(),
                    check.message());
        }
        if (!report.healthy()) {
            throw new IllegalStateException("Environment checks reported failures");
        }
    }
}
