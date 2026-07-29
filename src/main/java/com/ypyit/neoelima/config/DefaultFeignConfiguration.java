package com.ypyit.neoelima.config;

import feign.Contract;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.form.FormEncoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DefaultFeignConfiguration {
    
    @Bean
    public Encoder getEncoder(ObjectFactory<HttpMessageConverters> converter) {
        return new FormEncoder(new SpringEncoder(converter));
    }

    @Bean
    public Decoder getDecoder(ObjectFactory<HttpMessageConverters> converter, ObjectProvider<HttpMessageConverterCustomizer> customizers) {
        return new SpringDecoder(converter, customizers);
    }

    @Bean
    public Contract getContract() {
        return new SpringMvcContract();
    }
}