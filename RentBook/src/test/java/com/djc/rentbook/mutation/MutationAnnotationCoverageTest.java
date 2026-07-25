package com.djc.rentbook.mutation;

import com.djc.rentbook.contract.ContractController;
import com.djc.rentbook.payment.PaymentController;
import com.djc.rentbook.property.PropertyController;
import com.djc.rentbook.tenant.TenantController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MutationAnnotationCoverageTest {
    @Test
    void everyUserMutationEndpointIsAuditedAndIdempotent() {
        List<Class<?>> controllers = List.of(
                PropertyController.class,
                PaymentController.class,
                ContractController.class,
                TenantController.class
        );

        for (Class<?> controller : controllers) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isMutationEndpoint(method)) {
                    continue;
                }
                assertThat(method.getAnnotation(MutationOperation.class))
                        .as("%s#%s must use @MutationOperation", controller.getSimpleName(), method.getName())
                        .isNotNull();
            }
        }
    }

    private boolean isMutationEndpoint(Method method) {
        return method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
    }
}

