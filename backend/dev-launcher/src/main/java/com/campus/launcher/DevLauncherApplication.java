package com.campus.launcher;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DevLauncherApplication {

    private static final String BACKEND_ARTIFACT_ID = "campus-platform";
    private static final Set<String> EXCLUDED_MODULES = Set.of("common-core", "dev-launcher");
    private static final long SERVICE_START_DELAY_MS = 1500L;
    private static final long DISCOVERY_BOOTSTRAP_DELAY_MS = 4000L;
    private static final String SHARED_MODULE = "common-core";
    private static final int PORT_CHECK_TIMEOUT_MS = 300;
    private static final long PROCESS_STOP_WAIT_MS = 1200L;
    private static final long PORT_RELEASE_WAIT_MS = 1000L;
    private static final int REDIS_PORT = 6379;
    private static final long REDIS_BOOTSTRAP_DELAY_MS = 1200L;
    private static final String DEFAULT_WINDOWS_REDIS_HOME = "D:\\code\\Redis-x64-3.0.504";
    private static final String REDIS_HOME_ENV = "REDIS_HOME";
    private static final String REDIS_SERVER_EXECUTABLE = "redis-server.exe";
    private static final String REDIS_WINDOWS_CONF = "redis.windows.conf";

    public static void main(String[] args) throws Exception {
        if (containsFlag(args, "--help")) {
            printHelp();
            return;
        }

        Path backendDir = locateBackendDir(Path.of("").toAbsolutePath().normalize());
        Set<String> includes = parseCsvArg(args, "--modules=");
        Set<String> excludes = parseCsvArg(args, "--skip-modules=");
        boolean dryRun = containsFlag(args, "--dry-run");
        boolean allowPortInUse = containsFlag(args, "--allow-port-in-use");
        boolean autoCleanupPorts = !containsFlag(args, "--no-auto-cleanup-ports");
        boolean autoStartRedis = !containsFlag(args, "--no-auto-start-redis");
        String redisHomeArg = parseStringArg(args, "--redis-home=");

        List<String> springBootModules = discoverSpringBootModules(backendDir, includes, excludes);
        if (springBootModules.isEmpty()) {
            System.err.println("No Spring Boot modules found to start.");
            return;
        }

        System.out.printf("[%s] Backend dir: %s%n", now(), backendDir);
        System.out.printf("[%s] Modules to start: %s%n", now(), String.join(", ", springBootModules));
        System.out.printf("[%s] Port cleanup strategy: %s%n",
                now(),
                allowPortInUse
                        ? "allow occupied ports and skip module"
                        : (autoCleanupPorts ? "auto cleanup occupied ports" : "fail if occupied"));
        System.out.printf("[%s] Redis startup strategy: %s%n",
                now(),
                autoStartRedis ? "auto start when port 6379 is unavailable" : "disabled");

        if (dryRun) {
            System.out.printf("[%s] Dry-run mode enabled, no service started.%n", now());
            return;
        }

        if (springBootModules.stream()
                .anyMatch(module -> dependsOnModule(backendDir.resolve(module).resolve("pom.xml"), SHARED_MODULE))) {
            prepareSharedModule(backendDir);
        }

        RedisLaunchContext redisContext = autoStartRedis
                ? ensureRedisRunning(redisHomeArg)
                : RedisLaunchContext.notStarted();

        Map<String, Process> processes = new LinkedHashMap<>();
        RedisLaunchContext finalRedisContext = redisContext;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopAll(processes);
            stopManagedRedis(finalRedisContext);
            if (autoCleanupPorts && !allowPortInUse) {
                cleanupModulePorts(backendDir, springBootModules);
            }
        }, "dev-launcher-shutdown"));

        for (String module : springBootModules) {
            Integer modulePort = resolveModulePort(backendDir.resolve(module));
            if (modulePort != null && isPortOpen(modulePort)) {
                String message = String.format("[%s] Module %s port %d is already in use.",
                        now(), module, modulePort);
                if (allowPortInUse) {
                    System.out.printf("%s Skip launch for this module.%n", message);
                    continue;
                }
                if (autoCleanupPorts && tryCleanupPort(modulePort, module)) {
                    System.out.printf("[%s] Port %d released for module %s.%n", now(), modulePort, module);
                } else {
                    throw new IllegalStateException(
                            message + " Auto cleanup failed. "
                                    + "Check permissions or conflicting processes. "
                                    + "You can use --allow-port-in-use to keep old skip behavior.");
                }
            }

            Process process = startModuleProcess(backendDir, module);
            processes.put(module, process);
            if ("service-discovery".equals(module)) {
                sleepSilently(DISCOVERY_BOOTSTRAP_DELAY_MS);
            } else {
                sleepSilently(SERVICE_START_DELAY_MS);
            }
        }

        System.out.printf("[%s] All modules started. Press Ctrl+C to stop all.%n", now());
        monitorProcesses(processes);
    }

    private static List<String> discoverSpringBootModules(Path backendDir, Set<String> includes, Set<String> excludes)
            throws Exception {
        List<String> modules = readModulesFromParentPom(backendDir.resolve("pom.xml"));
        Set<String> allowedModules = includes.isEmpty() ? Set.of() : new HashSet<>(includes);

        List<String> candidates = modules.stream()
                .filter(module -> !EXCLUDED_MODULES.contains(module))
                .filter(module -> includes.isEmpty() || allowedModules.contains(module))
                .filter(module -> !excludes.contains(module))
                .collect(Collectors.toCollection(ArrayList::new));

        List<String> springBootModules = candidates.stream()
                .filter(module -> hasSpringBootEntry(backendDir.resolve(module)))
                .collect(Collectors.toCollection(ArrayList::new));

        Map<String, Integer> orderIndex = new LinkedHashMap<>();
        for (int i = 0; i < springBootModules.size(); i++) {
            orderIndex.put(springBootModules.get(i), i);
        }

        springBootModules.sort(
                Comparator.comparingInt((String module) -> modulePriority(module))
                        .thenComparingInt(orderIndex::get));

        return springBootModules;
    }

    private static Integer resolveModulePort(Path moduleDir) {
        Path applicationYml = moduleDir.resolve("src").resolve("main").resolve("resources").resolve("application.yml");
        if (!Files.exists(applicationYml)) {
            return null;
        }

        try {
            List<String> lines = Files.readAllLines(applicationYml);

            for (String line : lines) {
                String trimmed = stripYamlComment(line).trim();
                if (trimmed.startsWith("server.port:")) {
                    return parsePortValue(trimmed);
                }
            }

            for (int i = 0; i < lines.size(); i++) {
                String line = stripYamlComment(lines.get(i));
                String trimmed = line.trim();
                if (!"server:".equals(trimmed)) {
                    continue;
                }
                int serverIndent = countLeadingSpaces(line);
                for (int j = i + 1; j < lines.size(); j++) {
                    String childLine = stripYamlComment(lines.get(j));
                    if (childLine.trim().isEmpty()) {
                        continue;
                    }
                    int childIndent = countLeadingSpaces(childLine);
                    if (childIndent <= serverIndent) {
                        break;
                    }
                    String childTrimmed = childLine.trim();
                    if (childTrimmed.startsWith("port:")) {
                        return parsePortValue(childTrimmed);
                    }
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }

    private static String stripYamlComment(String line) {
        String normalized = line == null ? "" : line.replace("\uFEFF", "");
        int commentIndex = normalized.indexOf('#');
        if (commentIndex >= 0) {
            return normalized.substring(0, commentIndex);
        }
        return normalized;
    }

    private static int countLeadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && Character.isWhitespace(line.charAt(count))) {
            count++;
        }
        return count;
    }

    private static Integer parsePortValue(String portLine) {
        int splitIndex = portLine.indexOf(':');
        if (splitIndex < 0 || splitIndex >= portLine.length() - 1) {
            return null;
        }
        String value = portLine.substring(splitIndex + 1).trim();
        if (value.isEmpty()) {
            return null;
        }
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return null;
        }
        try {
            return Integer.parseInt(value.substring(0, end));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isPortOpen(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), PORT_CHECK_TIMEOUT_MS);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void cleanupModulePorts(Path backendDir, List<String> modules) {
        for (String module : modules) {
            Integer port = resolveModulePort(backendDir.resolve(module));
            if (port == null || !isPortOpen(port)) {
                continue;
            }
            tryCleanupPort(port, module);
        }
    }

    private static boolean tryCleanupPort(int port, String module) {
        Set<Long> pids = findListeningPids(port);
        long currentPid = ProcessHandle.current().pid();

        if (pids.isEmpty()) {
            sleepSilently(PORT_RELEASE_WAIT_MS);
            return !isPortOpen(port);
        }

        System.out.printf("[%s] Attempting to release port %d for module %s, found pid(s): %s%n",
                now(), port, module, pids);
        for (Long pid : pids) {
            if (pid == null || pid <= 0 || pid == currentPid) {
                continue;
            }
            terminateProcess(pid);
        }

        sleepSilently(PORT_RELEASE_WAIT_MS);
        return !isPortOpen(port);
    }

    private static Set<Long> findListeningPids(int port) {
        if (isWindows()) {
            return findListeningPidsOnWindows(port);
        }
        return findListeningPidsOnUnix(port);
    }

    private static Set<Long> findListeningPidsOnWindows(int port) {
        Set<Long> pids = new LinkedHashSet<>();
        CommandResult result = runCommand(List.of("cmd", "/c", "netstat -ano -p tcp"));
        if (result.exitCode != 0) {
            return pids;
        }

        String suffix = ":" + port;
        for (String rawLine : result.output.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || !line.contains(suffix) || !line.toUpperCase().contains("LISTENING")) {
                continue;
            }
            String[] columns = line.split("\\s+");
            if (columns.length < 5) {
                continue;
            }
            String localAddress = columns[1];
            String state = columns[3];
            if (!localAddress.endsWith(suffix) || !"LISTENING".equalsIgnoreCase(state)) {
                continue;
            }
            Long pid = tryParseLong(columns[columns.length - 1]);
            if (pid != null) {
                pids.add(pid);
            }
        }
        return pids;
    }

    private static Set<Long> findListeningPidsOnUnix(int port) {
        Set<Long> pids = new LinkedHashSet<>();
        CommandResult result = runCommand(List.of("sh", "-c", "lsof -ti tcp:" + port + " -sTCP:LISTEN"));
        if (result.exitCode != 0) {
            return pids;
        }
        for (String line : result.output.split("\\R")) {
            Long pid = tryParseLong(line.trim());
            if (pid != null) {
                pids.add(pid);
            }
        }
        return pids;
    }

    private static boolean terminateProcess(long pid) {
        ProcessHandle handle = ProcessHandle.of(pid).orElse(null);
        if (handle != null && handle.isAlive()) {
            handle.destroy();
            if (waitForExit(handle, PROCESS_STOP_WAIT_MS)) {
                return true;
            }
            handle.destroyForcibly();
            if (waitForExit(handle, PROCESS_STOP_WAIT_MS)) {
                return true;
            }
        }

        if (isWindows()) {
            runCommand(List.of("cmd", "/c", "taskkill /PID " + pid + " /T /F"));
            return !isPidAlive(pid);
        }
        runCommand(List.of("sh", "-c", "kill -9 " + pid));
        return !isPidAlive(pid);
    }

    private static boolean waitForExit(ProcessHandle handle, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (handle.isAlive() && System.currentTimeMillis() - start < timeoutMs) {
            sleepSilently(100L);
        }
        return !handle.isAlive();
    }

    private static boolean isPidAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static Long tryParseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static CommandResult runCommand(List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        StringBuilder output = new StringBuilder();
        try {
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }
            int exitCode = process.waitFor();
            return new CommandResult(exitCode, output.toString());
        } catch (Exception ex) {
            return new CommandResult(-1, "");
        }
    }

    private static class CommandResult {
        private final int exitCode;
        private final String output;

        private CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
        }
    }

    private static class RedisLaunchContext {
        private final boolean startedByLauncher;
        private final Process process;

        private RedisLaunchContext(boolean startedByLauncher, Process process) {
            this.startedByLauncher = startedByLauncher;
            this.process = process;
        }

        private static RedisLaunchContext notStarted() {
            return new RedisLaunchContext(false, null);
        }

        private static RedisLaunchContext started(Process process) {
            return new RedisLaunchContext(true, process);
        }

        private boolean startedByLauncher() {
            return startedByLauncher;
        }

        private Process process() {
            return process;
        }
    }

    private static int modulePriority(String module) {
        if ("service-discovery".equals(module)) {
            return 0;
        }
        if ("api-gateway".equals(module)) {
            return 100;
        }
        return 50;
    }

    private static Process startModuleProcess(Path backendDir, String module) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(resolveMavenCommand(backendDir));
        command.add("-f");
        command.add(module + "/pom.xml");
        command.add("-Dspring-boot.run.fork=false");
        command.add("spring-boot:run");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(backendDir.toFile());
        builder.redirectErrorStream(true);

        System.out.printf("[%s] Starting module: %s%n", now(), module);
        Process process = builder.start();
        forwardLogs(module, process.getInputStream());
        return process;
    }

    private static RedisLaunchContext ensureRedisRunning(String redisHomeArg) {
        if (isPortOpen(REDIS_PORT)) {
            System.out.printf("[%s] Redis detected on port %d, skip auto start.%n", now(), REDIS_PORT);
            return RedisLaunchContext.notStarted();
        }

        if (!isWindows()) {
            System.err.printf("[%s] Redis port %d is closed. Auto start is only implemented for Windows now.%n",
                    now(), REDIS_PORT);
            return RedisLaunchContext.notStarted();
        }

        Path redisHome = resolveRedisHome(redisHomeArg);
        if (redisHome == null) {
            System.err.printf("[%s] Redis port %d is closed but redis home was not found. Use --redis-home=<path> or set %s.%n",
                    now(), REDIS_PORT, REDIS_HOME_ENV);
            return RedisLaunchContext.notStarted();
        }

        Path redisServer = redisHome.resolve(REDIS_SERVER_EXECUTABLE);
        if (!Files.exists(redisServer)) {
            System.err.printf("[%s] Redis executable not found: %s%n", now(), redisServer);
            return RedisLaunchContext.notStarted();
        }

        List<String> command = new ArrayList<>();
        command.add(redisServer.toAbsolutePath().toString());
        Path redisConf = redisHome.resolve(REDIS_WINDOWS_CONF);
        if (Files.exists(redisConf)) {
            command.add(redisConf.toAbsolutePath().toString());
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(redisHome.toFile());
        builder.redirectErrorStream(true);
        try {
            System.out.printf("[%s] Redis port %d is closed, auto starting from %s%n", now(), REDIS_PORT, redisHome);
            Process process = builder.start();
            forwardLogs("redis", process.getInputStream());
            sleepSilently(REDIS_BOOTSTRAP_DELAY_MS);
            if (isPortOpen(REDIS_PORT)) {
                System.out.printf("[%s] Redis started by launcher on port %d.%n", now(), REDIS_PORT);
                return RedisLaunchContext.started(process);
            }
            System.err.printf("[%s] Redis process started but port %d is still unavailable.%n", now(), REDIS_PORT);
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            return RedisLaunchContext.notStarted();
        } catch (IOException ex) {
            System.err.printf("[%s] Failed to auto start Redis: %s%n", now(), ex.getMessage());
            return RedisLaunchContext.notStarted();
        }
    }

    private static Path resolveRedisHome(String redisHomeArg) {
        List<Path> candidates = new ArrayList<>();
        if (redisHomeArg != null && !redisHomeArg.isBlank()) {
            candidates.add(Path.of(redisHomeArg.trim()));
        }
        String redisHomeEnv = System.getenv(REDIS_HOME_ENV);
        if (redisHomeEnv != null && !redisHomeEnv.isBlank()) {
            candidates.add(Path.of(redisHomeEnv.trim()));
        }
        if (isWindows()) {
            candidates.add(Path.of(DEFAULT_WINDOWS_REDIS_HOME));
        }

        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private static void stopManagedRedis(RedisLaunchContext redisContext) {
        if (redisContext == null || !redisContext.startedByLauncher() || redisContext.process() == null) {
            return;
        }
        Process process = redisContext.process();
        if (!process.isAlive()) {
            return;
        }
        System.out.printf("[%s] Stopping Redis started by launcher.%n", now());
        process.destroy();
        sleepSilently(PROCESS_STOP_WAIT_MS);
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private static void prepareSharedModule(Path backendDir) throws IOException, InterruptedException {
        Path sharedPom = backendDir.resolve(SHARED_MODULE).resolve("pom.xml");
        if (!Files.exists(sharedPom)) {
            return;
        }

        List<String> command = new ArrayList<>();
        command.add(resolveMavenCommand(backendDir));
        command.add("-pl");
        command.add(SHARED_MODULE);
        command.add("-am");
        command.add("-DskipTests");
        command.add("install");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(backendDir.toFile());
        builder.redirectErrorStream(true);

        System.out.printf("[%s] Preparing shared module: %s%n", now(), SHARED_MODULE);
        Process process = builder.start();
        forwardLogs("bootstrap", process.getInputStream());
        int exit = process.waitFor();
        if (exit != 0) {
            System.err.printf("[%s] Failed to prepare shared module: %s (exitCode=%d), continue startup.%n", now(),
                    SHARED_MODULE, exit);
            return;
        }
        System.out.printf("[%s] Shared module prepared: %s%n", now(), SHARED_MODULE);
    }

    private static boolean dependsOnModule(Path modulePom, String dependencyArtifactId) {
        if (!Files.exists(modulePom)) {
            return false;
        }
        try {
            String pomText = Files.readString(modulePom);
            return pomText.contains("<artifactId>" + dependencyArtifactId + "</artifactId>");
        } catch (IOException e) {
            return false;
        }
    }

    private static void monitorProcesses(Map<String, Process> processes) {
        while (true) {
            List<String> exitedModules = new ArrayList<>();

            for (Map.Entry<String, Process> entry : processes.entrySet()) {
                String module = entry.getKey();
                Process process = entry.getValue();
                if (!process.isAlive()) {
                    int exitCode = process.exitValue();
                    exitedModules.add(module);
                    if (exitCode != 0) {
                        System.err.printf("[%s] Module %s exited with code %d, stopping all.%n", now(), module,
                                exitCode);
                        stopAll(processes);
                        throw new IllegalStateException("Module startup failed: " + module + " exitCode=" + exitCode);
                    } else {
                        System.out.printf("[%s] Module %s exited normally.%n", now(), module);
                    }
                }
            }

            for (String module : exitedModules) {
                processes.remove(module);
            }

            if (processes.isEmpty()) {
                System.out.printf("[%s] All module processes exited.%n", now());
                return;
            }

            sleepSilently(2000L);
        }
    }

    private static void stopAll(Map<String, Process> processes) {
        for (Map.Entry<String, Process> entry : processes.entrySet()) {
            Process process = entry.getValue();
            if (process.isAlive()) {
                System.out.printf("[%s] Stopping module: %s%n", now(), entry.getKey());
                process.destroy();
            }
        }

        sleepSilently(5000L);

        for (Map.Entry<String, Process> entry : processes.entrySet()) {
            Process process = entry.getValue();
            if (process.isAlive()) {
                System.out.printf("[%s] Force killing module: %s%n", now(), entry.getKey());
                process.destroyForcibly();
            }
        }
    }

    private static void forwardLogs(String module, InputStream inputStream) {
        Thread logThread = new Thread(() -> {
            Charset charset = Charset.defaultCharset();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.printf("[%s] [%s] %s%n", now(), module, line);
                }
            } catch (IOException e) {
                System.err.printf("[%s] [%s] log stream closed: %s%n", now(), module, e.getMessage());
            }
        }, "log-forwarder-" + module);
        logThread.setDaemon(true);
        logThread.start();
    }

    private static String resolveMavenCommand(Path backendDir) {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path wrapper = backendDir.resolve(windows ? "mvnw.cmd" : "mvnw");
        if (Files.exists(wrapper)) {
            return wrapper.toAbsolutePath().toString();
        }
        return windows ? "mvn.cmd" : "mvn";
    }

    private static List<String> readModulesFromParentPom(Path pomPath) throws Exception {
        Document document = newDocumentBuilderFactory().newDocumentBuilder().parse(pomPath.toFile());

        NodeList moduleNodes = document.getElementsByTagName("module");
        List<String> modules = new ArrayList<>();
        for (int i = 0; i < moduleNodes.getLength(); i++) {
            Node node = moduleNodes.item(i);
            String module = node.getTextContent();
            if (module != null && !module.isBlank()) {
                modules.add(module.trim());
            }
        }
        return modules;
    }

    private static boolean hasSpringBootEntry(Path moduleDir) {
        Path javaSourceDir = moduleDir.resolve("src").resolve("main").resolve("java");
        if (!Files.isDirectory(javaSourceDir)) {
            return false;
        }

        try (Stream<Path> pathStream = Files.walk(javaSourceDir)) {
            return pathStream
                    .filter(path -> path.toString().endsWith(".java"))
                    .anyMatch(DevLauncherApplication::containsSpringBootApplicationAnnotation);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean containsSpringBootApplicationAnnotation(Path javaFile) {
        try (Stream<String> lines = Files.lines(javaFile)) {
            return lines.anyMatch(line -> line.contains("@SpringBootApplication"));
        } catch (IOException e) {
            return false;
        }
    }

    private static Path locateBackendDir(Path start) {
        Path current = start;
        while (current != null) {
            Path directPom = current.resolve("pom.xml");
            if (looksLikeBackendPom(directPom)) {
                return current;
            }

            Path nestedBackendPom = current.resolve("backend").resolve("pom.xml");
            if (looksLikeBackendPom(nestedBackendPom)) {
                return current.resolve("backend");
            }

            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate backend directory containing campus-platform pom.xml");
    }

    private static boolean looksLikeBackendPom(Path pomPath) {
        if (!Files.exists(pomPath)) {
            return false;
        }
        try {
            Document document = newDocumentBuilderFactory().newDocumentBuilder().parse(pomPath.toFile());
            NodeList artifactIdNodes = document.getDocumentElement().getElementsByTagName("artifactId");
            for (int i = 0; i < artifactIdNodes.getLength(); i++) {
                Node node = artifactIdNodes.item(i);
                if (node.getParentNode() != document.getDocumentElement()) {
                    continue;
                }
                String value = node.getTextContent();
                if (BACKEND_ARTIFACT_ID.equals(value == null ? null : value.trim())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static DocumentBuilderFactory newDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory;
    }

    private static boolean containsFlag(String[] args, String flag) {
        return Arrays.stream(args).anyMatch(flag::equalsIgnoreCase);
    }

    private static Set<String> parseCsvArg(String[] args, String prefix) {
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                String raw = arg.substring(prefix.length()).trim();
                if (raw.isEmpty()) {
                    return Set.of();
                }
                return Arrays.stream(raw.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .collect(Collectors.toCollection(HashSet::new));
            }
        }
        return Set.of();
    }

    private static String parseStringArg(String[] args, String prefix) {
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private static void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    private static void printHelp() {
        System.out.println("Dev Launcher for campus backend microservices");
        System.out.println("Args:");
        System.out.println("  --modules=a,b        Start only listed modules");
        System.out.println("  --skip-modules=a,b   Start all except listed modules");
        System.out.println("  --allow-port-in-use  Keep old behavior: skip modules whose ports are already occupied");
        System.out.println("  --no-auto-cleanup-ports  Do not auto kill processes that occupy service ports");
        System.out.println("  --redis-home=<path>  Redis home dir, default: " + DEFAULT_WINDOWS_REDIS_HOME);
        System.out.println("  --no-auto-start-redis  Disable auto start Redis when port 6379 is closed");
        System.out.println("  --dry-run            Print startup plan only");
        System.out.println("  --help               Show this help");
    }
}
