package com.breadShop.XXI.dto.customer;

/** ผลของ OrderRepository.countOrdersGroupByUser — user คนนี้สั่งไปกี่ order */
public record UserOrderCount(Integer userId, Long orderCount) {}
