package com.fsap.monitor.cli;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.stereotype.Component;

import com.fsap.monitor.FsapApplication;

import picocli.CommandLine;

@Component
@ConditionalOnNotWebApplication
/**
 * 在非 Web 模式下，負責把 Spring Boot 啟動流程接到 Picocli 指令執行。
 */
public class PicocliRunner implements ApplicationRunner, ExitCodeGenerator {

    private final FsapCli fsapCli;
    private final PicocliSpringFactory picocliSpringFactory;
    private int exitCode;

    public PicocliRunner(FsapCli fsapCli, PicocliSpringFactory picocliSpringFactory) {
        this.fsapCli = fsapCli;
        this.picocliSpringFactory = picocliSpringFactory;
    }

    @Override
    public void run(ApplicationArguments args) {
        CommandLine commandLine = new CommandLine(fsapCli, picocliSpringFactory);
        commandLine.setExecutionExceptionHandler((exception, cmd, parseResult) -> {
            String message = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
            cmd.getErr().println("ERROR: " + message);
            return cmd.getCommandSpec().exitCodeOnExecutionException();
        });
        exitCode = commandLine.execute(FsapApplication.cliArgs());
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
