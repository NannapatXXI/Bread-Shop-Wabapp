// [Claude] DTO สำหรับ SalesChart — แต่ละ data point มีชื่อ label + ยอดปัจจุบัน + ยอดช่วงก่อนหน้า
package com.breadShop.XXI.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO สำหรับ SalesChart — แต่ละ data point มีชื่อ label + ยอดปัจจุบัน + ยอดช่วงก่อนหน้า
 */
public class SalesChartResponse {

    private final List<DataPoint> data;

    public SalesChartResponse(List<DataPoint> data) {
        this.data = data;
    }

    public List<DataPoint> getData() { return data; }

    public static class DataPoint {
        private final String name;
        private final BigDecimal current;
        private final BigDecimal previous;

        public DataPoint(String name, BigDecimal current, BigDecimal previous) {
            this.name = name;
            this.current = current;
            this.previous = previous;
        }

        public String getName() { return name; }
        public BigDecimal getCurrent() { return current; }
        public BigDecimal getPrevious() { return previous; }
    }
}
