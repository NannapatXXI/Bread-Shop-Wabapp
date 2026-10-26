package com.breadShop.XXI.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.breadShop.XXI.dto.orderline.OrderLineResponse;
import com.breadShop.XXI.entity.OrderLine;
import com.breadShop.XXI.mapper.OrderLineMapper;
import com.breadShop.XXI.repository.OrderLineRepository;

/** รายการสินค้าใน order — เฉพาะ order ของ user คนนั้น ของคนอื่นตอบ 404 เหมือนไม่มีอยู่ */
@Service
public class OrderLineService {

    private final OrderLineRepository orderLineRepository;

    public OrderLineService(OrderLineRepository orderLineRepository) {
        this.orderLineRepository = orderLineRepository;
    }

    @Transactional(readOnly = true)
    public List<OrderLineResponse> getByOrderId(Integer orderId, Integer userId) {
        List<OrderLine> lines = orderLineRepository.findByOrderId(orderId);
        if (lines.stream().anyMatch(line -> !isOwnedBy(line, userId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return lines.stream().map(OrderLineMapper::toResponse).toList();
    }

    /** line ต้องอยู่ใน order ตาม URL และ order นั้นต้องเป็นของ user */
    @Transactional(readOnly = true)
    public OrderLineResponse getById(Integer orderId, Integer lineId, Integer userId) {
        return orderLineRepository.findById(lineId)
                .filter(line -> line.getOrder().getId().equals(orderId) && isOwnedBy(line, userId))
                .map(OrderLineMapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OrderLine not found"));
    }

    private static boolean isOwnedBy(OrderLine line, Integer userId) {
        return line.getOrder().getUser().getId().equals(userId);
    }
}
