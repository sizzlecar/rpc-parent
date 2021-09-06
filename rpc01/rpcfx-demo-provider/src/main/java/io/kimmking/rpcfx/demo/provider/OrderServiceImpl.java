package io.kimmking.rpcfx.demo.provider;

import io.kimmking.rpcfx.annotation.RpcService;
import io.kimmking.rpcfx.demo.api.Order;
import io.kimmking.rpcfx.demo.api.OrderService;
import org.springframework.stereotype.Service;

@RpcService
@Service
public class OrderServiceImpl implements OrderService {

    public OrderServiceImpl() {
    }

    @Override
    public Order findOrderById(int id) {
        return new Order(id, "Cuijing" + System.currentTimeMillis(), 9.9f);
    }
}
