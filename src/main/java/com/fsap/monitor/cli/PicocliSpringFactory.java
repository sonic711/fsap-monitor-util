package com.fsap.monitor.cli;

import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.stereotype.Component;

import picocli.CommandLine;

@Component
/**
 * 讓 Picocli 透過 Spring 建立 command 物件，這樣所有 CLI 指令都能使用
 * 建構式注入的 Spring bean。
 */
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
