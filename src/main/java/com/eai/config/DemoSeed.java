package com.eai.config;

import com.eai.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("demo")
public class DemoSeed {

    private static final Logger log = LoggerFactory.getLogger(DemoSeed.class);

    CommandLineRunner seed(AdapterConfigRepository adapters,
                           InterfaceDefRepository interfaces,
                           InterfaceVersionRepository versions,
                           ObjectMapper mapper) {
        return args -> {
            if (!adapters.findAll().isEmpty()) {
                log.info("Demo seed: data already present, skipping");
                return;
            }
            AdapterConfigEntity src = new AdapterConfigEntity();
            src.setName("httpbin-json");
            src.setType(AdapterType.REST);
            src.setDirection(AdapterDirection.SOURCE);
            src.setConfigJson(mapper.writeValueAsString(Map.of(
                    "url", "https://httpbin.org/json",
                    "method", "GET",
                    "timeoutSec", 30)));
            src = adapters.save(src);

            AdapterConfigEntity tgt = new AdapterConfigEntity();
            tgt.setName("demo-file");
            tgt.setType(AdapterType.FILE);
            tgt.setDirection(AdapterDirection.TARGET);
            tgt.setConfigJson(mapper.writeValueAsString(Map.of(
                    "path", "./out/demo.json",
                    "format", "json")));
            tgt = adapters.save(tgt);

            InterfaceDef def = new InterfaceDef();
            def.setName("HTTPBin → File (demo)");
            def.setDescription("Fetch httpbin.org/json and write to ./out/demo.json");
            def.setActive(true);
            def = interfaces.save(def);

            InterfaceVersion v = new InterfaceVersion();
            v.setInterfaceId(def.getId());
            v.setVersionNo(1);
            v.setSourceAdapterId(src.getId());
            v.setTargetAdapterId(tgt.getId());
            v.setMappingRulesJson("[{\"source\":\"$.slideshow.title\",\"target\":\"$.title\",\"transform\":\"identity\"}]");
            v.setTriggerConfigJson("{\"type\":\"MANUAL\"}");
            v = versions.save(v);

            def.setCurrentVersionId(v.getId());
            interfaces.save(def);

            log.info("Demo seed: created adapters {} + {} and interface {}", src.getId(), tgt.getId(), def.getId());
        };
    }

    @org.springframework.context.annotation.Bean
    CommandLineRunner demoSeedRunner(AdapterConfigRepository adapters,
                                     InterfaceDefRepository interfaces,
                                     InterfaceVersionRepository versions,
                                     ObjectMapper mapper) {
        return seed(adapters, interfaces, versions, mapper);
    }
}
