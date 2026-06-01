package com.fsap.monitor;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 同時支援 CLI 與 Spring Boot Web 模式的統一啟動入口。
 *
 * <p>這裡會先把 Spring 參數與指令參數拆開，讓同一個 jar 可以同時扮演兩種角色：
 * - 第一個指令是 {@code serve} 時，啟動 Web 伺服器
 * - 其他情況則走批次 CLI 模式
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FsapApplication {

    private static final List<String> ROOT_CLI_OPTIONS = List.of("-h", "--help", "-V", "--version");
    private static String[] cliArgs = new String[0];

    static {
        // 此專案預設信任餵入的 Excel，因此關閉 POI 的 zip bomb inflate ratio 檢查。
        ZipSecureFile.setMinInflateRatio(0.0d);
    }

    public static void main(String[] args) {
        ParsedArguments parsedArguments = splitArguments(args);

        if (isServeMode(parsedArguments.commandArgs())) {
            String[] serverArgs = merge(parsedArguments.springArgs(),
                    translateServeArgs(Arrays.copyOfRange(parsedArguments.commandArgs(), 1, parsedArguments.commandArgs().length)));
            SpringApplication application = createApplication(WebApplicationType.SERVLET);
            application.run(serverArgs);
            return;
        }

        cliArgs = parsedArguments.commandArgs();
        SpringApplication application = createApplication(WebApplicationType.NONE);
        int exitCode = SpringApplication.exit(application.run(parsedArguments.springArgs()));
        System.exit(exitCode);
    }

    public static String[] cliArgs() {
        return Arrays.copyOf(cliArgs, cliArgs.length);
    }

    private static boolean isServeMode(String[] args) {
        return args.length > 0 && "serve".equalsIgnoreCase(args[0]);
    }

    private static SpringApplication createApplication(WebApplicationType webApplicationType) {
        SpringApplication application = new SpringApplication(FsapApplication.class);
        application.setWebApplicationType(webApplicationType);
        application.setDefaultProperties(defaultProperties());
        return application;
    }

    private static Map<String, Object> defaultProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.config.additional-location", externalConfigLocations());
        return properties;
    }

    private static String externalConfigLocations() {
        Path applicationHome = applicationHome();
        return String.join(",",
                asConfigDirectory(applicationHome),
                asConfigDirectory(applicationHome.resolve("config"))
        );
    }

    private static Path applicationHome() {
        try {
            Path codeSource = Path.of(FsapApplication.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            if (codeSource.toFile().isFile()) {
                return codeSource.getParent();
            }
            return codeSource;
        } catch (URISyntaxException | RuntimeException exception) {
            return Path.of(System.getProperty("user.dir"));
        }
    }

    private static String asConfigDirectory(Path directory) {
        String uri = directory.toAbsolutePath().normalize().toUri().toString();
        if (!uri.endsWith("/")) {
            uri += "/";
        }
        return "optional:" + uri;
    }

    private static String[] translateServeArgs(String[] args) {
        List<String> translated = new java.util.ArrayList<>();
        for (int index = 0; index < args.length; index++) {
            String current = args[index];
            // 使用者看到的是 Picocli 的 serve 參數，但真正啟動 Web 時仍要轉成
            // Spring Boot 可理解的 server.* / fsap.* 屬性。
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
