package com.fsap.monitor.cli;

import org.springframework.stereotype.Component;

import com.fsap.monitor.cli.command.DoctorCommand;
import com.fsap.monitor.cli.command.GenerateReportCommand;
import com.fsap.monitor.cli.command.IngestCommand;
import com.fsap.monitor.cli.command.ServeCommand;
import com.fsap.monitor.cli.command.SyncViewsCommand;
import com.fsap.monitor.cli.command.UpdateMonitorDataCommand;

import picocli.CommandLine.Command;

@Component
/**
 * Picocli 根指令，負責收攏所有操作型子指令。
 */
@Command(
        name = "fsap-monitor-util",
        mixinStandardHelpOptions = true,
        description = "FSAP monitor utility CLI",
        subcommands = {
                DoctorCommand.class,
                IngestCommand.class,
                SyncViewsCommand.class,
                GenerateReportCommand.class,
                UpdateMonitorDataCommand.class,
                ServeCommand.class
        }
)
public class FsapCli implements Runnable {

    @Override
    public void run() {
        System.out.println("Use --help to list available commands.");
    }
}
