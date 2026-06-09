package com.ifoto.ifoto_backend.service.payment;

import com.ifoto.ifoto_backend.model.EquipmentRental;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.model.enumerator.RentalPaymentMethod;

public interface PaymentMethodHandler {
    RentalPaymentMethod getMethod();
    void initiate(EquipmentRental rental, User renter);
}
