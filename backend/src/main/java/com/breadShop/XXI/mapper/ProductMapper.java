package com.breadShop.XXI.mapper;

import com.breadShop.XXI.dto.product.ProductResponse;
import com.breadShop.XXI.entity.Product;

public class ProductMapper {

    public static ProductResponse toResponse(Product product) {

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                product.getDescription(),
                product.getImageUrl(),
                product.getCategory(),
                product.getExpiryDate(),
                product.isActive()
        );
    }
}
