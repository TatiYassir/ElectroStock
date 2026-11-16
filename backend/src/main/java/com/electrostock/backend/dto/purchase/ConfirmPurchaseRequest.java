package com.electrostock.backend.dto.purchase;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

public class ConfirmPurchaseRequest {

    @NotNull
    private Map<Long, List<String>> serialsByItemId;

    public Map<Long, List<String>> getSerialsByItemId() {
        return serialsByItemId;
    }

    public void setSerialsByItemId(Map<Long, List<String>> serialsByItemId) {
        this.serialsByItemId = serialsByItemId;
    }
}