package com.campus.market.dto;

public class ProductUpdateRequest extends ProductUpsertRequest {
    private Long operatorUserId;

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }
}

