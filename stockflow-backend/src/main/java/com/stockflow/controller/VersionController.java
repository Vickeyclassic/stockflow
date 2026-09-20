package com.stockflow.controller;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VersionController {
    public record Version(String service, String version, String builtAt) {}
    private final Version version;
    public VersionController(ObjectProvider<BuildProperties> properties) {
        var build = properties.getIfAvailable();
        version = new Version("stockflow-backend", build == null ? "development" : build.getVersion(),
            build == null || build.getTime() == null ? "unknown" : build.getTime().toString());
    }
    @GetMapping("/api/version")
    public ResponseEntity<Version> version() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(version);
    }
}
