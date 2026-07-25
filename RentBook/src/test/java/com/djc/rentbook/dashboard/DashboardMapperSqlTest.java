package com.djc.rentbook.dashboard;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardMapperSqlTest {
    @Test
    void monthCollectedUsesDuePeriodInsteadOfCashDate() throws NoSuchMethodException {
        Select annotation = DashboardMapper.class.getMethod("summary").getAnnotation(Select.class);
        String sql = String.join("\n", annotation.value());
        String monthCollectedSection = sql.substring(
                sql.indexOf("(select coalesce(sum(amount), 0)"),
                sql.indexOf("as month_income")
        );

        assertThat(monthCollectedSection)
                .contains("period_start")
                .doesNotContain("paid_date");
    }
}

