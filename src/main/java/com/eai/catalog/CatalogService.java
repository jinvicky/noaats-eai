package com.eai.catalog;

import com.eai.domain.*;
import com.eai.trigger.TriggerRegistrar;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final InterfaceDefRepository interfaces;
    private final InterfaceVersionRepository versions;
    private final AdapterConfigRepository adapters;
    private final RegisteredSystemRepository systems;
    private final ApplicationEventPublisher events;

    public CatalogService(InterfaceDefRepository interfaces,
                          InterfaceVersionRepository versions,
                          AdapterConfigRepository adapters,
                          RegisteredSystemRepository systems,
                          ApplicationEventPublisher events) {
        this.interfaces = interfaces;
        this.versions = versions;
        this.adapters = adapters;
        this.systems = systems;
        this.events = events;
    }

    public List<RegisteredSystem> listSystems() {
        return systems.findAllByOrderByCodeAsc();
    }

    public Optional<RegisteredSystem> findSystem(String id) {
        return systems.findById(id);
    }

    public Optional<RegisteredSystem> findSystemByCode(String code) {
        return systems.findByCode(code);
    }

    @Transactional
    public RegisteredSystem saveSystem(RegisteredSystem s) {
        return systems.save(s);
    }

    @Transactional
    public void deleteSystem(String id) {
        systems.deleteById(id);
    }

    public List<InterfaceDef> listInterfaces() {
        return interfaces.findAllByOrderByCreatedAtDesc();
    }

    public Optional<InterfaceDef> findInterface(String id) {
        return interfaces.findById(id);
    }

    public Optional<InterfaceVersion> latestVersion(String interfaceId) {
        return versions.findTopByInterfaceIdOrderByVersionNoDesc(interfaceId);
    }

    public List<AdapterConfigEntity> listAdapters() {
        return adapters.findAllByOrderByNameAsc();
    }

    public Optional<AdapterConfigEntity> findAdapter(String id) {
        return adapters.findById(id);
    }

    @Transactional
    public AdapterConfigEntity saveAdapter(AdapterConfigEntity a) {
        return adapters.save(a);
    }

    @Transactional
    public void deleteAdapter(String id) {
        adapters.deleteById(id);
    }

    @Transactional
    public InterfaceDef saveInterface(InterfaceDef def, InterfaceVersion newVersion) {
        boolean isNew = def.getId() == null;
        InterfaceDef saved = interfaces.save(def);
        int nextVer = versions.findTopByInterfaceIdOrderByVersionNoDesc(saved.getId())
                .map(v -> v.getVersionNo() + 1).orElse(1);
        newVersion.setInterfaceId(saved.getId());
        newVersion.setVersionNo(nextVer);
        InterfaceVersion v = versions.save(newVersion);
        saved.setCurrentVersionId(v.getId());
        interfaces.save(saved);
        events.publishEvent(new TriggerRegistrar.InterfaceChangedEvent(saved.getId(), saved.isActive()));
        return saved;
    }

    @Transactional
    public void deleteInterface(String id) {
        events.publishEvent(new TriggerRegistrar.InterfaceChangedEvent(id, false));
        versions.findByInterfaceIdOrderByVersionNoDesc(id).forEach(versions::delete);
        interfaces.deleteById(id);
    }
}
