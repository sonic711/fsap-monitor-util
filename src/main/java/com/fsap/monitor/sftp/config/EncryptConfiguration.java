package com.fsap.monitor.sftp.config;

import java.util.Arrays;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

@Order(Ordered.HIGHEST_PRECEDENCE)
@Configuration
public class EncryptConfiguration {
	public static final String ENCRYPTOR_BEAN = "encryptorBean";
	private static final StringEncryptor ENCRYPTOR;
	static {
		char[] p1Chars = new char[] { 'f', 's', 'a', 'p', 'a', 'd', 'm' };
		try{
			System.getenv("ENCRYPTION_PASSWORD");
			PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
			SimpleStringPBEConfig config = new SimpleStringPBEConfig();
			config.setPasswordCharArray(p1Chars);
			config.setAlgorithm("PBEWithMD5AndDES");
			config.setKeyObtentionIterations("1000");
			config.setPoolSize("1");
			config.setProviderName("SunJCE");
			config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
			config.setStringOutputType("base64");
			encryptor.setConfig(config);
			ENCRYPTOR = encryptor;
		} finally {
			// 清除密碼陣列，減少記憶體洩漏風險
			Arrays.fill(p1Chars, '\0');
		}
	}

	@Bean(name = ENCRYPTOR_BEAN)
	StringEncryptor stringEncryptor() {
		return ENCRYPTOR;
	}

	public static String decrypt(String input) {
		if (null == ENCRYPTOR || !StringUtils.hasText(input)) {
			return input;
		}
		try {
			return ENCRYPTOR.decrypt(input);
		} catch (RuntimeException exception) {
			return input;
		}
	}

	public static String encrypt(String input) {
		if (null == ENCRYPTOR || !StringUtils.hasText(input)) {
			return input;
		}
		return ENCRYPTOR.encrypt(input);
	}

}
