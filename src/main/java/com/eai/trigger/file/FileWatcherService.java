package com.eai.trigger.file;

import com.eai.domain.TriggerType;
import com.eai.execution.ExecutionEngine;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FileWatcherService {

    private static final Logger log = LoggerFactory.getLogger(FileWatcherService.class);

    private final ExecutionEngine engine;
    private final long debounceMs;
    private final Map<String, Path> registered = new ConcurrentHashMap<>();
    private final Map<Path, String> watchedDirs = new ConcurrentHashMap<>();
    private WatchService watcher;
    private Thread loopThread;
    private volatile boolean running;

    public FileWatcherService(ExecutionEngine engine,
                              @Value("${eai.file-watcher.debounce-ms:500}") long debounceMs) {
        this.engine = engine;
        this.debounceMs = debounceMs;
    }

    @PostConstruct
    public void start() throws IOException {
        watcher = FileSystems.getDefault().newWatchService();
        running = true;
        loopThread = new Thread(this::loop, "eai-file-watcher");
        loopThread.setDaemon(true);
        loopThread.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (loopThread != null) loopThread.interrupt();
        if (watcher != null) try { watcher.close(); } catch (IOException ignored) {}
    }

    public void register(String interfaceId, String dirPath) {
        Path dir = Path.of(dirPath).toAbsolutePath();
        try {
            Files.createDirectories(dir);
            dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
            registered.put(interfaceId, dir);
            watchedDirs.put(dir, interfaceId);
            log.info("Watching {} for interface {}", dir, interfaceId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to register file watcher: " + e.getMessage(), e);
        }
    }

    public void unregister(String interfaceId) {
        Path dir = registered.remove(interfaceId);
        if (dir != null) watchedDirs.remove(dir);
    }

    private void loop() {
        Map<Path, Long> debounce = new HashMap<>();
        while (running) {
            WatchKey key;
            try { key = watcher.take(); }
            catch (InterruptedException e) { return; }
            catch (ClosedWatchServiceException e) { return; }

            Path dir = (Path) key.watchable();
            String interfaceId = watchedDirs.get(dir);
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() != StandardWatchEventKinds.ENTRY_CREATE) continue;
                if (interfaceId == null) continue;
                Path filename = (Path) event.context();
                Path full = dir.resolve(filename);
                long now = System.currentTimeMillis();
                Long last = debounce.get(full);
                if (last != null && now - last < debounceMs) continue;
                debounce.put(full, now);
                try {
                    engine.start(interfaceId, TriggerType.FILE, Map.of("filePath", full.toString()));
                } catch (Exception e) {
                    log.error("File trigger failed for {}: {}", full, e.getMessage(), e);
                }
            }
            if (!key.reset()) watchedDirs.remove(dir);
        }
    }
}
