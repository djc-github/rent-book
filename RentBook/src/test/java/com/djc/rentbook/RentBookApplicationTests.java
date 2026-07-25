package com.djc.rentbook;

import com.djc.rentbook.contract.ContractMapper;
import com.djc.rentbook.dashboard.DashboardMapper;
import com.djc.rentbook.mutation.MutationMapper;
import com.djc.rentbook.payment.PaymentMapper;
import com.djc.rentbook.property.PropertyMapper;
import com.djc.rentbook.roomimage.RoomImageMapper;
import com.djc.rentbook.tenant.TenantMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration"
})
@ActiveProfiles("local")
class RentBookApplicationTests {

    @MockBean
    private ContractMapper contractMapper;

    @MockBean
    private DashboardMapper dashboardMapper;

    @MockBean
    private PaymentMapper paymentMapper;

    @MockBean
    private PropertyMapper propertyMapper;

    @MockBean
    private RoomImageMapper roomImageMapper;

    @MockBean
    private TenantMapper tenantMapper;

    @MockBean
    private MutationMapper mutationMapper;

    @MockBean
    private PlatformTransactionManager transactionManager;

    @Test
    void contextLoads() {
    }

}
