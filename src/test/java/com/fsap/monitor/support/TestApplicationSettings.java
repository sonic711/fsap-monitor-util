package com.fsap.monitor.support;

import java.io.IOException;
import java.util.ArrayList;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import com.fsap.monitor.infra.config.FsapProperties;

public final class TestApplicationSettings {

    private TestApplicationSettings() { }

    public static FsapProperties load() throws IOException {
        var loader = new YamlPropertySourceLoader();
        var sources = new ArrayList<PropertySource<?>>();
        sources.addAll(loader.load("external", new FileSystemResource("config/application.yml")));
        sources.addAll(loader.load("internal", new ClassPathResource("application.yml")));
        return new Binder(ConfigurationPropertySources.from(sources))
                .bind("fsap", Bindable.of(FsapProperties.class)).get();
    }
}
