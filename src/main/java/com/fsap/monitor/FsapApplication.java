package com.fsap.monitor;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FsapApplication {

    private static final List<String> ROOT_CLI_OPTIONS = List.of("-h", "--help", "-V", "--version");
    private static String[] cliArgs = new String[0];

    static {
        // Trust project-provided Excel files and disable POI zip bomb inflate ratio checks.
        ZipSecureFile.setMinInflateRatio(0.0d);
    }

    public static void main(String[] args) {
        ParsedArguments parsedArguments = splitArguments(args);

        if (isServeMode(parsedArguments.commandArgs())) {
            String[] serverArgs = merge(parsedArguments.springArgs(),
                    translateServeArgs(Arrays.copyOfRange(parsedArguments.commandArgs(), 1, parsedArguments.commandArgs().length)));
            SpringApplication application = new SpringApplication(FsapApplication.class);
            application.setWebApplicationType(WebApplicationType.SERVLET);
            application.run(serverArgs);
            return;
        }

        cliArgs = parsedArguments.commandArgs();
        SpringApplication application = new SpringApplication(FsapApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        int exitCode = SpringApplication.exit(application.run(parsedArguments.springArgs()));
        System.exit(exitCode);
    }

    public static String[] cliArgs() {
        return Arrays.copyOf(cliArgs, cliArgs.length);
    }

    private static boolean isServeMode(String[] args) {
        return args.length > 0 && "serve".equalsIgnoreCase(args[0]);
    }

    private static String[] translateServeArgs(String[] args) {
        List<String> translated = new java.util.ArrayList<>();
        for (int index = 0; index < args.length; index++) {
            String current = args[index];
            if ("--port".equals(current) && index + 1 < args.length) {
                translated.add("--server.port=" + args[++index]);
                continue;
            }
            if ("--host".equals(current) && index + 1 < args.length) {
                translated.add("--server.address=" + args[++index]);
                continue;
            }
            if ("--readonly".equals(current)) {
                translated.add("--fsap.web.readonly=true");
                continue;
            }
            if ("--writable".equals(current)) {
                translated.add("--fsap.web.readonly=false");
                continue;
            }
            translated.add(current);
        }
        return translated.toArray(String[]::new);
    }

    private static ParsedArguments splitArguments(String[] args) {
        List<String> springArgs = new java.util.ArrayList<>();
        List<String> commandArgs = new java.util.ArrayList<>();
        boolean commandMode = false;

        for (String arg : args) {
            if (!commandMode && isSpringPropertyArgument(arg)) {
                springArgs.add(arg);
                continue;
            }
            commandMode = true;
            commandArgs.add(arg);
        }

        return new ParsedArguments(springArgs.toArray(String[]::new), commandArgs.toArray(String[]::new));
    }

    private static boolean isSpringPropertyArgument(String arg) {
        if (arg == null || arg.isBlank()) {
            return false;
        }
        if (ROOT_CLI_OPTIONS.contains(arg)) {
            return false;
        }
        if (!arg.startsWith("--")) {
            return false;
        }
        String normalized = arg.toLowerCase(Locale.ROOT);
        return normalized.startsWith("--fsap.")
                || normalized.startsWith("--spring.")
                || normalized.startsWith("--server.")
                || normalized.startsWith("--logging.")
                || normalized.startsWith("--management.");
    }

    private static String[] merge(String[] left, String[] right) {
        String[] merged = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, merged, left.length, right.length);
        return merged;
    }

    private record ParsedArguments(String[] springArgs, String[] commandArgs) { }
}
