package com.ypyit.neoelima.domain.user.service.impl;

import com.ypyit.neoelima.config.DefaultFeignConfiguration;
import com.ypyit.neoelima.domain.establishment.dto.TenantDto;
import com.ypyit.neoelima.domain.user.form.TenantCreationForm;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.UUID;

@FeignClient(
        configuration = DefaultFeignConfiguration.class,
        name = "payment-api",
        url = "${payment-api.base-url}"
)
public interface TenantServiceConnector {

    @PostMapping("/tenants")
    TenantDto createTenant(TenantCreationForm request);

    @PutMapping("/tenants/{companyId}/deactivate")
    void deactivateByCompanyId(@PathVariable UUID companyId);

    @PutMapping("/tenants/{companyId}/activate")
    void activateByCompanyId(@PathVariable UUID companyId);
}