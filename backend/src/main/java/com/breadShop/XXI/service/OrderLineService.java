package com.breadShop.XXI.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    /**
     * ดึง order line ทั้งหมดของ order ที่ระบุ โดยเรียงลำดับจาก id ของ order line และแปลงเป็น OrderLineResponse (DTO)
     * @param orderId รหัสของ order ที่ต้องการดึง order line
     * @return  List ของ OrderLineResponse ที่เกี่ยวข้องกับ order
     */
    @Transactional(readOnly = true)
    public List<OrderLineResponse> getByOrderId(Integer orderId) {
        return orderLineRepository.findByOrderId(orderId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

   /**
    * ดึง order line ที่ระบุ โดยถ้า order line นั้นมีอยู่ จะทำการแปลงเป็น OrderLineResponse (DTO) และส่งกลับไป
    * @param id รหัสของ order line ที่ต้องการดึง
    * @return OrderLineResponse ที่เกี่ยวข้องกับ order line
    */
    @Transactional(readOnly = true)
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