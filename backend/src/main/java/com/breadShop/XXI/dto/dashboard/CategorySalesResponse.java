// [Claude] DTO สำหรับ CategoryDonutChart — ยอดขายแบ่งตาม category เดือนนี้
package com.breadShop.XXI.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO สำหรับ CategoryDonutChart — ยอดขายแบ่งตาม category เดือนนี้
 */
public class CategorySalesResponse {

    private final List<CategoryData> data;

    public CategorySalesResponse(List<CategoryData> data) {
        this.data = data;
    }

    public List<CategoryData> getData() { return data; }

    public static class CategoryData {
        private final String name;
        private final BigDecimal value;

        public CategoryData(String name, BigDecimal value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }
        public BigDecimal getValue() { return value; }
    }
}
