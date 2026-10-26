package com.breadShop.XXI.mapper;

import com.breadShop.XXI.dto.orderline.OrderLineResponse;
import com.breadShop.XXI.entity.OrderLine;

/** OrderLine → DTO ที่ส่งให้ frontend (ใช้ทั้งใน order และ order line) */
public final class OrderLineMapper {

    private OrderLineMapper() {}

    public static OrderLineResponse toResponse(OrderLine line) {
        return new OrderLineResponse(
                line.getId(),
                line.getProduct().getId().intValue(),
                line.getProductName(),
                line.getProductSku(),
                line.getProduct().getImageUrl(),
                line.getUnitPrice(),
                line.getQuantity(),
                line.getDiscountAmount(),
                line.getTotalPrice());
    }
}
