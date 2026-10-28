package com.orionkey.service;

import com.orionkey.model.response.WorkbenchResponse.Ledger;
import com.orionkey.model.response.WorkbenchResponse.Overview;

public interface WorkbenchService {

    Overview overview(String startDate, String endDate, String paymentMethod);

    Ledger orders(String startDate, String endDate, String paymentMethod, String status,
                  String keyword, String page, String pageSize);

    byte[] export(String startDate, String endDate, String paymentMethod, String status,
                  String keyword, String page, String pageSize, String ids);
}
