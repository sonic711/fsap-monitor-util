package com.fsap.monitor.cli;

import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.stereotype.Component;

import picocli.CommandLine;

@Component
public class PicocliSpringFactory implements CommandLine.IFactory {

    private final AutowireCapableBeanFactory beanFactory;

    public PicocliSpringFactory(AutowireCapableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public <K> K create(Class<K> clazz) throws Exception {
        return beanFactory.createBean(clazz);
    }
}
