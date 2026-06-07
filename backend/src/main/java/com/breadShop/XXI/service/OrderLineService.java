package com.breadShop.XXI.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.orderline.OrderLineResponse;
import com.breadShop.XXI.entity.OrderLine;
import com.breadShop.XXI.repository.OrderLineRepository;

//สำหรับการจัดการ order line ของ order  เอาไว้จำกัดข้อมูลที่จะส่งกลับไปหา frontend  | reviewd by peak
@Service
public class OrderLineService {

    private final OrderLineRepository orderLineRepository;

    public OrderLineService(OrderLineRepository orderLineRepository) {
        this.orderLineRepository = orderLineRepository;
    }

    // ดึง order lines ทั้งหมดของ order
    public List<OrderLineResponse> getByOrderId(Integer orderId) {
        return orderLineRepository.findByOrderId(orderId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ดึง order line เดี่ยว
    public OrderLineResponse getById(Integer id) {
        OrderLine line = orderLineRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "OrderLine not found"));
        return toResponse(line);
    }

    private OrderLineResponse toResponse(OrderLine ol) {
        return new OrderLineResponse(
                ol.getId(),
                ol.getProduct().getId().intValue(),
                ol.getProductName(),
                ol.getProductSku(),
                ol.getProduct().getImageUrl(),
                ol.getUnitPrice(),
                ol.getQuantity(),
                ol.getDiscountAmount(),
                ol.getTotalPrice()
        );
    }
}